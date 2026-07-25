# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is (branch `monolith-async-payment`)

This branch takes the `monolith` branch (SAGA pattern with explicit LIFO compensation, running
in-process — no NATS/Outbox/idempotency, see `main` for the distributed version) and makes payment
genuinely asynchronous: the gateway confirms the charge hours later via callback, not in the same
HTTP request. Every other step (stock, shipping, order confirmation) is still synchronous, direct
Java method calls. The async boundary is modeled as **two independent HTTP requests**
(`POST /checkout` and `POST /payments/{id}/callback`) — not threads, executors, or messaging.
There is still no NATS/Outbox here; don't reintroduce them for this either, the two-request split
is enough.

## Commands

```bash
make up                       # docker compose -f docker-compose.full.yml up -d --build (mariadb + bff)
make infra-up                 # mariadb only, for local bootRun

./gradlew compileJava
./gradlew test
./gradlew bootRun             # runs the checkout module (the only executable one)
```

Multi-module Gradle build, one module per saga step: `orchestrator` (generic `SagaStep`/
`SagaOrchestrator`, no domain knowledge), `orders`, `inventory`, `payments`, `shipping` (each a
`java-library`), and `checkout` (the only `org.springframework.boot` module — web layer, use case,
`CheckoutApplication`). `settings.gradle` lists them all. The Docker Compose service is still named
`bff`.

## Architecture

Same hexagonal shape per module as `monolith` (`domain/application/infrastructure/persistence/
entity`), just physically split so each domain is its own Gradle subproject instead of a package
under one project. `checkout` depends on the other four plus `orchestrator`; nothing depends on
`checkout` (avoids a cycle, since `orders`/`inventory`/`payments`/`shipping` all implement
`SagaStep`, which lives in `orchestrator`, not in `checkout`).

- `orchestrator/.../SagaStep.java` / `SagaOrchestrator.java` — unchanged from `monolith`:
  `execute()`/`compensate()` contract, LIFO compensation on `RuntimeException`. Still has no domain
  knowledge; still generic over any `List<SagaStep>`, used for **two separate sub-sagas** now (see
  below), not one.
- `checkout/application/CheckoutUseCase.java` — defines the saga, now split across the async
  boundary:
  - **Phase 1, `checkout(...)`** (synchronous, called from `POST /checkout`): `ReserveStockStep`
    runs directly (not through the orchestrator — it's the only step in this phase, nothing to
    compensate but itself if what follows fails). Then `OrderService.createPending(...)` creates
    the order as `PENDING_PAYMENT`, and `PaymentService.initiate(...)` hands the charge to the
    gateway and returns immediately with a `PENDING` `Payment` — no exception, no decision yet. If
    order-creation or payment-initiation blow up, `reserveStock.compensate()` releases the stock
    before rethrowing. Returns a `CheckoutInitiation` (orderId + paymentId + `PENDING_PAYMENT`).
  - **Phase 2, `handlePaymentResult(paymentId, approved)`** (called from the payment callback,
    hours later, no relation to the original HTTP request/thread): loads `Payment` and `Order` by
    id (that's the only "saga state" needed to resume — no separate saga-state table). If
    `!approved`: reject the payment, release the stock, cancel the order — done, nobody is waiting
    on this over HTTP except the gateway's own webhook call. If `approved`: confirm the payment,
    then run `SagaOrchestrator.run(List.of(GenerateShippingStep, ConfirmOrderStep))` — a 2-step
    sub-saga reusing the same LIFO engine. If *that* fails, the failure is outside what the
    orchestrator's deque covers (payment + stock aren't in that list), so `CheckoutUseCase` itself
    compensates them: refund the payment, release the stock, cancel the order.
  - `ConfirmOrderStep` (`orders/application/`) transitions `PENDING_PAYMENT → CONFIRMED`; like the
    old `CreateOrderStep` on `monolith`, it's the last step in its sub-saga so `compensate()` is a
    no-op. There is no `ChargePaymentStep` anymore — charging can't complete inside `execute()`
    when the result isn't known until the callback, so phase 1 calls `PaymentService.initiate(...)`
    directly instead of going through a `SagaStep`.
- `checkout/web/CheckoutController.java` — `POST /checkout` now returns **202 Accepted** with
  `{orderId, paymentId, status: "PENDING_PAYMENT"}`, not the final result.
- `checkout/web/PaymentCallbackController.java` — `POST /payments/{id}/callback {approved}`
  simulates the gateway's webhook (there's no real gateway in this branch; call it manually or from
  a test). Delegates straight to `handlePaymentResult`.
- `checkout/web/OrderController.java` — `GET /orders/{id}`, needed now that the client can't learn
  the final state from the `/checkout` response alone.
- `checkout/web/CheckoutExceptionHandler.java` — `InsufficientStockException`/
  `ShippingFailedException` → 409 (only reachable from phase 1's synchronous stock check now,
  since shipping failures in phase 2 are compensated internally with no HTTP caller to report to);
  `IllegalStateException` (unknown order/payment id) → 404.
- Each domain's `application/*Service` — `OrderService` gained `createPending`, `confirm`,
  `cancel`, `findById`; `PaymentService` replaced `charge` with `initiate`, `confirm`, `reject`
  (kept `refund`). Both repository ports (`OrderRepository`, `PaymentRepository`) gained
  `findById`, implemented in the adapters via the existing find-by-id-and-mutate-the-managed-entity
  pattern.
- `OrderStatus` gained `PENDING_PAYMENT` and `CANCELLED` (was just `CONFIRMED`); `PaymentStatus`
  gained `PENDING` and `REJECTED` (was `CHARGED`/`REFUNDED`). `Payment` gained an `orderId` field —
  that's the link phase 2 uses to go from a callback's paymentId to the order it belongs to.

### Config-driven failure simulation

`payments.simulate.reject` is gone — payment outcome is no longer decided synchronously by config,
it's whatever `approved` the callback passes. `shipping.simulate.fail` (bool) is unchanged:
`ShippingService.generate` throws `ShippingFailedException` during phase 2's sub-saga.

### Adding a new step to the checkout

If it belongs in phase 1 (before payment is even initiated) or as part of the phase-2 sub-saga
(after payment is confirmed), same recipe as `monolith`: domain package with a forward + optional
compensation method on its `*Service`, a `SagaStep` implementation in that package's
`application/`, wired into the relevant `List.of(...)` in `CheckoutUseCase`. A step that itself
needs to wait on an external async result (like payment) doesn't fit the `SagaStep` contract —
follow the `initiate`/`handle*Result` split instead, not a `SagaStep`.
