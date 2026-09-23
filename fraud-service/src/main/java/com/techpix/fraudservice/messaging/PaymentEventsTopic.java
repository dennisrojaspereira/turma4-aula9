package com.techpix.fraudservice.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * O consumidor tambem declara o topico, com as mesmas 3 particoes do produtor.
 * <p>
 * Sem isto, se o Fraud Service subir antes do monolito e o broker auto-criar o topico com 1 particao,
 * o consumidor fica preso a metadados antigos e nao ve as particoes criadas depois (ate o refresh, 5 min).
 * Por isso o broker do laboratorio roda com auto-create desligado: topicos sao criados de proposito.
 */
@Configuration
@ConditionalOnProperty(name = "fraud.events.enabled", havingValue = "true")
public class PaymentEventsTopic {

    @Bean
    NewTopic paymentEventsTopicDefinition() {
        return TopicBuilder.name("payment-events").partitions(3).replicas(1).build();
    }
}
