# Flujos del checkout — rama `monolith`

Todos los flujos que puede ejecutar el saga de checkout — definido en `CheckoutUseCase`
(`src/main/java/com/saga/checkout/application/CheckoutUseCase.java`) como una lista ordenada de
`SagaStep` (`src/main/java/com/saga/checkout/orchestrator/SagaStep.java`), ejecutada por
`SagaOrchestrator` (`.../checkout/orchestrator/SagaOrchestrator.java`) — con diagramas de secuencia. A
diferencia de `main` (microservicios sobre NATS), acá todo es in-process: cada flecha sólida es
una llamada directa a método Java (commitea su propia transacción local al retornar), cada
flecha punteada es el valor de retorno. No hay red, no hay async — el pedido completo pasa en
una sola request HTTP síncrona.

Orden de pasos: la orden nace **CONFIRMED directamente, como último paso**, recién
después de que el pago (el paso más propenso a fallar) tuvo éxito. Así nunca existe una orden en
un estado intermedio ("fantasma") — si algo falla antes, no hay ninguna orden que compensar.

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
       ┌──────────────────┐
       │ GenerateShipping │
       └──────────────────┘
                 │
                 ▼
          ┌─────────────┐
          │ CreateOrder │
          └─────────────┘
```

## 2. Happy path

Los 4 pasos tienen éxito; `CheckoutUseCase` nunca acumula nada que compensar y responde
`CONFIRMED` en la misma request. La orden recién existe en el último paso, ya `CONFIRMED`.

```
  Cliente           Checkout           Inventory           Payments           Shipping           Orders
     │                  │                 │                   │                  │                 │
     |--POST /checkout-->
     │                  │                 │                   │                  │                 │
                        |----reserve()---->
     │                  │                 │                   │                  │                 │
                        < - - - -ok - - - |
     │                  │                 │                   │                  │                 │
                        |----------------------charge()------->
     │                  │                 │                   │                  │                 │
                        < - - - - - - -Payment CHARGED- - - - |
     │                  │                 │                   │                  │                 │
                        |--------------------------------generate()------------->
     │                  │                 │                   │                  │                 │
                        < - - - - - - - - - - -Shipment GENERATED- - - - - - - - |
     │                  │                 │                   │                  │                 │
                        |------------------------------------------------create()---------------->
     │                  │                 │                   │                  │                 │
                        < - - - - - - - - - - - - - - - - - - -Order CONFIRMED- - - - - - - - - - -|
     │                  │                 │                   │                  │                 │
     < -200 CONFIRMED- -|
     │                  │                 │                   │                  │                 │
```

## 3. Caso A — sin stock

`ReserveStock` es el primer paso y también el primero que puede fallar (pedí `quantity` > stock
sembrado). Ningún paso anterior tuvo efecto todavía, así que no hay nada que compensar — ni
siquiera existe una orden.

```
  Cliente                   Checkout                   Inventory
     │                          │                         │
     |------POST /checkout------>
     │                          │                         │
                                |--------reserve()-------->
     │                          │                         │
                                < - - -InsufficientStockException- - - |
     │                          │                         │
     < -409 Insufficient stock -|
     │                          │                         │
