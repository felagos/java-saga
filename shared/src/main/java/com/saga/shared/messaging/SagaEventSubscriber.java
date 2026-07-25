package com.saga.shared.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.function.Consumer;

/** Subscribes a handler to a NATS subject, deserializing each message as the given event type. */
@Component
public class SagaEventSubscriber {

    private static final Logger log = LoggerFactory.getLogger(SagaEventSubscriber.class);

    private final Connection natsConnection;
    private final ObjectMapper objectMapper;

    public SagaEventSubscriber(Connection natsConnection, ObjectMapper objectMapper) {
        this.natsConnection = natsConnection;
        this.objectMapper = objectMapper;
    }

    public <T> void subscribe(String subject, Class<T> eventType, Consumer<T> handler) {
        natsConnection.createDispatcher(msg -> {
            T event;
            try {
                event = objectMapper.readValue(msg.getData(), eventType);
            } catch (IOException e) {
                log.error("Failed to deserialize saga event from {}", subject, e);
                return;
            }
            try {
                handler.accept(event);
            } catch (RuntimeException e) {
                log.error("Handler failed for saga event from {}: {}", subject, event, e);
            }
        }).subscribe(subject);
    }
}
