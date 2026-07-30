# Requerimientos propuestos — java-saga monolith

Este documento detalla tres funcionalidades de alto valor que pueden agregarse al proyecto, siguiendo la arquitectura hexagonal y el patrón saga ya establecido.

---

## 1. Suite completa de tests para el saga

**Estado actual:** `src/test` está vacío. No hay cobertura de la lógica de orquestación ni de los flujos de checkout (happy path + compensación).

**Motivo:** El CLAUDE.md documenta `./gradlew test --tests "com.saga.checkout.orchestrator.SagaOrchestratorTest"`, pero ese test no existe. Sin pruebas unitarias e integración, cambios en la secuencia de pasos o la lógica de compensación pueden romper silenciosamente.

### 1.1 Tests unitarios: `SagaOrchestratorTest`
Valida el motor genérico de saga en aislamiento (sin Spring, sin BD).

**Casos a cubrir:**
- ✓ Ejecuta los pasos en orden exacto
- ✓ Empuja cada paso exitoso a la pila de compensación
- ✓ Un paso que falla nunca se compensa a sí mismo
- ✓ Compensación corre en orden LIFO (más reciente primero)
- ✓ RuntimeException del paso se relanza tras compensar todos

**Implementación:**
```java
// src/test/java/com/saga/checkout/orchestrator/SagaOrchestratorTest.java
// Mock steps con flags para verificar orden de ejecución y compensación
```

### 1.2 Tests de integración: `CheckoutUseCaseIntegrationTest`
Verifica el flujo de checkout end-to-end con BD embebida (H2 o Testcontainers MariaDB).

**Happy path (200 OK):**
- Checkout exitoso → Order creada con status CONFIRMED
- Stock decrementado, Payment creado en estado CHARGED, Shipment en GENERATED

**Caso 1: Stock insuficiente (409 Conflict):**
- InsufficientStockException en ReserveStockStep
- Compensación vacía (es el primer paso)
- Order, Payment, Shipment = no existen
- Stock regresa a su valor inicial (nunca se reservó)

**Caso 2: Rechazo de pago (409 Conflict):**
- PaymentRejectedException en ChargePaymentStep (simulado con `payments.simulate.reject=true`)
- Compensación: ReserveStockStep.release() ejecuta antes
- Stock liberado, Payment no existe, Shipment no existe, Order no existe
- BD limpia excepto por el intento de pago rechazado (opcional registrar en log)

**Caso 3: Fallo de envío (409 Conflict):**
- ShippingFailedException en GenerateShippingStep (simulado con `shipping.simulate.fail=true`)
- Compensación: ChargePaymentStep.refund() + ReserveStockStep.release() en LIFO
- Payment refunded, Stock liberado, Shipment no existe, Order no existe

**Implementación:**
```java
// src/test/java/com/saga/checkout/application/CheckoutUseCaseIntegrationTest.java
// @SpringBootTest + TestContainers o BD embebida en application-test.yml
// Inyecta repositorios para verificar estado final en cada caso
```

### 1.3 Tests de controlador: `CheckoutControllerTest`
HTTP layer: request validation, response codes, exception handling.

**Casos:**
- Valid request → 200 CheckoutResponse con orderId y status
- Invalid request (missing field, negative amount) → 400
- Insufficient stock → 409 ErrorResponse
- Payment rejected → 409 ErrorResponse
- Shipping failed → 409 ErrorResponse

---

## 2. Endpoint para consultar pedidos

**Estado actual:** El único acceso a un `Order` es via `POST /checkout`, que retorna el id una sola vez. No existe forma de volver a consultar después.

**Motivo:** En una arquitectura real, un cliente necesita poder verificar el estado de su pedido sin repetir el checkout (idempotencia peligrosa).

### 2.1 Contrato REST

```
GET /orders/{id}
  ↓
200 OK
{
  "id": 1,
  "customerId": "cust-1",
  "productId": "sku-1",
  "quantity": 1,
  "amount": 100.0,
  "status": "CONFIRMED"
}
```

