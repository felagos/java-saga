# Flujos del checkout — rama `monolith-async-payment`

El saga de checkout ahora cruza dos requests HTTP independientes, separadas por el tiempo que
tarde el gateway de pago en resolver el cobro (simulado acá con un callback manual). Definido en
`CheckoutUseCase` (`checkout/src/main/java/com/saga/checkout/application/CheckoutUseCase.java`):
`checkout(...)` es la fase 1 (síncrona), `handlePaymentResult(...)` es la fase 2 (disparada por el
callback). Cada flecha sólida sigue siendo una llamada directa a método Java dentro del mismo
proceso — la única red real en este diagrama es el propio HTTP entre cliente/gateway y el server.

## Índice

1. [Pipeline de pasos](#1-pipeline-de-pasos)
2. [Happy path](#2-happy-path)
3. [Caso A — sin stock (fase 1)](#3-caso-a--sin-stock-fase-1)
4. [Caso B — pago rechazado (fase 2)](#4-caso-b--pago-rechazado-fase-2)
5. [Caso C — falla el envío (fase 2, post-aprobación)](#5-caso-c--falla-el-envío-fase-2-post-aprobación)
6. [Tabla: paso → compensación](#6-tabla-paso--compensación)

## 1. Pipeline de pasos

```
  Fase 1 (POST /checkout, síncrona)
         ┌──────────────┐
         │ ReserveStock │
         └──────────────┘
                 │
                 ▼
       ┌───────────────────┐
       │ CreatePendingOrder │
       └───────────────────┘
                 │
                 ▼
       ┌──────────────────┐
       │ InitiatePayment  │  -> responde 202 PENDING_PAYMENT
       └──────────────────┘

  ...horas después...

  Fase 2 (POST /payments/{id}/callback, dispara handlePaymentResult)
                 │
        approved?│
        ┌────────┴────────┐
        │no                │sí
        ▼                  ▼
  ┌───────────┐   ┌──────────────────┐
  │ Compensar │   │ GenerateShipping │
  │ (release  │   └──────────────────┘
  │ + cancel) │             │
  └───────────┘             ▼
                    ┌─────────────┐
                    │ ConfirmOrder│
                    └─────────────┘
```

## 2. Happy path

```
  Cliente        Checkout        Inventory        Orders        Payments
     │               │               │               │              │
     |-POST /checkout->
     │               │               │               │              │
                     |--reserve()---->
                     < - - -ok- - - -|
                     |------------------createPending()------------->
                     < - - - - - - - -Order PENDING_PAYMENT- - - - -|
                     |------------------------------------initiate()------------>
                     < - - - - - - - - - - - - -Payment PENDING- - - - - - - - -|
     < -202 PENDING_PAYMENT- - - - - -|
     │               │               │               │              │
     ...horas después, el gateway llama al callback...
     │               │               │               │              │
     |-POST /payments/1/callback {approved:true}------------------->|
                     |<-------------------------------handlePaymentResult(1, true)
                     |----------------------------------------------confirm()---->
                     < - - - - - - - - - - - - - - - -Payment CHARGED- - - - - -|
                     |---generate()----> (Shipping, no mostrado arriba)
                     < - - -ok- - - - -|
                     |------------------confirm(orderId)------------->
                     < - - - - - - - -Order CONFIRMED- - - - - - - -|
     < -200 (ack al gateway)- - - - - |
     │               │               │               │              │
```

## 3. Caso A — sin stock (fase 1)

Igual que en `monolith`: `ReserveStock` falla antes de crear orden o pago — nada que compensar,
ninguna orden ni pago llegaron a existir.

```
  Cliente          Checkout          Inventory
     │                 │                 │
     |--POST /checkout-->
                       |--reserve()------>
                       < - -InsufficientStockException- - |
     < -409 Insufficient stock- - - - - -|
```

## 4. Caso B — pago rechazado (fase 2)

Fase 1 tuvo éxito: hay `Order PENDING_PAYMENT` y `Payment PENDING`. El callback llega con
`approved:false` — se compensa lo que fase 1 dejó pendiente. El cliente original de `/checkout` ya
recibió su `202` hace rato; nadie HTTP espera el resultado de esta compensación salvo el propio
gateway (que solo necesita el ack del callback).

```
  Gateway              Checkout              Inventory              Orders              Payments
     │                    │                     │                     │                    │
     |-POST /callback {approved:false}--------------------------------------------------->|
                         |<---------------------------------------------handlePaymentResult(id, false)
                         |------------------------------------------------------------reject()->
                         < - - - - - - - - - - - - - - - - - - - - - - -Payment REJECTED- - - -|
                         |--release()----------->
                         < - - -ok- - - - - - - -|
                         |------------------cancel(orderId)---------->
                         < - - - - - - - -Order CANCELLED- - - - - - |
     < -200 (ack)- - - - |
```

## 5. Caso C — falla el envío (fase 2, post-aprobación)

El callback llega con `approved:true`, el pago se confirma (`CHARGED`), pero
`GenerateShipping` falla (`shipping.simulate.fail=true`). El sub-saga
`SagaOrchestrator.run([GenerateShipping, ConfirmOrder])` no tiene nada que compensar en su propio
deque (`GenerateShipping` nunca se pushea, `ConfirmOrder` nunca corre) — pero `CheckoutUseCase`
compensa lo que quedó *fuera* de ese sub-saga: el pago ya cobrado y el stock ya reservado.

```
  Gateway              Checkout              Payments              Shipping              Inventory              Orders
     │                    │                     │                     │                     │                    │
     |-POST /callback {approved:true}-------------------------------------------------------------------------->|
                         |<---------------------------------------------handlePaymentResult(id, true)
                         |------------------confirm()----->
                         < - - -Payment CHARGED- - - - - -|
                         |------------------------------------generate()------------>
                         < - - - - - - - - - - - -ShippingFailedException- - - - - -|
                         |--refund()  [compensa pago]----->
                         < - - -ok- - - - - - - - - - - - |
                         |------------------------------------------------release()  [compensa stock]---------->
                         < - - - - - - - - - - - - - - - - - - - - - - - - -ok- - - - - - - - - - - - - - - - -|
                         |---------------------------------------------------------------------cancel(orderId)---------------->
                         < - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -Order CANCELLED- - - - - - - -|
     < -200 (ack)- - - - |
```

## 6. Tabla: paso → compensación

| Fase | Paso | Paquete | Llama a | Compensación |
|---|---|---|---|---|
| 1 | `ReserveStockStep` | `inventory.application` | `inventoryService.reserve(productId, qty)` | `inventoryService.release(productId, qty)` (manual, no vía `SagaStep` — es la única acción de fase 1 que puede necesitar deshacerse) |
| 1 | — (`createPending`) | `orders.application.OrderService` | `orderRepository.save(... PENDING_PAYMENT)` | ninguna directa — si algo posterior de fase 1 falla, la compensación es liberar stock |
| 1 | — (`initiate`) | `payments.application.PaymentService` | `paymentRepository.save(... PENDING)` | ninguna directa (mismo motivo) |
| 2 (rechazo) | — | `payments`/`inventory`/`orders` | `reject()` + `release()` + `cancel(orderId)` | — (ya son la compensación) |
| 2 (aprobado) | `GenerateShippingStep` | `shipping.application` | `shippingService.generate(productId)` | `shippingService.cancel(shipment)` (vía `SagaOrchestrator`, si algo posterior falla) |
| 2 (aprobado) | `ConfirmOrderStep` | `orders.application` | `orderService.confirm(orderId)` | ninguna — último paso del sub-saga |
| 2 (aprobado, si el sub-saga falla) | — | `checkout.application.CheckoutUseCase` | — | `paymentService.refund(charged)` + `inventoryService.release(...)` + `orderService.cancel(orderId)`, fuera del `SagaOrchestrator` porque esos dos pasos ya se habían resuelto en fase 1 |

Fuente única de este archivo: `orchestrator/.../SagaOrchestrator.java` (mecánica LIFO del sub-saga
de fase 2) y `checkout/.../CheckoutUseCase.java` (las dos fases y su orquestación).
