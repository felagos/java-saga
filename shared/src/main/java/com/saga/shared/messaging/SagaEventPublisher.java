package com.saga.shared.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import org.springframework.stereotype.Component;

@Component
public class SagaEventPublisher {

    private final Connection natsConnection;
    private final ObjectMapper objectMapper;

    public SagaEventPublisher(Connection natsConnection, ObjectMapper objectMapper) {
        this.natsConnection = natsConnection;
        this.objectMapper = objectMapper;
    }

    public void publish(String subject, Object event) {
        try {
            natsConnection.publish(subject, objectMapper.writeValueAsBytes(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize saga event for " + subject, e);
        }
    }
}
