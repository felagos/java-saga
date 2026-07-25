# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is (branch `monolith-async-payment`)

This branch takes the `monolith` branch (SAGA pattern with explicit LIFO compensation, running
in-process) and makes payment genuinely asynchronous: the gateway resolves the charge off-band, not
in the same HTTP request. Unlike the first iteration of this branch (two independent HTTP requests),
the async boundary is now modeled with **NATS**: `POST /checkout` publishes a
`saga.payment.initiated` event and returns immediately; every step after that (payment resolution,
shipping, order confirmation, and all compensations) is a **choreography** — each module owns a NATS
listener that reacts to the event it cares about and publishes the next one. There is no central
orchestrator driving phase 2 anymore, and no manual callback endpoint. Every step (success or
compensation) is written to a `saga_audit_log` table for later auditing.

## Commands

```bash
make up                       # docker compose -f docker-compose.full.yml up -d --build (nats + mariadb + bff)
make infra-up                 # nats + mariadb only, for local bootRun

./gradlew compileJava
./gradlew test
./gradlew bootRun             # runs the checkout module (the only executable one)
```

Multi-module Gradle build, one module per saga step: `shared` (generic messaging: NATS connection,
event records, `SagaEventPublisher`/`SagaEventSubscriber`, and the `SagaAuditService` — no domain
knowledge), `orders`, `inventory`, `payments`, `shipping` (each a `java-library`), and `checkout`
(the only `org.springframework.boot` module — web layer, phase-1 use case, `CheckoutApplication`).
`settings.gradle` lists them all. The Docker Compose service is still named `bff`.

## Architecture

Same hexagonal shape per module as `monolith` (`domain/application/infrastructure/persistence/
entity`), just physically split so each domain is its own Gradle subproject instead of a package
under one project. `checkout` depends on the other four plus `shared`; nothing depends on
`checkout`. `orders`/`inventory`/`payments`/`shipping` don't depend on each other — they only
depend on `shared`, and coordinate exclusively through NATS events, never through direct calls or
shared repositories.

There is no `SagaStep`/`SagaOrchestrator` anymore — a LIFO in-process compensator doesn't fit a flow
that spans independent listeners reacting to messages. Compensation is distributed: whichever
modules need to undo their own step each subscribe to the relevant failure event and do it
themselves.

- `checkout/application/CheckoutUseCase.java` — **phase 1 only**, synchronous, called from
  `POST /checkout`: reserve stock (`InventoryService.reserve`, inline — no `SagaStep` wrapper
  anymore), `OrderService.createPending(...)` creates the order as `PENDING_PAYMENT`,
  `PaymentService.initiate(...)` creates a `PENDING` `Payment`, then publishes
  `saga.payment.initiated` (`shared/.../events/PaymentInitiatedEvent`) via `SagaEventPublisher`. If
  order-creation or payment-initiation blow up, stock is released before rethrowing as
  `CheckoutInitiationException`. Returns a `CheckoutInitiation` (orderId + paymentId +
  `PENDING_PAYMENT`). Nothing in this class waits for or drives what happens after that publish.
- **The choreography** (`shared/.../events/SagaSubjects.java` has the full list of subjects):
  - `payments` — `PaymentInitiatedListener` subscribes `saga.payment.initiated`, simulates the
    gateway's decision (`payments.simulate.reject`, back after being removed in the previous
    iteration — there's no real gateway here), then `confirm`/`reject`s the payment and publishes
    `saga.payment.approved` or `saga.payment.rejected`. `ShippingFailedRefundListener` subscribes
    `saga.shipping.failed` and refunds the payment (compensation).
  - `inventory` — `ReleaseStockListener` subscribes both `saga.payment.rejected` and
    `saga.shipping.failed`, releases the reserved stock either way (compensation).
  - `orders` — `CancelOrderListener` subscribes both `saga.payment.rejected` and
    `saga.shipping.failed`, cancels the order (compensation). `ConfirmOrderListener` subscribes
    `saga.shipping.generated`, confirms the order (`PENDING_PAYMENT → CONFIRMED`), publishes
    `saga.order.confirmed` (terminal, audit only).
  - `shipping` — `PaymentApprovedListener` subscribes `saga.payment.approved`, generates the
    shipment; on success publishes `saga.shipping.generated`, on `ShippingFailedException` publishes
    `saga.shipping.failed` instead.
  - Every listener writes a row to `saga_audit_log` (via `shared/.../audit/SagaAuditService`) after
    acting, whether it succeeded or compensated.
