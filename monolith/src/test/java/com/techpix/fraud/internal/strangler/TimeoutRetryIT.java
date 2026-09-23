package com.techpix.fraud.internal.strangler;

import static org.assertj.core.api.Assertions.assertThat;

import com.techpix.fraud.FraudMode;
import com.techpix.support.AbstractIntegrationTest;
import com.techpix.support.FakeFraudService;
import com.techpix.support.FakeFraudService.Behaviour;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Rede falha. O que Payment faz a respeito.
 * Timeout curto (300 ms), 3 tentativas, backoff 50 ms: os numeros sao pequenos para o teste ser rapido.
 */
class TimeoutRetryIT extends AbstractIntegrationTest {

    static final FakeFraudService FRAUD_SERVICE = new FakeFraudService();

    @DynamicPropertySource
    static void remote(DynamicPropertyRegistry registry) {
        registry.add("techpix.fraud.remote.url", FRAUD_SERVICE::url);
        registry.add("techpix.fraud.remote.read-timeout-ms", () -> 300);
        registry.add("techpix.fraud.remote.retry.max-attempts", () -> 3);
        registry.add("techpix.fraud.remote.retry.base-backoff-ms", () -> 50);
        registry.add("techpix.fraud.remote.retry.max-backoff-ms", () -> 200);
    }

    @Autowired
    FraudModeConfig mode;
    @Autowired
    MeterRegistry metrics;

    @BeforeEach
    void setUp() {
        FRAUD_SERVICE.reset();
        mode.change(FraudMode.NEW);
    }

    @AfterEach
    void rollback() {
        mode.change(FraudMode.LEGACY);
    }

    @Test
    void transientErrorsAreRetriedAndThePaymentSucceeds() {
        // 500, 500, 200: indisponibilidade temporaria
        FRAUD_SERVICE.respondBySequence(call -> call < 3 ? Behaviour.error(500) : Behaviour.ok(0, "APPROVED"));
        UUID payer = openAccount("Retry Ok", "100.00");
        UUID payee = openAccount("Retry Ok Payee", "0.00");

        ResponseEntity<Map> response = pay(payer, payee, "1.00", "d");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status")).isEqualTo("APPROVED");
        assertThat(FRAUD_SERVICE.calls()).isEqualTo(3);
    }

    @Test
    void retriesAreBoundedAndTheClientGetsAClearAnswer() {
        FRAUD_SERVICE.respond(Behaviour.error(503));
        UUID payer = openAccount("Retry Bounded", "100.00");
        UUID payee = openAccount("Retry Bounded Payee", "0.00");

        long start = System.currentTimeMillis();
        ResponseEntity<Map> response = pay(payer, payee, "1.00", "d");
        long elapsed = System.currentTimeMillis() - start;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(FRAUD_SERVICE.calls()).isEqualTo(3);           // nao 4, nao infinito
        assertThat(elapsed).isLessThan(2_000);                     // backoff limitado: 50 + 100 (+-20%)
        assertThat((String) response.getBody().get("error")).contains("after 3 attempt");
    }

    @Test
    void timeoutsAreRetriedButStillBounded() {
        FRAUD_SERVICE.respond(Behaviour.ok(0, "APPROVED").withDelay(1_000));   // > 300 ms de read timeout
        UUID payer = openAccount("Retry Timeout", "100.00");
        UUID payee = openAccount("Retry Timeout Payee", "0.00");

        long start = System.currentTimeMillis();
        ResponseEntity<Map> response = pay(payer, payee, "1.00", "d");
        long elapsed = System.currentTimeMillis() - start;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(FRAUD_SERVICE.calls()).isEqualTo(3);
        // 3 x 300 ms de timeout + backoffs: bem menos que 3 x 1 s que o servico levaria para responder
        assertThat(elapsed).isBetween(900L, 2_500L);
    }

    @Test
    void clientErrorsAreNotRetriedBecauseRepeatingDoesNotFixTheRequest() {
        FRAUD_SERVICE.respond(Behaviour.error(400));
        UUID payer = openAccount("Retry 400", "100.00");
        UUID payee = openAccount("Retry 400 Payee", "0.00");

        ResponseEntity<Map> response = pay(payer, payee, "1.00", "d");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(FRAUD_SERVICE.calls()).isEqualTo(1);
    }

    @Test
    void retryWithoutIdempotencyOnTheOtherSideDuplicatesEffects() {
        // O servico PROCESSOU a 1a chamada (gravou), mas a resposta se perdeu (timeout). A 2a chamada processa de novo.
        // Este teste documenta o efeito: duas avaliacoes gravadas para um pagamento. Fraud tolera. Um debito nao toleraria.
        FRAUD_SERVICE.respondBySequence(call -> call == 1 ? Behaviour.ok(0, "APPROVED").withDelay(1_000) : Behaviour.ok(0, "APPROVED"));
        UUID payer = openAccount("Retry Dup", "100.00");
        UUID payee = openAccount("Retry Dup Payee", "0.00");

        ResponseEntity<Map> response = pay(payer, payee, "1.00", "d");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(FRAUD_SERVICE.calls()).isEqualTo(2);
        assertThat(FRAUD_SERVICE.requests().get(0).get("paymentId")).isEqualTo(FRAUD_SERVICE.requests().get(1).get("paymentId"));
    }
}
