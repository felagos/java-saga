package com.saga.shared.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.function.Consumer;

/** Subscribes a handler to a NATS subject, deserializing each message as the given event type. */
@Component
public class SagaEventSubscriber {

    private final Connection natsConnection;
    private final ObjectMapper objectMapper;

    public SagaEventSubscriber(Connection natsConnection, ObjectMapper objectMapper) {
        this.natsConnection = natsConnection;
        this.objectMapper = objectMapper;
    }

    public <T> void subscribe(String subject, Class<T> eventType, Consumer<T> handler) {
        natsConnection.createDispatcher(msg -> {
            try {
                handler.accept(objectMapper.readValue(msg.getData(), eventType));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to deserialize saga event from " + subject, e);
            }
        }).subscribe(subject);
    }
}
