# Cómo correr el monolito

## Opción A — todo en Docker

```bash
make up      # build de la imagen + levanta mariadb y bff
make logs    # seguir logs
make down    # apagar
make clean   # apagar y borrar el volumen de MariaDB
```

Puerto único: `bff` en `:8080`. MariaDB en `:3306`, base `monolith`, usuario `saga`/`saga`.

## Opción B — infra en Docker, bff local

```bash
make infra-up          # o: docker compose up -d (solo mariadb)
cd bff && ./gradlew bootRun
```

## Probar el happy path

```bash
curl -s -X POST localhost:8080/checkout -H "Content-Type: application/json" \
  -d '{"customerId":"cust-1","productId":"sku-1","quantity":1,"amount":100.0}'
# -> 200 { "orderId": 1, "status": "CONFIRMED" }
```

Verificá en MariaDB que las 5 tablas tienen una fila nueva:

```bash
docker exec -it $(docker compose -f docker-compose.full.yml ps -q mariadb) \
  mariadb -usaga -psaga monolith -e \
  "select * from customer_order; select * from stock; select * from payment; select * from loyalty_grant; select * from shipment;"
```

## Probar que el rollback es real (no hay compensación, hay ACID)

- **Sin stock**: pedí `quantity` mayor al stock sembrado (100 unidades de `sku-1`) →
  `409 { "reason": "Insufficient stock..." }`. Confirmá que **no** hay fila nueva en
  `customer_order` ni en ninguna otra tabla para esa request — la transacción nunca commiteó nada.
- **Pago rechazado**: seteá `PAYMENTS_SIMULATE_REJECT=true` en el servicio `bff` de
  `docker-compose.full.yml` (o `payments.simulate.reject: true` en `application.yml` para
  correrlo local) → `409 { "reason": "Payment gateway rejected..." }`. De nuevo, ninguna tabla
  queda con filas huérfanas — ni siquiera el pedido, aunque `OrderService.create` se ejecutó
  antes de que fallara el pago.
- **Shipping falla**: `SHIPPING_SIMULATE_FAIL=true` → mismo resultado: `409`, rollback completo.
  A diferencia de `main`, acá no hay reintentos con backoff — cualquier fallo revierte todo de
  inmediato.

## Tests

```bash
cd bff && ./gradlew test
```
