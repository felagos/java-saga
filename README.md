# java-saga — rama `monolith-async-payment`

Parte de `monolith` (checkout de e-commerce como un solo proceso Spring Boot, una sola base
MariaDB, patrón SAGA con compensación explícita, todo in-process) y le cambia una sola cosa: el
**cobro del pago pasa a ser asíncrono**. En la vida real, un gateway de pago no siempre resuelve al
instante — puede tardar horas y avisar el resultado después, vía callback. Reservar stock, generar
envío y confirmar la orden siguen siendo síncronos.

## Qué cambia respecto a `monolith`

- **`POST /checkout` ya no espera el resultado final**: responde `202 Accepted` con
  `{orderId, paymentId, status: "PENDING_PAYMENT"}` apenas el stock se reserva y el cobro se
  inicia con el gateway — no cuando se sabe si fue aprobado.
- **El resultado del pago llega por un segundo request**: `POST /payments/{paymentId}/callback
  {"approved": true|false}` simula el webhook del gateway (no hay gateway real en esta rama, se
  llama a mano o desde un test). Ahí se retoma el saga: si se aprobó, se genera el envío y se
  confirma la orden; si se rechazó, se libera el stock y se cancela la orden.
- **Nuevo `GET /orders/{orderId}`** para consultar el estado mientras el pago está pendiente — ya
  no aplica el "no hace falta polling" de `monolith`, ahora sí hay un estado intermedio que
  consultar.
- **Sin threads ni colas propias**: el desacople async se logra con dos requests HTTP
  independientes, no con `@Async`/executors ni mensajería. El estado que la segunda request
  necesita para retomar (`productId`, `quantity`, `orderId`) ya vive en las entidades `Order` y
  `Payment` — no hay una tabla nueva de "estado de saga".
- **Multi-módulo Gradle**: cada paso del saga es su propio subproyecto (`orders`, `inventory`,
  `payments`, `shipping`), más `orchestrator` (el motor genérico `SagaStep`/`SagaOrchestrator`) y
  `checkout` (el único módulo ejecutable — web + `CheckoutUseCase` + `CheckoutApplication`).

## Estructura

```
orchestrator/  {SagaStep, SagaOrchestrator — motor genérico, sin conocimiento de dominio}
orders/        {domain, application, infrastructure/persistence}
inventory/     {domain, application, infrastructure/persistence}
payments/      {domain, application, infrastructure/persistence}
shipping/      {domain, application, infrastructure/persistence}
checkout/      {CheckoutApplication, application/CheckoutUseCase, web/}
```

## Quick start

```bash
make up      # build + levanta MariaDB y bff
```

```bash
# Fase 1 — inicia el checkout, responde sin esperar el pago
curl -s -X POST localhost:8080/checkout -H "Content-Type: application/json" \
  -d '{"customerId":"cust-1","productId":"sku-1","quantity":1,"amount":100.0}'
# -> 202 { "orderId": 1, "paymentId": 1, "status": "PENDING_PAYMENT" }

# Fase 2 — simula el webhook del gateway, horas después
curl -s -X POST localhost:8080/payments/1/callback -H "Content-Type: application/json" \
  -d '{"approved": true}'

curl -s localhost:8080/orders/1
# -> { "orderId": 1, "status": "CONFIRMED" }
```

Para comparar contra el diseño 100% síncrono: `git checkout monolith`. Para comparar contra la
versión microservicios (SAGA sobre NATS): `git checkout main`.
