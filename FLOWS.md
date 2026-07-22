# Flujos del checkout — rama `monolith`

Todos los flujos que puede ejecutar el saga de checkout — definido en `CheckoutUseCase`
(`src/main/java/com/saga/checkout/application/CheckoutUseCase.java`) como una lista ordenada de
`SagaStep` (`src/main/java/com/saga/checkout/orchestrator/SagaStep.java`), ejecutada por
`SagaOrchestrator` (`.../checkout/orchestrator/SagaOrchestrator.java`) — con diagramas de secuencia. A
diferencia de `main` (microservicios sobre NATS), acá todo es in-process: cada flecha sólida es
una llamada directa a método Java (commitea su propia transacción local al retornar), cada
flecha punteada es el valor de retorno. No hay red, no hay async — el pedido completo pasa en
una sola request HTTP síncrona. El comportamiento externo (lo que muestran los diagramas de
abajo) no cambió con ese refactor — solo cambió *dónde* vive la mecánica LIFO (ver §6).

## Índice

1. [Pipeline de pasos](#1-pipeline-de-pasos)
2. [Happy path](#2-happy-path)
3. [Caso A — sin stock](#3-caso-a--sin-stock)
4. [Caso B — pago rechazado](#4-caso-b--pago-rechazado)
5. [Caso C — falla el envío](#5-caso-c--falla-el-envío)
6. [Tabla: paso → compensación](#6-tabla-paso--compensación)

## 1. Pipeline de pasos

Orden fijo en que `CheckoutUseCase.checkout()` llama a los servicios. Si cualquiera lanza
excepción, no se sigue avanzando — se pasa directo a compensar lo que ya se ejecutó.

```
          ┌─────────────┐
          │ CreateOrder │
          └─────────────┘
                 │
                 ▼
         ┌──────────────┐
         │ ReserveStock │
         └──────────────┘
                 │
                 ▼
         ┌───────────────┐
         │ ChargePayment │
         └───────────────┘
                 │
                 ▼
      ┌────────────────────┐
      │ GrantLoyaltyPoints │
      └────────────────────┘
                 │
                 ▼
       ┌──────────────────┐
       │ GenerateShipping │
       └──────────────────┘
                 │
                 ▼
         ┌──────────────┐
         │ ConfirmOrder │
         └──────────────┘
```

## 2. Happy path

Los 5 pasos tienen éxito; `CheckoutUseCase` nunca acumula nada que compensar y responde
`CONFIRMED` en la misma request.

```
  Cliente           Checkout           Orders           Inventory           Payments           Loyalty           Shipping
     │                  │                 │                 │                   │                 │                  │
     |--POST /checkout-->
     │                  │                 │                 │                   │                 │                  │
                        |----create()----->
     │                  │                 │                 │                   │                 │                  │
                        < -Order PENDING- |
     │                  │                 │                 │                   │                 │                  │
                        |-------------reserve()------------->
     │                  │                 │                 │                   │                 │                  │
                        < - - - - - - - -ok - - - - - - - - |
     │                  │                 │                 │                   │                 │                  │
                        |-----------------------charge()------------------------>
     │                  │                 │                 │                   │                 │                  │
                        < - - - - - - - - - -Payment CHARGED- - - - - - - - - - |
     │                  │                 │                 │                   │                 │                  │
                        |---------------------------------grant()--------------------------------->
     │                  │                 │                 │                   │                 │                  │
                        < - - - - - - - - - - - - - - -LoyaltyGrant - - - - - - - - - - - - - - - |
     │                  │                 │                 │                   │                 │                  │
                        |-----------------------------------------generate()----------------------------------------->
     │                  │                 │                 │                   │                 │                  │
                        < - - - - - - - - - - - - - - - - - - Shipment GENERATED- - - - - - - - - - - - - - - - - - -|
     │                  │                 │                 │                   │                 │                  │
                        |----confirm()---->
     │                  │                 │                 │                   │                 │                  │
                        < - - - ok- - - - |
     │                  │                 │                 │                   │                 │                  │
     < -200 CONFIRMED- -|
     │                  │                 │                 │                   │                 │                  │
```

## 3. Caso A — sin stock

`ReserveStock` es el primer paso que puede fallar (pedí `quantity` > stock sembrado). Solo hay
una compensación: deshacer `CreateOrder`, lo único que ya había tenido efecto.

```
  Cliente                   Checkout                   Orders                   Inventory
     │                          │                         │                         │
     |------POST /checkout------>
     │                          │                         │                         │
                                |--------create()--------->
     │                          │                         │                         │
                                < - - -Order PENDING- - - |
     │                          │                         │                         │
                                |---------------------reserve()--------------------->
     │                          │                         │                         │
                                < - - - - - -InsufficientStockException - - - - - - |
     │                          │                         │                         │
                                |--cancel()  [compensa]--->
     │                          │                         │                         │
                                < - - - - - ok- - - - - - |
     │                          │                         │                         │
     < -409 Insufficient stock -|
     │                          │                         │                         │
```

## 4. Caso B — pago rechazado

`CreateOrder` y `ReserveStock` ya tuvieron éxito cuando `ChargePayment` falla
(`payments.simulate.reject=true`). Compensación LIFO: primero se deshace lo más reciente
(`release()` sobre inventory), después lo más viejo (`cancel()` sobre el pedido).

```
  Cliente                     Checkout                     Orders                     Inventory                     Payments
     │                            │                           │                           │                             │
     |-------POST /checkout------->
     │                            │                           │                           │                             │
                                  |---------create()---------->
     │                            │                           │                           │                             │
                                  < - - - Order PENDING - - - |
     │                            │                           │                           │                             │
                                  |-----------------------reserve()----------------------->
     │                            │                           │                           │                             │
                                  < - - - - - - - - - - - - -ok - - - - - - - - - - - - - |
     │                            │                           │                           │                             │
                                  |--------------------------------------charge()--------------------------------------->
     │                            │                           │                           │                             │
                                  < - - - - - - - - - - - - - - -PaymentRejectedException - - - - - - - - - - - - - - - |
     │                            │                           │                           │                             │
                                  |---------------release()  [compensa #1]---------------->
     │                            │                           │                           │                             │
                                  < - - - - - - - - - - - - -ok - - - - - - - - - - - - - |
     │                            │                           │                           │                             │
                                  |--cancel()  [compensa #2]-->
     │                            │                           │                           │                             │
                                  < - - - - - -ok - - - - - - |
     │                            │                           │                           │                             │
     < - -409 Payment rejected - -|
     │                            │                           │                           │                             │
```

## 5. Caso C — falla el envío

Los primeros 4 pasos tienen éxito; `GenerateShipping` falla
(`shipping.simulate.fail=true`). Cadena completa de compensación LIFO — 4 pasos, en el orden
inverso exacto al que se ejecutaron:

```
  Cliente                     Checkout                     Orders                     Inventory                     Payments                     Loyalty                     Shipping
     │                            │                           │                           │                             │                           │                            │
     |-------POST /checkout------->
     │                            │                           │                           │                             │                           │                            │
                                  |---------create()---------->
     │                            │                           │                           │                             │                           │                            │
                                  < - - - Order PENDING - - - |
     │                            │                           │                           │                             │                           │                            │
                                  |-----------------------reserve()----------------------->
     │                            │                           │                           │                             │                           │                            │
                                  < - - - - - - - - - - - - -ok - - - - - - - - - - - - - |
     │                            │                           │                           │                             │                           │                            │
                                  |--------------------------------------charge()--------------------------------------->
     │                            │                           │                           │                             │                           │                            │
                                  < - - - - - - - - - - - - - - - - - Payment CHARGED - - - - - - - - - - - - - - - - - |
     │                            │                           │                           │                             │                           │                            │
                                  |-----------------------------------------------------grant()----------------------------------------------------->
     │                            │                           │                           │                             │                           │                            │
                                  < - - - - - - - - - - - - - - - - - - - - - - - - -LoyaltyGrant - - - - - - - - - - - - - - - - - - - - - - - - - |
     │                            │                           │                           │                             │                           │                            │
                                  |------------------------------------------------------------------generate()------------------------------------------------------------------>
     │                            │                           │                           │                             │                           │                            │
                                  < - - - - - - - - - - - - - - - - - - - - - - - - - - - - - ShippingFailedException - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -|
     │                            │                           │                           │                             │                           │                            │
                                  |---------------------------------------------revert()  [compensa #1]--------------------------------------------->
     │                            │                           │                           │                             │                           │                            │
                                  < - - - - - - - - - - - - - - - - - - - - - - - - - - - ok- - - - - - - - - - - - - - - - - - - - - - - - - - - - |
     │                            │                           │                           │                             │                           │                            │
                                  |-------------------------------refund()  [compensa #2]------------------------------->
     │                            │                           │                           │                             │                           │                            │
                                  < - - - - - - - - - - - - - - - - - - - - ok- - - - - - - - - - - - - - - - - - - - - |
     │                            │                           │                           │                             │                           │                            │
                                  |---------------release()  [compensa #3]---------------->
     │                            │                           │                           │                             │                           │                            │
                                  < - - - - - - - - - - - - -ok - - - - - - - - - - - - - |
     │                            │                           │                           │                             │                           │                            │
                                  |--cancel()  [compensa #4]-->
     │                            │                           │                           │                             │                           │                            │
                                  < - - - - - -ok - - - - - - |
     │                            │                           │                           │                             │                           │                            │
     < - -409 Shipping failed- - -|
     │                            │                           │                           │                             │                           │                            │
```

Nota: no hay reintento acá — a diferencia de `main`, donde `GenerateShipping` reintenta con
backoff antes de rendirse (paso "pivote"), en esta rama cualquier fallo compensa de inmediato.

## 6. Tabla: paso → compensación

| # | `SagaStep` | Paquete | `execute()` llama a | `compensate()` llama a |
|---|---|---|---|---|
| 1 | `CreateOrderStep` | `orders.application` | `orderService.create(...)` | `orderService.cancel(order)` |
| 2 | `ReserveStockStep` | `inventory.application` | `inventoryService.reserve(productId, qty)` | `inventoryService.release(productId, qty)` |
| 3 | `ChargePaymentStep` | `payments.application` | `paymentService.charge(...)` | `paymentService.refund(payment)` |
| 4 | `GrantLoyaltyPointsStep` | `loyalty.application` | `loyaltyService.grant(...)` | `loyaltyService.revert(grant)` |
| 5 | `GenerateShippingStep` | `shipping.application` | `shippingService.generate(productId)` | `shippingService.cancel(shipment)` |
| 6 | `ConfirmOrderStep` | `orders.application` | `orderService.confirm(order)` | (ninguna — último paso, nada corre después que pueda fallar) |

Cada `*Step` es un adaptador chico (no un bean de Spring — carga estado por-request) que delega
en el `*Service` de su dominio y guarda el resultado en un campo para que `compensate()` lo use
después. `CheckoutUseCase.checkout()` arma estos 6 objetos en orden en un `List<SagaStep>` y se
lo pasa a `SagaOrchestrator.run(steps)`, que es donde vive la mecánica LIFO en sí: empuja cada
paso a un `Deque` apenas su `execute()` tiene éxito; si cualquiera lanza `RuntimeException`,
corre `compensate()` sobre ese deque (LIFO — el más reciente primero) y re-lanza la excepción
original, que `CheckoutExceptionHandler` mapea a `409`. El paso que falló nunca se agrega al
deque, así que nunca se compensa a sí mismo (ver el trace de `ChargePaymentStep` fallando en
el caso B, §4: se compensan `ReserveStockStep` y `CreateOrderStep` — los dos que sí habían
tenido éxito — nunca `ChargePaymentStep` mismo).

`SagaOrchestrator` no sabe nada de checkout específicamente — serviría para cualquier lista de
`SagaStep`, no solo esta. La definición de *qué* pasos y en qué orden vive únicamente en
`CheckoutUseCase`.

Fuente única de este archivo: `src/main/java/com/saga/checkout/orchestrator/SagaOrchestrator.java`
(mecánica LIFO) y `src/main/java/com/saga/checkout/application/CheckoutUseCase.java` (los pasos
y su orden).
