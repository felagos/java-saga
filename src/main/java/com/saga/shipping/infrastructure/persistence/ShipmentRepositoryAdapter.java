package com.saga.shipping.infrastructure.persistence;

import com.saga.shipping.domain.Shipment;
import com.saga.shipping.domain.ShipmentRepository;
import com.saga.shipping.infrastructure.persistence.entity.ShipmentEntity;
import org.springframework.stereotype.Component;

@Component
public class ShipmentRepositoryAdapter implements ShipmentRepository {

    private final ShipmentJpaRepository jpaRepository;
    private final ShipmentPersistenceMapper mapper;

    public ShipmentRepositoryAdapter(ShipmentJpaRepository jpaRepository, ShipmentPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Shipment save(Shipment shipment) {
        if (shipment.id() == null) {
            ShipmentEntity saved = jpaRepository.save(mapper.toEntity(shipment));
            return mapper.toDomain(saved);
        }
        ShipmentEntity entity = jpaRepository.findById(shipment.id())
                .orElseThrow(() -> new IllegalStateException("Shipment not found: " + shipment.id()));
        entity.setStatus(shipment.status());
        return mapper.toDomain(entity);
    }
}
