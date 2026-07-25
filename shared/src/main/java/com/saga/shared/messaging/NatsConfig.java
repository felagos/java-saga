package com.saga.shared.messaging;

import io.nats.client.Connection;
import io.nats.client.Nats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class NatsConfig {

    @Bean(destroyMethod = "close")
    public Connection natsConnection(@Value("${nats.url}") String natsUrl) throws IOException, InterruptedException {
        return Nats.connect(natsUrl);
    }
}
