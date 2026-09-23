package com.techpix.fraudservice.api;

import com.techpix.fraudservice.domain.PaymentFacts;
import com.techpix.fraudservice.domain.RiskAssessment;
import com.techpix.fraudservice.domain.RiskEngine;
import com.techpix.fraudservice.persistence.EvaluationStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A API do Fraud Service. Um endpoint, uma pergunta.
 * <p>
 * O contrato foi definido pelo consumidor (o monólito, lab 07) antes de este serviço existir.
 */
@RestController
@RequestMapping("/fraud")
public class FraudEvaluationController {

    private static final Logger log = LoggerFactory.getLogger(FraudEvaluationController.class);

    public record EvaluationRequest(
            @NotNull UUID paymentId,
            @NotNull UUID payerAccountId,
            @NotNull UUID payeeAccountId,
            @NotNull BigDecimal amount,
            String deviceId,
            Instant occurredAt) {
    }

    public record EvaluationResponse(
            UUID paymentId,
            int score,
            RiskAssessment.Decision decision,
            List<String> triggeredRules,
            int rulesEvaluated,
            long durationMs) {
    }

    private final RiskEngine engine;
    private final EvaluationStore store;
    private final Clock clock;
    private final Timer evaluationTimer;

    public FraudEvaluationController(RiskEngine engine, EvaluationStore store, Clock clock, MeterRegistry metrics) {
        this.engine = engine;
        this.store = store;
        this.clock = clock;
        this.evaluationTimer = Timer.builder("fraud.evaluation").description("Tempo de avaliacao no Fraud Service").register(metrics);
    }

    @PostMapping("/evaluations")
    public EvaluationResponse evaluate(@Valid @RequestBody EvaluationRequest request) {
        long start = System.nanoTime();
        Instant at = request.occurredAt() != null ? request.occurredAt() : Instant.now(clock);
        PaymentFacts facts = new PaymentFacts(request.paymentId(), request.payerAccountId(), request.payeeAccountId(),
                request.amount(), request.deviceId(), at);

        RiskAssessment assessment = engine.assess(facts);

        long durationMs = (System.nanoTime() - start) / 1_000_000;
        store.record(request.paymentId(), assessment, durationMs, Instant.now(clock));
        evaluationTimer.record(durationMs, TimeUnit.MILLISECONDS);
        log.info("evaluation payment={} score={} decision={} rules={} durationMs={}",
                request.paymentId(), assessment.score(), assessment.decision(), assessment.triggeredRules(), durationMs);
        return new EvaluationResponse(request.paymentId(), assessment.score(), assessment.decision(),
                assessment.triggeredRules(), assessment.rulesEvaluated(), durationMs);
    }
}
