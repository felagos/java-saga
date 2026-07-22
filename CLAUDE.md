# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is (branch `monolith`)

This branch is a monolithic rewrite of the same checkout business capability implemented as 6
SAGA-orchestrated microservices on `main`. One Spring Boot process (this repo root), one MariaDB
database — but it still runs the **SAGA pattern with explicit compensation**, just in-process:
each step commits its own local transaction immediately (plain JPA save, no enclosing
`@Transactional`), and if a step fails, the compensations of the steps that already succeeded run
in LIFO order. The difference from `main` is not "no saga" — it's "saga without the network":
no NATS, no Outbox, no idempotency guard, no separate services/databases; every "command" is a
direct Java method call instead of an async message with a reply. See `README.md` for the full
comparison, and `main` if you need the distributed version — don't port NATS/Outbox/idempotency
concepts back into this branch, that's genuinely not needed without network calls between steps.

## Commands

```bash
make up                       # docker compose -f docker-compose.full.yml up -d --build (mariadb + bff)
make infra-up                 # mariadb only, for local bootRun

./gradlew compileJava
./gradlew test
./gradlew test --tests "com.saga.checkout.orchestrator.SagaOrchestratorTest"
./gradlew bootRun
```

There is exactly one Gradle project, at the repo root (project name `saga`) — do not look for the
other 5 projects that exist on `main`; they were deleted on this branch. The Docker Compose
service is still named `bff` (matches `CheckoutApplication`/`CheckoutController`).

## Architecture

`src/main/java/com/saga/` has one package per former microservice — `orders/`, `inventory/`,
`payments/`, `shipping/` — as **siblings** of `checkout/` (not nested inside it), each with its
own `domain/application/infrastructure/persistence/entity`. Same hexagonal shape as the
microservices on `main`, just called via direct Java method calls instead of NATS, sharing one
MariaDB database (`monolith`) instead of one-per-domain. `checkout/` is the entry-point package
(formerly `bff/`) — it holds the web layer, the `CheckoutUseCase` that defines this saga, and the
generic `orchestrator/` engine.

- `checkout/orchestrator/SagaStep.java` — the contract every checkout step implements: `execute()`
  and `compensate()`, no generics, no return type. Each implementation is a small **per-request
  object, not a Spring bean** (it carries request state — inputs, then its own result — so it
  can't be a singleton); one lives in the `application/` package of the domain it belongs to,
  e.g. `orders/application/CreateOrderStep.java`, `payments/application/ChargePaymentStep.java`.
  Each is a thin adapter over its domain's `*Service` — it doesn't duplicate business logic.
- `checkout/orchestrator/SagaOrchestrator.java` — the actual saga machinery, read this file first
  when working on failure/compensation behavior. `run(List<SagaStep>)` executes steps in order,
  pushing each onto a `Deque` as it succeeds; on any `RuntimeException` it runs `compensate()` on
  that deque (LIFO — most recent step compensated first) and rethrows. A step that throws is
  never pushed, so it never compensates itself. This class has no domain knowledge at all — it
  would work for any saga, not just checkout.
- `checkout/application/CheckoutUseCase.java` — defines *this* saga: builds the steps in order
  (`CreateOrderStep`, `ReserveStockStep`, `ChargePaymentStep`, `GenerateShippingStep`,
  `ConfirmOrderStep`) fresh on every call (they carry per-request state) and hands the list to
  `SagaOrchestrator.run(...)`. `ConfirmOrderStep` is the one step that depends on another step's
  result (the `Order` `CreateOrderStep` created) — it takes that step directly in its constructor
  rather than through a shared context object; nothing else in this saga has a cross-step
  dependency, so don't introduce a context object for a problem that doesn't exist yet.
- `checkout/web/CheckoutController.java` — `POST /checkout` is synchronous: it returns the final
  `CONFIRMED` result or an error in the same HTTP response. There is no `GET /saga/{id}` — a
  synchronous in-process call has no async state to poll.
- `checkout/web/CheckoutExceptionHandler.java` — maps the three domain failure exceptions
  (`InsufficientStockException`, `PaymentRejectedException`, `ShippingFailedException`) to a 409
  with a reason. By the time one reaches here, `SagaOrchestrator` has already run compensation
  for every step that succeeded before the one that threw.
- Each domain's `application/*Service` exposes both the forward action and its compensation as
  plain methods (`OrderService.create`/`cancel`, `InventoryService.reserve`/`release`,
  `PaymentService.charge`/`refund`, `ShippingService.generate`/`cancel`) — no envelope, no
  command type string, no NATS subject. The `*Step` classes call these directly and hold the
  returned domain object (`Order`, `Payment`, `Shipment`) in a field so `compensate()` has what it
  needs (id, current state).
- Each domain's `infrastructure/persistence/entity/` holds its JPA `@Entity`; repository ports
  live in `domain/`, adapters/mappers/Spring Data repositories in `infrastructure/persistence/`.
  Adapters that support compensation (payments, shipping — orders/inventory already had this)
  follow the same find-by-id-and-mutate-the-managed-entity pattern used everywhere else in this
  codebase — **never** build a fresh detached entity and `save()` it to update, always `findById`
  inside the same call and mutate that managed instance (see any `*RepositoryAdapter` for the
  pattern; this matters for JPA merge semantics, not just style).
- No `Outbox` or `ProcessedMessage` entities exist here — those solve message-redelivery
  deduplication, and there is no messaging in this branch to redeliver anything.

### Config-driven failure simulation

Same idea as `main`, no gateway/warehouse integration — `application.yml` flags:
- `payments.simulate.reject` (bool) — `PaymentService.charge` throws `PaymentRejectedException`.
- `shipping.simulate.fail` (bool) — `ShippingService.generate` throws `ShippingFailedException`.
  Plain boolean here, not the fail-count-then-succeed knob `main` uses for its forward-recovery
  demo — there's no retry loop in this branch, any failure compensates immediately.

### Adding a new step to the checkout

Add the domain package (same 3-layer shape as the existing 5) with a forward method and a
compensation method on its application service, then a `SagaStep` implementation in that
package's `application/` folder (constructor takes the service plus whatever inputs it needs;
store the `execute()` result in a field if `compensate()` needs it — see any existing `*Step` for
the pattern). Add an instance to the `List.of(...)` in `CheckoutUseCase.checkout()`, in the
position it belongs in the sequence. Don't touch `SagaOrchestrator` — it's generic over any
`SagaStep` and already handles the LIFO compensation for however many steps the list has.