```

## 4. Caso B — pago rechazado

`ReserveStock` ya tuvo éxito cuando `ChargePayment` falla (`payments.simulate.reject=true`).
Compensación LIFO: solo hay un paso previo exitoso, `release()` sobre inventory. Ninguna orden
llegó a crearse.

```
  Cliente                     Checkout                     Inventory                     Payments
     │                            │                           │                             │
     |-------POST /checkout------->
     │                            │                           │                             │
                                  |------reserve()------------>
     │                            │                           │                             │
                                  < - - - - - -ok - - - - - - |
     │                            │                           │                             │
                                  |--------------------------charge()------------------------>
     │                            │                           │                             │
                                  < - - - - - - - - - -PaymentRejectedException - - - - - - -|
     │                            │                           │                             │
                                  |--release()  [compensa #1]->
     │                            │                           │                             │
                                  < - - - - - -ok - - - - - - |
     │                            │                           │                             │
     < - -409 Payment rejected - -|
     │                            │                           │                             │
```

## 5. Caso C — falla el envío

Los primeros 2 pasos tienen éxito; `GenerateShipping` falla (`shipping.simulate.fail=true`).
Compensación LIFO — 2 pasos, en el orden inverso exacto al que se ejecutaron. Ninguna orden
llegó a crearse porque `CreateOrder` es el último paso, nunca alcanzado.

```
  Cliente                     Checkout                     Inventory                     Payments                     Shipping
     │                            │                           │                             │                            │
     |-------POST /checkout------->
     │                            │                           │                             │                            │
                                  |------reserve()------------>
     │                            │                           │                             │                            │
                                  < - - - - - -ok - - - - - - |
     │                            │                           │                             │                            │
                                  |--------------------------charge()------------------------>
     │                            │                           │                             │                            │
                                  < - - - - - - - - - -Payment CHARGED - - - - - - - - - - - |
     │                            │                           │                             │                            │
                                  |------------------------------------------generate()------------------------------->
     │                            │                           │                             │                            │
                                  < - - - - - - - - - - - - - - - - -ShippingFailedException - - - - - - - - - - - - -|
     │                            │                           │                             │                            │
                                  |---------------------------refund()  [compensa #1]-------->
     │                            │                           │                             │                            │
                                  < - - - - - - - - - -ok - - - - - - - - - - - - - - - - - -|
     │                            │                           │                             │                            │
                                  |--release()  [compensa #2]->
     │                            │                           │                             │                            │
                                  < - - - - - -ok - - - - - - |
     │                            │                           │                             │                            │
     < - -409 Shipping failed- - -|
     │                            │                           │                             │                            │
```

Nota: no hay reintento acá — a diferencia de `main`, donde `GenerateShipping` reintenta con
backoff antes de rendirse (paso "pivote"), en esta rama cualquier fallo compensa de inmediato.

## 6. Tabla: paso → compensación

| # | `SagaStep` | Paquete | `execute()` llama a | `compensate()` llama a |
|---|---|---|---|---|
| 1 | `ReserveStockStep` | `inventory.application` | `inventoryService.reserve(productId, qty)` | `inventoryService.release(productId, qty)` |
| 2 | `ChargePaymentStep` | `payments.application` | `paymentService.charge(...)` | `paymentService.refund(payment)` |
| 3 | `GenerateShippingStep` | `shipping.application` | `shippingService.generate(productId)` | `shippingService.cancel(shipment)` |
| 4 | `CreateOrderStep` | `orders.application` | `orderService.create(...)` (nace `CONFIRMED`) | (ninguna — último paso, nada corre después que pueda fallar) |

Cada `*Step` es un adaptador chico (no un bean de Spring — carga estado por-request) que delega
en el `*Service` de su dominio y guarda el resultado en un campo para que `compensate()` lo use
después. `CheckoutUseCase.checkout()` arma estos 4 objetos en orden en un `List<SagaStep>` y se
lo pasa a `SagaOrchestrator.run(steps)`, que es donde vive la mecánica LIFO en sí: empuja cada
paso a un `Deque` apenas su `execute()` tiene éxito; si cualquiera lanza `RuntimeException`,
corre `compensate()` sobre ese deque (LIFO — el más reciente primero) y re-lanza la excepción
original, que `CheckoutExceptionHandler` mapea a `409`. El paso que falló nunca se agrega al
deque, así que nunca se compensa a sí mismo (ver el trace de `GenerateShippingStep` fallando en
el caso C, §5: se compensan `ChargePaymentStep` y `ReserveStockStep` — los dos que sí habían
tenido éxito — nunca `GenerateShippingStep` mismo).

`SagaOrchestrator` no sabe nada de checkout específicamente — serviría para cualquier lista de
`SagaStep`, no solo esta. La definición de *qué* pasos y en qué orden vive únicamente en
`CheckoutUseCase`.

Fuente única de este archivo: `src/main/java/com/saga/checkout/orchestrator/SagaOrchestrator.java`
(mecánica LIFO) y `src/main/java/com/saga/checkout/application/CheckoutUseCase.java` (los pasos
y su orden).