```
GET /orders/{id}
  (where id does not exist)
  ↓
404 Not Found
```

### 2.2 Cambios mínimos

**En `orders/domain/OrderRepository.java`:**
```java
Optional<Order> findById(Long id);
```

**En `orders/infrastructure/persistence/OrderRepositoryAdapter.java`:**
```java
@Override
public Optional<Order> findById(Long id) {
    return jpaRepository.findById(id).map(mapper::toDomain);
}
```

**Nuevo endpoint en `checkout/web/OrderController.java`:**
```java
@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderRepository orderRepository;

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        return orderRepository.findById(id)
                .map(order -> ResponseEntity.ok(new OrderResponse(
                        order.id(), order.customerId(), order.productId(), 
                        order.quantity(), order.amount(), order.status().name())))
                .orElse(ResponseEntity.notFound().build());
    }
}

record OrderResponse(Long id, String customerId, String productId, 
                    int quantity, double amount, String status) {}
```

### 2.3 Test
```bash
# Happy path
curl GET localhost:8080/orders/1
# -> 200 { "id": 1, "customerId": "cust-1", ... }

# Not found
curl GET localhost:8080/orders/999
# -> 404
```

---

## 3. Nuevo paso de saga: Sistema de puntos de lealtad

**Estado actual:** El saga tiene 4 pasos (Reserve Stock → Charge Payment → Generate Shipping → Create Order). Cumplen el flujo básico de checkout pero no capturan valor adicional (fidelización).

**Motivo:** Demuestra cómo extender el saga con un nuevo dominio sin modificar SagaOrchestrator (es genérico). La guía "Adding a new step to the checkout" en CLAUDE.md existe pero no tiene ejemplo implementado.

### 3.1 Flujo de negocio

Cuando un pedido se confirma exitosamente, el cliente gana puntos según el monto gastado:
- Fórmula: `puntos = floor(amount / points_per_currency_unit)`
- Los puntos se acreditan **después de cobrar** pero **antes de crear la orden** (así si algo falla después, los puntos no se gastan)
- Si se compensa (payment rechazado o shipping falla), los puntos se revocan

**Orden de pasos en CheckoutUseCase:**
```
1. ReserveStockStep
2. ChargePaymentStep
3. EarnLoyaltyPointsStep    ← NUEVO
4. GenerateShippingStep
5. CreateOrderStep
```

### 3.2 Estructura del dominio

**`src/main/java/com/saga/loyalty/domain/LoyaltyAccount.java`**
```java
public record LoyaltyAccount(String customerId, long points) {
    public LoyaltyAccount earn(long pointsEarned) {
        return new LoyaltyAccount(customerId, points + pointsEarned);
    }

    public LoyaltyAccount revoke(long pointsRevoked) {
        if (pointsRevoked > points) {
            throw new IllegalStateException("Insufficient loyalty points to revoke");
        }
        return new LoyaltyAccount(customerId, points - pointsRevoked);
    }
}
```

**`src/main/java/com/saga/loyalty/domain/LoyaltyAccountRepository.java`**
```java
public interface LoyaltyAccountRepository {
    Optional<LoyaltyAccount> findByCustomerId(String customerId);
    void save(LoyaltyAccount account);
}
```

**`src/main/java/com/saga/loyalty/application/LoyaltyService.java`**
```java
@Component
public class LoyaltyService {
    private final LoyaltyAccountRepository repository;
    @Value("${loyalty.points-per-currency-unit:100.0}")
    private double pointsPerCurrencyUnit;

    public LoyaltyAccount earnPoints(String customerId, double amount) {
        long earnedPoints = (long) (amount / pointsPerCurrencyUnit);
        LoyaltyAccount account = repository.findByCustomerId(customerId)
                .orElse(new LoyaltyAccount(customerId, 0L));
        LoyaltyAccount updated = account.earn(earnedPoints);
        repository.save(updated);
        return updated;
    }

    public void revokePoints(LoyaltyAccount account, long points) {
        LoyaltyAccount updated = account.revoke(points);
        repository.save(updated);
    }
}
```

