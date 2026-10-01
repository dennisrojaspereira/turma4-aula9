package com.techpix.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Relógio dos testes de integração: continua andando (a ordem por created_at é preservada),
 * mas deslocado para o meio-dia UTC de um dia fixo.
 * <p>
 * Sem isto, a suíte rodada entre 00h e 05h UTC (19h–02h no horário de Brasília) ganharia +15
 * da {@code NightTimeRule} em todo pagamento e os scores esperados não bateriam.
 */
@TestConfiguration
public class TestClockConfig {

    private static final Instant ANCHOR = Instant.parse("2026-03-01T12:00:00Z");

    @Bean
    @Primary
    Clock testClock() {
        return Clock.offset(Clock.systemUTC(), Duration.between(Instant.now(), ANCHOR));
    }
}
