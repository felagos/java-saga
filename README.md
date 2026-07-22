# java-saga — rama `monolith`

Versión monolítica del mismo checkout de e-commerce implementado en `main` como 6 microservicios
orquestados por SAGA sobre NATS. Acá es **un solo proceso Spring Boot (`saga/`), una sola base de
datos** — pero sigue usando el **patrón saga con compensación explícita**: cada paso (crear
pedido, reservar stock, cobrar, sumar puntos, generar envío) commitea su propio cambio de
inmediato, y si uno falla, se compensan los pasos anteriores en orden LIFO. La diferencia con
`main` no es "sin saga" — es "saga sin red": nada de NATS, Outbox, idempotencia ni servicios
separados; todo son llamadas directas a método dentro del mismo proceso.

## Qué cambia respecto a `main`

- **Un solo proceso, una sola base** en vez de 6 servicios + 6 bases — las compensaciones son
  llamadas a método Java directas, no comandos NATS con reply async.
- **`POST /checkout` es síncrono**: responde el resultado final (éxito o motivo de error) en la
  misma request. No hay `sagaId` para hacer `GET /saga/{id}` — no hace falta, no hay latencia de
  red entre pasos que justifique un estado consultable aparte.
- **Sin forward-recovery de Shipping**: en `main`, un fallo de `GenerateShipping` se reintenta
  con backoff antes de rendirse. Acá cualquier fallo (incluido Shipping) compensa de inmediato —
  reintentar dentro de una request HTTP síncrona no es buena práctica.
- **Sin Outbox ni idempotencia de mensaje**: no hay mensajería que pueda reentregar un comando
  duplicado, así que no hace falta la guarda de duplicados que sí necesita cada microservicio.

## Estructura

Un solo proyecto Gradle, `saga/`. Cada antiguo microservicio es un paquete hermano bajo
`com.saga`, con su propio domain/application/infrastructure (mismo estilo hexagonal, ya no
separado por red ni por base):

```
saga/src/main/java/com/saga/
├── BffApplication.java
├── bff/
│   ├── application/CheckoutUseCase.java   orquestador único: pasos + pila de compensaciones LIFO
│   └── web/                               CheckoutController, DTOs, manejador de excepciones
├── orders/      {domain, application, infrastructure/persistence}
├── inventory/   {domain, application, infrastructure/persistence}
├── payments/    {domain, application, infrastructure/persistence}
├── loyalty/     {domain, application, infrastructure/persistence}
└── shipping/    {domain, application, infrastructure/persistence}
```

## Quick start

```bash
make up      # build + levanta MariaDB y bff
```

```bash
curl -s -X POST localhost:8080/checkout -H "Content-Type: application/json" \
  -d '{"customerId":"cust-1","productId":"sku-1","quantity":1,"amount":100.0}'
# -> 200 { "orderId": 1, "status": "CONFIRMED" }
```

Diagramas de todos los flujos (happy path + los 3 casos de fallo, con la cadena de compensación
LIFO paso a paso): **[FLOWS.md](FLOWS.md)**. Detalle de arranque y variables de simulación de
fallos: **[RUN.md](RUN.md)**. Comandos de desarrollo y arquitectura para trabajar en el código:
**[CLAUDE.md](CLAUDE.md)**.

Para comparar contra la versión microservicios (SAGA sobre NATS): `git checkout main`.
