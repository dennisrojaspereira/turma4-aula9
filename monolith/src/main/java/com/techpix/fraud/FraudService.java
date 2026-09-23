package com.techpix.fraud;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Executa todas as regras registradas, soma os pontos e decide.
 * Cinco regras baratas. Com 10 TPS, ninguém olha para o custo de cada uma.
 */
@Service
@EnableConfigurationProperties(FraudProperties.class)
public class FraudService {

    private static final Logger log = LoggerFactory.getLogger(FraudService.class);

    private final List<FraudRule> rules;
    private final FraudProperties properties;
    private final FraudEvaluationRepository evaluations;
    private final Clock clock;

    public FraudService(List<FraudRule> rules, FraudProperties properties, FraudEvaluationRepository evaluations, Clock clock) {
        this.rules = rules;
        this.properties = properties;
        this.evaluations = evaluations;
        this.clock = clock;
    }

    public FraudResult evaluate(FraudCheck check) {
        long start = System.nanoTime();
        int score = 0;
        List<String> triggered = new ArrayList<>();
        for (FraudRule rule : rules) {
            int points = rule.evaluate(check);
            if (points > 0) {
                score += points;
                triggered.add(rule.name());
            }
        }
        FraudDecision decision = score >= properties.rejectThreshold() ? FraudDecision.REJECTED : FraudDecision.APPROVED;
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        FraudResult result = new FraudResult(score, decision, List.copyOf(triggered), durationMs);
        evaluations.insert(check.paymentId(), result, Instant.now(clock));
        log.debug("fraud payment={} score={} decision={} rules={} durationMs={}",
                check.paymentId(), score, decision, triggered, durationMs);
        return result;
    }

    public int ruleCount() {
        return rules.size();
    }
}