**`src/main/java/com/saga/loyalty/application/EarnLoyaltyPointsStep.java`**
```java
public class EarnLoyaltyPointsStep implements SagaStep {
    private final LoyaltyService loyaltyService;
    private final String customerId;
    private final double amount;

    private LoyaltyAccount account;
    private long earnedPoints;

    public EarnLoyaltyPointsStep(LoyaltyService loyaltyService, String customerId, double amount) {
        this.loyaltyService = loyaltyService;
        this.customerId = customerId;
        this.amount = amount;
    }

    @Override
    public void execute() {
        account = loyaltyService.earnPoints(customerId, amount);
        earnedPoints = (long) (amount / 100.0); // default config value
    }

    @Override
    public void compensate() {
        loyaltyService.revokePoints(account, earnedPoints);
    }
}
```

### 3.3 Persistencia

**`src/main/java/com/saga/loyalty/infrastructure/persistence/entity/LoyaltyAccountEntity.java`**
```java
@Entity
@Table(name = "loyalty_account")
public class LoyaltyAccountEntity {
    @Id
    private String customerId;
    private long points;

    // constructors, getters, setters...
}
```

**`src/main/java/com/saga/loyalty/infrastructure/persistence/LoyaltyAccountRepositoryAdapter.java`**
```java
@Component
public class LoyaltyAccountRepositoryAdapter implements LoyaltyAccountRepository {
    private final LoyaltyAccountJpaRepository jpaRepository;
    private final LoyaltyAccountPersistenceMapper mapper;

    @Override
    public Optional<LoyaltyAccount> findByCustomerId(String customerId) {
        return jpaRepository.findById(customerId).map(mapper::toDomain);
    }

    @Override
    public void save(LoyaltyAccount account) {
        LoyaltyAccountEntity entity = jpaRepository.findById(account.customerId())
                .orElseGet(() -> jpaRepository.save(mapper.toEntity(account)));
        entity.setPoints(account.points());
    }
}
```

### 3.4 Integración en el saga

**En `checkout/application/CheckoutUseCase.java`:**
```java
public Order checkout(String customerId, String productId, int quantity, double amount) {
    CreateOrderStep createOrder = new CreateOrderStep(orderService, customerId, productId, quantity, amount);
    List<SagaStep> steps = List.of(
        new ReserveStockStep(inventoryService, productId, quantity),
        new ChargePaymentStep(paymentService, customerId, amount),
        new EarnLoyaltyPointsStep(loyaltyService, customerId, amount),  // ← NUEVO
        new GenerateShippingStep(shippingService, productId),
        createOrder
    );
    sagaOrchestrator.run(steps);
    return createOrder.order();
}
```

### 3.5 Configuración

**En `application.yml`:**
```yaml
loyalty:
  points-per-currency-unit: 100.0  # 100 currency units = 1 punto
```

### 3.6 Tests
- **Unit:** `LoyaltyServiceTest` — earn/revoke en aislamiento
- **Integration:** Verificar que los puntos se revocan correctamente en cada caso de fallo
  - Payment rejected → puntos revertidos
  - Shipping failed → puntos revertidos
  - Happy path → puntos acreditados y persistidos

---

## Prioridad sugerida

1. **Tests (req. 1)** → proporciona confianza para los cambios futuros (2 semanas de trabajo)
2. **Puntos de lealtad (req. 3)** → valida el patrón de extensión del saga, agrega negocio (1 semana)
3. **Endpoint de consulta (req. 2)** → es el más simple, complementa la UX (2 días)

---

## Conexión con CLAUDE.md

El req. 3 (Loyalty) es el ejemplo práctico que falta para la sección "Adding a new step to the checkout" en CLAUDE.md. Una vez implementado, esa sección puede referenciar `EarnLoyaltyPointsStep` como caso de uso real.
