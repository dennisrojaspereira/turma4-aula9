package com.techpix.payment.internal.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Quando não há Kafka (labs 01 a 18, testes sem broker), os eventos viram uma linha de log.
 * O fluxo de pagamento não muda. Só ninguém está ouvindo.
 */
@Configuration
public class LoggingPaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingPaymentEventPublisher.class);

    @Bean
    @ConditionalOnMissingBean(PaymentEventPublisher.class)
    PaymentEventPublisher loggingPublisher() {
        return event -> log.debug("event {} payment={} (kafka desligado: techpix.events.enabled=false)", event.type(), event.paymentId());
    }
}
