# Code Review — `java-saga` (branch `monolith`)

Scope: all 62 Java/config sources under `src/`, plus `build.gradle` and `application.yml`.
Focus: **correctness of the saga/persistence layer** and **performance**.

Written as `CODE_REVIEW.md` rather than into `README.md`, which already holds the
monolith-vs-microservices comparison and should not be overwritten.

**Suite state at review time:** `./gradlew test` — green. 2 test classes, 7 tests, 0 failures.
Every CRITICAL finding below survives a green suite; see [H5](#h5--the-suite-cannot-catch-any-of-the-above).

**Update — C1–C4 fixed** (see diff): `@Transactional` added to all nine service methods,
`open-in-view: false` set, `SagaOrchestrator` compensation now best-effort (`addSuppressed`),
`LoyaltyAccountEntity` got `@Version` for parity with `StockEntity`, and
`OptimisticLockingFailureException` now maps to 409 instead of leaking as 500 — this closes C4
too, since `reserve()`'s `@Version` check now actually runs inside a transaction. Added
`SagaOrchestratorTest` covering both changed branches. M2 (`orElse`→`orElseGet`) fixed in passing,
same line as C3. Everything else below (H1–H5, M1/M3–M6, L1–L5) is still open.

---

## Summary

| # | Severity | Finding | Location |
|---|----------|---------|----------|
| C1 | **Critical** | Compensation writes never reach the database | all 5 `*RepositoryAdapter` + missing `@Transactional` |
| C2 | **Critical** | A throwing `compensate()` aborts the remaining chain and swallows the original exception | `SagaOrchestrator.java:25` |
| C3 | **Critical** | Lost update on loyalty points — no `@Version`, no lock | `LoyaltyAccountEntity.java`, `LoyaltyService.java:20` |
| C4 | **Critical** | Oversell TOCTOU — stock guard and write are not atomic | `InventoryService.java:17` |
| H1 | High | Money held in `double` | `Payment`, `Order`, `CheckoutRequest`, `LoyaltyService` |
| H2 | High | `ddl-auto: update` against MariaDB | `application.yml:13` |
| H3 | High | Field `@Value` injection — non-final, untestable, inconsistent | `PaymentService:16`, `ShippingService:16`, `StockSeeder:17` |
| H4 | High | Exception-handler javadoc is factually wrong; unmapped exceptions leak as 500 | `CheckoutExceptionHandler.java:11` |
| H5 | High | Test suite cannot catch C1–C4 by construction | `src/test/**` |
| M1 | Medium | Double read per update, papered over by OSIV | all mutating adapters |
| M2 | Medium | `orElse` allocates on every checkout | `LoyaltyService.java:23` |
| M3 | Medium | Insert branch writes the value, then re-sets the same value | `StockRepositoryAdapter:30`, `LoyaltyAccountRepositoryAdapter:29` |
| M4 | Medium | ~9 round-trips + 5 commits per checkout, no pool/batch tuning | `application.yml` |
| M5 | Medium | Dead SQL-log config; zero log statements in a silently-compensating saga | `application.yml:14`, whole codebase |
| M6 | Medium | Points formula computed twice — duplicated business rule | `EarnLoyaltyPointsStep.java:24` |
| L1 | Low | Missing product reported as "insufficient stock" | `InventoryService.java:19` |
| L2 | Low | Seeder silently ignores config changes after first run | `StockSeeder.java:28` |
| L3 | Low | Client dictates the charged amount | `CheckoutRequest.java:10` |
| L4 | Low | `Error` leaves the saga half-applied with no compensation | `SagaOrchestrator.java:24` |
| L5 | Low | `SagaStep` has no identity — nothing can log *which* step failed | `SagaStep.java` |

---

## CRITICAL

### C1 — Compensation writes never reach the database

**The saga's entire reason for existing does not work against a real database.**

There is no `@Transactional` anywhere in the codebase. The only match for the string is a
*comment* at `StockRepositoryAdapter.java:29` claiming a transaction that does not exist:

```java
// Update the managed entity in place instead of merging a fresh instance,
// so the @Version field reflects the row actually read in this transaction.
StockEntity entity = jpaRepository.findById(stock.productId())
        .orElseGet(() -> jpaRepository.save(mapper.toEntity(stock)));
entity.setAvailableQuantity(stock.availableQuantity());   // dirty-check, never flushed
```

All five mutating adapters use this shape — `StockRepositoryAdapter.save`,
`LoyaltyAccountRepositoryAdapter.save`, and the update branches of
`PaymentRepositoryAdapter.save`, `ShipmentRepositoryAdapter.save`, `OrderRepositoryAdapter.save`.
Each mutates a managed entity and relies on Hibernate dirty checking to emit the `UPDATE`.

The persistence context those entities live in is the **Open Session In View** one:
`spring.jpa.open-in-view` is not set in `application.yml`, so it defaults to `true`.
OSIV binds an `EntityManager` to the request thread — but it never begins a transaction and
**never flushes on close**. `SimpleJpaRepository.findById` is `@Transactional(readOnly = true)`,
which does not flush either. So a dirty entity only reaches the database if some *later* call
opens a **write** transaction that joins the same persistence context and commits.

On the compensation path there is no later write. LIFO compensation is the last thing that
happens before the exception propagates to `CheckoutExceptionHandler` and the request ends;
`OpenEntityManagerInViewInterceptor` then closes the `EntityManager` without flushing, and every
compensating `UPDATE` is discarded.

**Failure scenario** — set `shipping.simulate.fail: true`, `POST /checkout`:

| Step | Runs? | Intended effect | Actual DB state |
|---|---|---|---|
| `ReserveStockStep` | ✓ | stock −1 | **stock −1 (flushed)** |
| `ChargePaymentStep` | ✓ | payment CHARGED | **CHARGED (flushed)** |
| `EarnLoyaltyPointsStep` | ✓ | points +2 | **+2 (flushed)** |
| `GenerateShippingStep` | ✗ throws | — | — |
| `compensate` loyalty | ✓ | points −2 | **lost — still +2** |
| `compensate` payment | ✓ | REFUNDED | **lost — still CHARGED** |
| `compensate` stock | ✓ | stock +1 | **lost — still −1** |

Client receives `409 Conflict` and believes nothing happened. The customer has been charged.

**The happy path works only by accident.** Each dirty mutation happens to be followed by an
insert that opens a write transaction and flushes the whole context:
`ChargePaymentStep`'s `save(new Payment)` flushes the dirty `StockEntity`;
`GenerateShippingStep`'s insert flushes the dirty `LoyaltyAccountEntity`;
`CreateOrderStep`'s insert flushes whatever is left. Reordering the `List.of(...)` in
`CheckoutUseCase.checkout()` — e.g. moving `EarnLoyaltyPointsStep` last — silently drops that
write with no error and no test failure.

**Fix.** Put `@Transactional` on each domain service's forward and compensating methods —
`InventoryService.reserve/release`, `PaymentService.charge/refund`,
`LoyaltyService.earnPoints/revokePoints`, `ShippingService.generate/cancel`,
`OrderService.create`. This is exactly the model `CLAUDE.md` already documents ("each step commits
its own local transaction immediately"); the code just never implemented it. Additionally set
`spring.jpa.open-in-view: false` so that this class of mistake fails loudly (detached-entity /
`LazyInitializationException`) instead of silently.

> Derived from Spring/Hibernate flush semantics by reading the code, not from an executed
> reproduction. To confirm in one run: `spring.jpa.show-sql: true`, `shipping.simulate.fail: true`,
> `POST /checkout`, and observe that no `UPDATE` is emitted after the failure.

### C2 — A throwing `compensate()` aborts the chain and swallows the original exception

`SagaOrchestrator.java:24-27`:

```java
} catch (RuntimeException e) {
    executed.forEach(SagaStep::compensate);
    throw e;
}
```

The first `compensate()` that throws propagates straight out of `forEach`. Consequences, both bad:

1. **Remaining compensations never run.** They are the *earlier* steps — i.e. the ones holding
   the money. Compensation order is loyalty → payment → stock, so a failure in the loyalty
   compensation means the refund and the stock release are skipped.
2. **The original exception is replaced.** `throw e` is never reached, so
   `CheckoutExceptionHandler` never sees `ShippingFailedException` and the client gets a
   `500` with a stack trace instead of a `409` with a reason.

**Failure scenario, reachable today:** `LoyaltyAccount.revoke` throws
`IllegalStateException("Insufficient loyalty points to revoke")` when `pointsRevoked > points`
(`LoyaltyAccount.java:11`). A concurrent checkout that drained the account (see C3) triggers
exactly that during compensation → payment never refunded, stock never released, client sees a 500.

**Fix.** Compensation must be best-effort and complete:

```java
} catch (RuntimeException e) {
    for (SagaStep step : executed) {          // ArrayDeque iterates head-first = LIFO, correct
        try {
            step.compensate();
        } catch (RuntimeException ce) {
            e.addSuppressed(ce);
        }
    }
    throw e;
}
```

Note the LIFO ordering itself is correct — `push` prepends and `ArrayDeque` iteration is
head-first, and a step that throws is never pushed so it never compensates itself. That part of
the design is sound; only the error handling around it is not.

### C3 — Lost update on loyalty points

`LoyaltyService.earnPoints` is an unguarded read-modify-write:

```java
LoyaltyAccount account = loyaltyAccountRepository.findByCustomerId(customerId)
        .orElse(new LoyaltyAccount(customerId, 0L));
LoyaltyAccount updated = account.earn(earnedPoints);
loyaltyAccountRepository.save(updated);
```

`LoyaltyAccountEntity` has **no `@Version`** and no lock. Two concurrent checkouts for the same
customer both read `points = 100`, both write `102`; one earn is silently lost. `StockEntity` does
carry `@Version`, so inventory at least fails loudly — loyalty is the only aggregate with a
read-modify-write cycle and no optimistic locking at all.

The same staleness corrupts compensation: `EarnLoyaltyPointsStep.compensate` calls
`revokePoints(account, earnedPoints)` with the `account` snapshot captured during `execute()`.
`account.revoke(n)` computes an **absolute** new balance from a stale read, so it clobbers any
concurrent earn rather than decrementing.

**Fix.** Cheapest: add `@Version` to `LoyaltyAccountEntity` for parity with `StockEntity`.
Better: make it an atomic delta and remove the read entirely —

```java
@Modifying
@Query("update LoyaltyAccountEntity a set a.points = a.points + :delta where a.customerId = :id")
int addPoints(@Param("id") String customerId, @Param("delta") long delta);
```

which also makes compensation a `-delta` and immune to staleness.

### C4 — Oversell: the stock guard and the write are not atomic

`InventoryService.reserve`:

```java
Stock stock = stockRepository.findByProductId(productId)      // read
        .orElseThrow(...);
stockRepository.save(stock.reserve(quantity));                // check inside, then write
```

`Stock.reserve` checks `quantity > availableQuantity` **in memory**, against a value read in an
earlier statement. Two concurrent requests for the last unit both pass the check.

`@Version` on `StockEntity` is the only thing standing between this and an oversell, and it only
helps when the read and the flush share a persistence context *and* the flush actually happens —
neither of which is guaranteed today (C1). Once C1 is fixed by adding `@Transactional`, the
version check does start working, but the caller then gets a raw
`ObjectOptimisticLockingFailureException` → 500, since `CheckoutExceptionHandler` maps only the
three domain exceptions (H4).

**Fix.** Either take a write lock on the read used by `reserve`:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<StockEntity> findById(String productId);
```

or push the guard into the database as a conditional update and treat "0 rows affected" as
`InsufficientStockException`:

```sql
UPDATE stock SET available_quantity = available_quantity - :q
WHERE product_id = :id AND available_quantity >= :q
```

The second is one round-trip instead of two and needs no locking or retry.

---

## HIGH

### H1 — Money held in `double`

`Payment.amount`, `Order.amount`, `CheckoutRequest.amount`, `LoyaltyService.calculatePoints`,
and the corresponding columns are all `double`. Binary floating point cannot represent `0.10`;
sums drift, equality comparisons lie, and `(long) (amount / pointsPerCurrencyUnit)`
(`LoyaltyService.java:34`) truncates toward zero on an already-lossy value — a customer who
should earn 3 points can get 2.

**Fix.** `BigDecimal` with `@Column(precision = 19, scale = 4)`, or store minor units as `long`.
Either is a mechanical change across five files and worth doing before the schema has real rows.

### H2 — `ddl-auto: update` against MariaDB

`application.yml:13`. Hibernate mutates the schema at every boot: never drops, never renames
correctly, cannot be code-reviewed, and diverges silently from `infra/mariadb-init.sql`, which is
the file a reader would assume is authoritative.

**Fix.** `ddl-auto: validate` plus Flyway or Liquibase for migrations.

### H3 — Field `@Value` injection

```java
@Value("${payments.simulate.reject}")
private boolean simulateReject;          // PaymentService.java:16
```

Same in `ShippingService.java:16` and `StockSeeder.java:17,20`. These fields are non-final,
invisible in the constructor, and unsettable from a plain unit test without reflection or a full
Spring context — which is part of why the failure-simulation paths have no unit tests.
`LoyaltyService.java:15` already does it correctly through the constructor; the other three are
inconsistent with it and with the project's stated "constructor injection always" convention.

### H4 — Exception-handler javadoc is factually wrong, and unmapped exceptions leak as 500

`CheckoutExceptionHandler.java:11-15` states:

> Every domain exception here means the checkout transaction already rolled back (see
> CheckoutUseCase) — nothing was persisted for this request. […] there is no compensation to run

Both claims are false. There is no enclosing transaction to roll back (C1), and compensation
*is* run — `SagaOrchestrator` executes it in LIFO order before rethrowing. `CLAUDE.md` describes
the correct behaviour; this javadoc contradicts it on the one class every failure passes through.

Separately, only the three domain exceptions are mapped. `IllegalStateException` from a failed
compensation (C2), `ObjectOptimisticLockingFailureException` from a version conflict (C3/C4), and
`MethodArgumentNotValidException` from `@Valid` all fall through to a default 500 with a stack
trace.

### H5 — The suite cannot catch any of the above

`CheckoutUseCaseLoyaltyTest` mocks all five domain services and asserts *interactions*:

```java
verify(loyaltyService).revokePoints(new LoyaltyAccount(CUSTOMER_ID, 2L), 2L);
```

That asserts the method was **called**, never that a row changed — so C1 is invisible by
construction. `LoyaltyServiceTest` mocks the repository, so it cannot see C3 either. There is no
`@SpringBootTest`, no `@DataJpaTest`, and no test that touches a database.

`CLAUDE.md` references a `SagaOrchestratorTest` and `REQUIREMENTS.md` references
`CheckoutUseCaseIntegrationTest` and `CheckoutControllerTest` — **none of the three exist.**
The orchestrator, the single most critical class in the repo, has zero direct tests.

**Fix, in priority order:**

1. `SagaOrchestratorTest` — compensation order, self-compensation is skipped, and a throwing
   `compensate()` still compensates the rest and preserves the original exception (C2).
2. One `@DataJpaTest` or Testcontainers test asserting that after a simulated shipping failure the
   stock row, payment row, and loyalty row are **back to their pre-checkout values** (C1).
3. A concurrency test: two parallel `earnPoints` on one customer must produce `+2n`, not `+n` (C3).

---

## MEDIUM — performance

### M1 — Double read per update

Every mutating path reads twice: the service calls `findByProductId` / `findByCustomerId`, then
`save(...)` calls `findById` again internally. With OSIV the second read is served from the
first-level cache, so it costs no SQL today — but that is precisely the accidental coupling
described in C1. Narrow the persistence context (as C1's fix requires) and this becomes a real
second round-trip on every step.

**Fix.** Have the service pass the loaded aggregate into the repository, or move the mutation into
the repository as an atomic update (which C3 and C4 want anyway).

### M2 — `orElse` allocates on every checkout

`LoyaltyService.java:23`:

```java
.orElse(new LoyaltyAccount(customerId, 0L))     // allocated even when the account exists
```

`orElse` evaluates its argument eagerly. `orElseGet(() -> new LoyaltyAccount(customerId, 0L))` is
the same length and allocates only on the miss. Trivial in isolation; it is on the hot path of
every checkout.

### M3 — Insert branch writes the value, then re-sets it

`StockRepositoryAdapter.java:30` and `LoyaltyAccountRepositoryAdapter.java:29`:

```java
Entity e = jpaRepository.findById(id)
        .orElseGet(() -> jpaRepository.save(mapper.toEntity(x)));   // inserts with final value
e.setPoints(x.points());                                            // sets the same value again
```

Harmless but misleading — it reads as if the insert branch needs the setter, which it does not.

### M4 — ~9 round-trips and 5 commits per checkout, untuned

Five steps, each doing a read and a write, none batched: roughly nine database round-trips per
`POST /checkout`. There is no `spring.datasource.hikari` configuration (default pool: 10
connections) and no `hibernate.jdbc.batch_size`. Once C1 is fixed properly, each step also becomes
its own commit — five fsyncs per checkout. Size the pool deliberately and set a batch size before
any load testing; the connection pool will be the first ceiling hit.

### M5 — Dead SQL-log config, and no logging at all

`application.yml:14-17` sets `show-sql: false` alongside `format_sql: true`; `format_sql` only
affects `show-sql` output, so it does nothing. Meanwhile `logging.level.com.saga.checkout: INFO`
is configured and **the codebase contains not a single log statement**. A saga that runs
compensation silently, and (per C1) discards it silently, has no audit trail whatsoever.
Log each step's execute and compensate at INFO, including the failure that triggered compensation.

### M6 — The points formula is computed twice

`EarnLoyaltyPointsStep.java:24-25`:

```java
earnedPoints = loyaltyService.calculatePoints(amount);
account      = loyaltyService.earnPoints(customerId, amount);   // recomputes calculatePoints internally
```

Beyond the duplicate arithmetic, this **duplicates a business rule across two classes**. If
`earnPoints` ever changes how it derives points — a tier multiplier, a promotion, rounding —
`compensate()` keeps revoking the *old* number and silently corrupts balances.

**Fix.** Have `earnPoints` return the delta it applied (or derive the delta from the returned
account) so there is exactly one place that decides how many points a checkout is worth.

---

## LOW

- **L1** — `InventoryService.java:19` throws `InsufficientStockException(productId, quantity, 0)`
  when the product row does not exist. A nonexistent SKU is reported to the client as
  "insufficient stock" (409) rather than "no such product" (404).
- **L2** — `StockSeeder.java:28` is a no-op when the row already exists, so changes to
  `inventory.seed.initial-stock` never apply after the first boot. Reasonable, but undocumented.
- **L3** — `CheckoutRequest.amount` is validated `@Positive` but never cross-checked against
  `quantity × unit price`; the client dictates what it is charged. Acceptable for a pattern demo,
  worth an explicit note so it is not mistaken for an oversight.
- **L4** — `SagaOrchestrator.java:24` catches `RuntimeException`, so an `Error` (OOM,
  `StackOverflowError`) leaves the saga half-applied with no compensation. Defensible, but it is a
  deliberate limitation and should be stated as one.
- **L5** — `SagaStep` exposes only `execute()`/`compensate()`. With no `name()` or `toString()`,
  nothing can log *which* step failed or which compensations ran — a hard blocker for M5.

---

## Recommended order

1. **C1** — `@Transactional` on the nine service methods; `open-in-view: false`. Everything else
   is secondary while compensation does not persist.
2. **H5.1 + H5.2** — `SagaOrchestratorTest` and one DB-backed compensation test, so C1 can never
   silently return.
3. **C2** — best-effort compensation with `addSuppressed`.
4. **C3 + C4** — atomic updates for points and stock; drop the read-modify-write cycles.
5. **H1** — `double` → `BigDecimal` before the schema carries real data.
6. **H2, H3, H4** — schema migrations, constructor injection, and correct the javadoc.
7. **M1–M6** — performance and hygiene, once the correctness work has settled the shape of the
   persistence layer.
