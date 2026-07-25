# java-saga — rama `monolith-async-payment`

Parte de `monolith` (checkout de e-commerce como un solo proceso Spring Boot, una sola base
MariaDB, patrón SAGA con compensación explícita, todo in-process) y le cambia una sola cosa: el
**cobro del pago pasa a ser asíncrono**. En la vida real, un gateway de pago no siempre resuelve al
instante — puede tardar horas y avisar el resultado después. Acá el desacople se modela con NATS:
`payments` lee el pago iniciado de un topic, decide el resultado, y publica un evento — cada módulo
que necesite reaccionar (avanzar el saga o compensar) escucha su propio evento. No hay un
orquestador central para esta parte, ni un callback HTTP manual.

## Qué cambia respecto a `monolith`

- **`POST /checkout` ya no espera el resultado final**: responde `202 Accepted` con
  `{orderId, paymentId, status: "PENDING_PAYMENT"}` apenas el stock se reserva, la orden se crea
  pendiente y el pago se inicia — publica `saga.payment.initiated` en NATS y retorna, sin esperar
  la decisión del gateway.
- **El resto del saga es choreography vía NATS, no un segundo request HTTP**: `payments` escucha
  `saga.payment.initiated`, simula el cobro (`payments.simulate.reject`) y publica
  `saga.payment.approved`/`saga.payment.rejected`. Desde ahí, `shipping` genera el envío,
  `orders` confirma o cancela la orden, e `inventory`/`payments` liberan stock o hacen refund —
  cada uno reaccionando a los eventos que le importan, de forma independiente. No hay endpoint de
  callback: nadie dispara el resultado desde afuera.
- **`GET /orders/{orderId}`** sigue siendo la única forma de consultar el estado final — más
  necesario que nunca, no hay ninguna respuesta síncrona ni callback que lo entregue.
- **Auditoría en base de datos**: cada paso (éxito o compensación) de cualquier módulo escribe una
  fila en `saga_audit_log` (`orderId`, `step`, `outcome`, `detail`, `createdAt`).
- **Multi-módulo Gradle**: cada paso del saga es su propio subproyecto (`orders`, `inventory`,
  `payments`, `shipping`), más `shared` (conexión NATS, eventos, publisher/subscriber, auditoría —
  sin conocimiento de dominio) y `checkout` (el único módulo ejecutable — web + fase 1 del saga +
  `CheckoutApplication`).

## Estructura

```
shared/        {NATS connection, events, SagaEventPublisher/Subscriber, SagaAuditService}
orders/        {domain, application (incl. listeners NATS), infrastructure/persistence}
inventory/     {domain, application (incl. listeners NATS), infrastructure/persistence}
payments/      {domain, application (incl. listeners NATS), infrastructure/persistence}
shipping/      {domain, application (incl. listeners NATS), infrastructure/persistence}
checkout/      {CheckoutApplication, application/CheckoutUseCase (fase 1), web/}
```

## Quick start

```bash
make up      # build + levanta NATS, MariaDB y bff
```

```bash
# Único request: reserva stock, crea la orden pendiente, inicia el pago, publica en NATS
curl -s -X POST localhost:8080/checkout -H "Content-Type: application/json" \
  -d '{"customerId":"cust-1","productId":"sku-1","quantity":1,"amount":100.0}'
# -> 202 { "orderId": 1, "paymentId": 1, "status": "PENDING_PAYMENT" }

# El resto corre solo, disparado por los listeners NATS de cada módulo
curl -s localhost:8080/orders/1
# -> { "orderId": 1, "status": "CONFIRMED" }
```

Para comparar contra el diseño 100% síncrono: `git checkout monolith`. Para comparar contra la
versión microservicios (SAGA sobre NATS): `git checkout main`.
