# Flujos del checkout — rama `monolith-async-payment`

El saga de checkout cruza un solo request HTTP (`POST /checkout`, fase 1) y de ahí en más es
**choreography vía NATS**: cada módulo escucha el evento que le importa, actúa, y publica el
siguiente. No hay orquestador central para esta parte — la compensación es distribuida, cada módulo
se compensa solo al escuchar el evento de falla que le corresponde. Cada paso (éxito o compensación)
queda auditado en `saga_audit_log`.

Definido en `checkout/.../CheckoutUseCase.java` (fase 1) y en los `*Listener` de cada módulo
(`payments`, `inventory`, `shipping`, `orders` — todos bajo su paquete `application/`). Las flechas
sólidas de fase 1 son llamadas directas a método Java; desde `saga.payment.initiated` en adelante,
cada flecha es un mensaje NATS.

## Índice

1. [Pipeline de eventos](#1-pipeline-de-eventos)
2. [Happy path](#2-happy-path)
3. [Caso A — sin stock (fase 1)](#3-caso-a--sin-stock-fase-1)
4. [Caso B — pago rechazado](#4-caso-b--pago-rechazado)
5. [Caso C — falla el envío (post-aprobación)](#5-caso-c--falla-el-envío-post-aprobación)
6. [Tabla: paso → compensación](#6-tabla-paso--compensación)

## 1. Pipeline de eventos

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
                 │
                 ▼
     publish saga.payment.initiated
                 │
  ════════════════════════════════ NATS ═══════════════════════════════
                 │
                 ▼
        payments: ¿aprobado?
        ┌────────┴────────┐
        │no                │sí
        ▼                  ▼
  publish                publish
  payment.rejected       payment.approved
        │                  │
   ┌────┴────┐             ▼
   ▼         ▼      shipping: generate()
inventory  orders          │
release    cancel     ┌────┴────┐
stock      order       │ok       │ShippingFailedException
                        ▼         ▼
                 publish        publish
                 shipping.      shipping.failed
                 generated        │
                     │       ┌────┼────┐
                     ▼       ▼    ▼    ▼
                  orders   payments inventory orders
                  confirm  refund   release   cancel
                     │
                     ▼
              publish order.confirmed
```

## 2. Happy path

```
  Cliente        Checkout        Inventory        Orders        Payments          NATS
     │               │               │               │              │              │
     |-POST /checkout->
                     |--reserve()---->
                     < - - -ok- - - -|
                     |------------------createPending()------------->
                     < - - - - - - - -Order PENDING_PAYMENT- - - - -|
                     |------------------------------------initiate()------------>
                     < - - - - - - - - - - - - -Payment PENDING- - - - - - - - -|
                     |----------------------------------------------------------publish payment.initiated->
     < -202 PENDING_PAYMENT- - - - - -|
     │               │               │               │              │              │
     ...la choreography corre sola, disparada por los listeners de cada módulo...
     │               │               │               │              │              │
                                                                    payments: confirm() -> Payment CHARGED
                                                                    publish payment.approved
                                                        shipping: generate() -> ok
                                                        publish shipping.generated
                                                  orders: confirm(orderId) -> Order CONFIRMED
                                                  publish order.confirmed
     │               │               │               │              │              │
     |-GET /orders/1---------------------------------->
     < - - - - - - - -{status: CONFIRMED}- - - - - - -|
```

## 3. Caso A — sin stock (fase 1)

Igual que en `monolith`: reservar stock falla antes de crear orden o pago — nada que compensar,
ninguna orden ni pago llegaron a existir, ningún evento se publica.

```
  Cliente          Checkout          Inventory
     │                 │                 │
     |--POST /checkout-->
                       |--reserve()------>
                       < - -InsufficientStockException- - |
     < -409 Insufficient stock- - - - - -|
```

## 4. Caso B — pago rechazado

Fase 1 tuvo éxito: hay `Order PENDING_PAYMENT` y `Payment PENDING`, y `saga.payment.initiated` ya se
publicó. `payments` decide rechazar (`payments.simulate.reject=true`) — publica
`saga.payment.rejected`, y `inventory`/`orders` cada uno, de forma independiente, se compensa al
escucharlo. Nadie HTTP espera este resultado — el cliente original ya recibió su `202`.

```
  payments              NATS              inventory              orders
     │                    │                     │                     │
   reject() -> Payment REJECTED
     |--publish payment.rejected---------------->|
                         |------------------------------------------->|
                         |--release(productId, qty)------------------>
                         < - - -Stock released- - - - - - - - - - - -|
                         |------------------------------cancel(orderId)-->
                         < - - - - - - - - - - - - - -Order CANCELLED- -|
```

## 5. Caso C — falla el envío (post-aprobación)

`payments` aprobó (`Payment CHARGED`), pero `shipping.generate()` falla
(`shipping.simulate.fail=true`). `shipping` publica `saga.shipping.failed` en vez de
`saga.shipping.generated` — tres listeners independientes (`payments`, `inventory`, `orders`)
escuchan ese mismo evento y cada uno deshace su propia parte, sin coordinación entre ellos.

```
  shipping                    NATS                 payments      inventory      orders
     │                          │                       │             │            │
  generate() -> ShippingFailedException
     |--publish shipping.failed------------------------->|
                                |----------------------------------->|
                                |-----------------------------------------------→|
                                |--------------------------------------------------------------→|
                                                                    refund()     release()     cancel(orderId)
```

## 6. Tabla: paso → compensación

| Fase | Paso | Módulo / clase | Dispara con | Compensación (evento que la dispara) |
|---|---|---|---|---|
| 1 | Reserve stock | `checkout.application.CheckoutUseCase` (inline) | `inventoryService.reserve(productId, qty)` | `inventoryService.release(...)` manual si falla algo más adelante en fase 1 |
| 1 | Create pending order | `checkout.application.CheckoutUseCase` (inline) | `orderService.createPending(...)` | ninguna directa — si falla, se libera el stock |
| 1 | Initiate payment | `checkout.application.CheckoutUseCase` (inline) | `paymentService.initiate(...)` | ninguna directa (mismo motivo) |
| async | Resolve payment | `payments.PaymentInitiatedListener` | `saga.payment.initiated` | rechazo: publica `saga.payment.rejected` |
| async | Release stock | `inventory.ReleaseStockListener` | `saga.payment.rejected` **o** `saga.shipping.failed` | — (ya es la compensación) |
| async | Cancel order | `orders.CancelOrderListener` | `saga.payment.rejected` **o** `saga.shipping.failed` | — (ya es la compensación) |
| async | Generate shipping | `shipping.PaymentApprovedListener` | `saga.payment.approved` | falla: publica `saga.shipping.failed` (no hay shipment que cancelar, nunca se creó) |
| async | Confirm order | `orders.ConfirmOrderListener` | `saga.shipping.generated` | ninguna — último paso del happy path |
| async | Refund payment | `payments.ShippingFailedRefundListener` | `saga.shipping.failed` | — (ya es la compensación) |

Fuente única de este archivo: `shared/.../events/SagaSubjects.java` (los asuntos NATS) y cada
`*Listener.java` en `payments`/`inventory`/`shipping`/`orders` (quién escucha qué y qué hace).
