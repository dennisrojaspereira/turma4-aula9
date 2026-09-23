package com.techpix.fraudservice.domain;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Executa as regras, soma os pontos, decide. É o coração do serviço e não sabe que HTTP ou banco existem.
 */
public class RiskEngine {

    private final List<RiskRule> rules;
    private final int rejectThreshold;
    private final MeterRegistry metrics;

    public RiskEngine(List<RiskRule> rules, int rejectThreshold, MeterRegistry metrics) {
        this.rules = List.copyOf(rules);
        this.rejectThreshold = rejectThreshold;
        this.metrics = metrics;
    }

    public RiskAssessment assess(PaymentFacts facts) {
        int score = 0;
        List<String> triggered = new ArrayList<>();
        for (RiskRule rule : rules) {
            long start = System.nanoTime();
            int points = rule.evaluate(facts);
            Timer.builder("fraud.rule").tag("rule", rule.name()).register(metrics)
                    .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            if (points > 0) {
                score += points;
                triggered.add(rule.name());
            }
        }
        RiskAssessment.Decision decision = score >= rejectThreshold ? RiskAssessment.Decision.REJECTED : RiskAssessment.Decision.APPROVED;
        return new RiskAssessment(score, decision, List.copyOf(triggered), rules.size());
    }

    public List<String> ruleNames() {
        return rules.stream().map(RiskRule::name).toList();
    }
}
