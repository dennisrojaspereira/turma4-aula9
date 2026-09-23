package com.techpix.fraudservice.messaging;

import com.techpix.fraudservice.history.LocalViewUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consome {@code payment-events} no consumer group {@code fraud-service}.
 * <p>
 * Consumer group: todas as réplicas do Fraud Service dividem as partições entre si; cada evento é
 * entregue a UMA réplica. Offset: a posição de leitura do grupo, commitada depois de processar.
 * Se a réplica morrer entre processar e commitar, o próximo consumidor recebe o evento de novo.
 * Por isso {@link LocalViewUpdater#apply} é idempotente.
 */
@Component
@ConditionalOnProperty(name = "fraud.events.enabled", havingValue = "true")
public class PaymentEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventsConsumer.class);

    private final LocalViewUpdater updater;

    public PaymentEventsConsumer(LocalViewUpdater updater) {
        this.updater = updater;
    }

    @KafkaListener(topics = "payment-events", groupId = "fraud-service")
    public void on(@Payload PaymentEventMessage event,
                   @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                   @Header(KafkaHeaders.OFFSET) long offset) {
        log.debug("received {} payment={} partition={} offset={}", event.type(), event.paymentId(), partition, offset);
        updater.apply(event);
    }
}
