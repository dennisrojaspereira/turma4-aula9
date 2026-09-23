package com.techpix.payment.internal.events;

import com.techpix.payment.PaymentEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Publica fatos de pagamento no tópico {@code payment-events}.
 * <p>
 * Chave = payerAccountId: todos os fatos do mesmo pagador vão para a mesma partição e chegam em ordem.
 * Fatos de pagadores diferentes podem chegar fora de ordem entre si. Isso é suficiente para Fraud.
 * <p>
 * A publicação acontece DEPOIS de o pagamento estar commitado. Se o Kafka estiver fora, o pagamento
 * já existe e o evento se perde: é o problema do dual write, discutido no lab 20 (outbox pattern).
 */
@Configuration
@ConditionalOnProperty(name = "techpix.events.enabled", havingValue = "true")
public class KafkaPaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaPaymentEventPublisher.class);
    public static final String TOPIC = "payment-events";

    @Bean
    NewTopic paymentEventsTopic() {
        return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    PaymentEventPublisher kafkaPublisher(KafkaTemplate<String, PaymentEvent> kafka, MeterRegistry metrics) {
        Counter published = Counter.builder("payment.events.published").register(metrics);
        Counter failed = Counter.builder("payment.events.failed").register(metrics);
        return event -> kafka.send(TOPIC, event.payerAccountId().toString(), event)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        failed.increment();
                        log.error("event {} payment={} NAO publicado: {}", event.type(), event.paymentId(), error.getMessage());
                    } else {
                        published.increment();
                        log.info("event {} payment={} eventId={} partition={} offset={}", event.type(), event.paymentId(),
                                event.eventId(), result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                });
    }
}