- `checkout/web/CheckoutController.java` — `POST /checkout` returns **202 Accepted** with
  `{orderId, paymentId, status: "PENDING_PAYMENT"}`, not the final result. There is no
  `PaymentCallbackController` anymore — nothing external triggers phase 2, the gateway simulation
  lives inside `payments`' own NATS listener.
- `checkout/web/OrderController.java` — `GET /orders/{id}`, the only way a client learns the final
  state; more necessary than ever since there's no callback and no synchronous final response.
- `checkout/web/CheckoutExceptionHandler.java` — `InsufficientStockException` (409, from phase 1's
  synchronous stock check) and `CheckoutInitiationException` (500, phase 1 order/payment creation
  failure) map to HTTP. `NotFoundException` (`shared/.../exceptions`, the common base for
  `OrderNotFoundException`/`PaymentNotFoundException`/etc.) → 404. `ShippingFailedException` is
  never thrown synchronously anymore — it only happens inside `shipping`'s async listener, where
  there's no HTTP caller to report to.
- `shared/.../messaging/` — `NatsConfig` (single `Connection` bean, `nats.url` property),
  `SagaEventPublisher.publish(subject, event)` (JSON via Jackson), `SagaEventSubscriber.subscribe(
  subject, EventClass, handler)` (wraps `Connection.createDispatcher`, deserializes, calls the
  handler). Every listener class follows the same shape: constructor-inject the subscriber +
  publisher + its module's `*Service` + `SagaAuditService`, subscribe in a `@PostConstruct` method,
  act on the event, publish the next one and/or record an audit row.
- `shared/.../audit/` — `SagaAuditLogEntity` (table `saga_audit_log`: `orderId`, `step`, `outcome`
  [`SUCCESS`/`FAILED`/`COMPENSATED`], `detail`, `createdAt`), `SagaAuditService.record(...)`. Table
  is created by Hibernate (`ddl-auto: update`), same as every other entity in this codebase.
- Each domain's `application/*Service` — unchanged signatures from the previous iteration:
  `OrderService` (`createPending`, `confirm`, `cancel`, `findById`), `PaymentService` (`initiate`,
  `confirm`, `reject`, `refund`, `findById`).
- `OrderStatus` (`PENDING_PAYMENT`, `CONFIRMED`, `CANCELLED`); `PaymentStatus` (`PENDING`,
  `CHARGED`, `REJECTED`, `REFUNDED`). `Payment.orderId` is what every listener uses to correlate
  events back to the order — there's no separate "saga id", `orderId` already is one.

### Config-driven failure simulation

`payments.simulate.reject` (bool) — `PaymentInitiatedListener` rejects instead of confirming.
`shipping.simulate.fail` (bool) — `ShippingService.generate` throws `ShippingFailedException`,
caught by `PaymentApprovedListener`, which publishes `saga.shipping.failed` instead of
`saga.shipping.generated`.

### Adding a new step to the checkout

If it belongs in phase 1 (before payment is even initiated), same recipe as before: a method on the
relevant `*Service`, called directly from `CheckoutUseCase.checkout(...)`, with inline compensation
in its `catch` block if something later in phase 1 fails.

If it belongs after payment is initiated: add a domain method on its `*Service`, a new `@Component
*Listener` in that module's `application/` package that subscribes to whichever event should trigger
it (add a new constant to `SagaSubjects` if none fits), and — if anything downstream can still
fail — publish a new event (add a new record to `shared/.../events/`) so the modules that need to
compensate can subscribe to it. Always call `SagaAuditService.record(...)` after acting, success or
compensation, so the step shows up in `saga_audit_log`.
