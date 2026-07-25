# Ejemplos de uso — rama `monolith-async-payment`

Requiere `make up` (o `infra-up` + `bootRun`) corriendo, con NATS + MariaDB levantados.

## Happy path

```bash
curl -s -X POST localhost:8080/checkout -H "Content-Type: application/json" \
  -d '{"customerId":"cust-1","productId":"sku-1","quantity":1,"amount":100.0}'
# -> 202 { "orderId": 1, "paymentId": 1, "status": "PENDING_PAYMENT" }

# la choreography corre sola (payments -> shipping -> orders), esperar un instante
curl -s localhost:8080/orders/1
# -> { "orderId": 1, "status": "CONFIRMED" }
```

## Consultar estado de una orden

```bash
curl -s localhost:8080/orders/1
```

## Caso: sin stock (fase 1, síncrono)

```bash
curl -s -X POST localhost:8080/checkout -H "Content-Type: application/json" \
  -d '{"customerId":"cust-1","productId":"sku-1","quantity":9999,"amount":100.0}'
# -> 409 { "reason": "Insufficient stock for sku-1: requested 9999, available 100" }
```

## Caso: pago rechazado

Requiere reiniciar `bff` con `payments.simulate.reject=true` (o `PAYMENTS_SIMULATE_REJECT=true` en
el compose):

```bash
curl -s -X POST localhost:8080/checkout -H "Content-Type: application/json" \
  -d '{"customerId":"cust-1","productId":"sku-1","quantity":1,"amount":100.0}'
# -> 202 { "orderId": 2, "paymentId": 2, "status": "PENDING_PAYMENT" }

curl -s localhost:8080/orders/2
# -> { "orderId": 2, "status": "CANCELLED" }   (stock liberado, pago rechazado)
```

## Caso: falla el envío (post-aprobación)

Requiere reiniciar `bff` con `shipping.simulate.fail=true`:

```bash
curl -s -X POST localhost:8080/checkout -H "Content-Type: application/json" \
  -d '{"customerId":"cust-1","productId":"sku-1","quantity":1,"amount":100.0}'
# -> 202 { "orderId": 3, "paymentId": 3, "status": "PENDING_PAYMENT" }

curl -s localhost:8080/orders/3
# -> { "orderId": 3, "status": "CANCELLED" }   (pago con refund, stock liberado)
```

## Revisar la auditoría del saga

```bash
docker exec -it $(docker ps -qf name=mariadb) \
  mariadb -usaga -psaga monolith -e "SELECT order_id, step, outcome, detail, created_at FROM saga_audit_log ORDER BY order_id, created_at"
```

Ejemplo de salida para el happy path (`orderId=1`):

```
order_id  step               outcome      detail
1         STOCK_RESERVED     SUCCESS      Reserved 1 of sku-1
1         ORDER_CREATED      SUCCESS      Order created as PENDING_PAYMENT
1         PAYMENT_INITIATED  SUCCESS      Payment 1 handed to gateway
1         PAYMENT_APPROVED   SUCCESS      Payment 1 charged
1         SHIPPING_GENERATED SUCCESS      Shipment generated for sku-1
1         ORDER_CONFIRMED    SUCCESS      Order confirmed
```
