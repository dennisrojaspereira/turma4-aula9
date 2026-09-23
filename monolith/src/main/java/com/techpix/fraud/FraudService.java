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
 * Executa as regras do perfil ativo, soma os pontos e decide.
 * <p>
 * No início eram cinco regras baratas. Depois vieram dezenas. Cada uma foi adicionada
 * por um motivo de negócio legítimo. Ninguém olhou o custo somado.
 */
@Service
@EnableConfigurationProperties(FraudProperties.class)
public class FraudService {

    private static final Logger log = LoggerFactory.getLogger(FraudService.class);

    private final List<FraudRule> rules;
    private final FraudProperties properties;
    private final FraudProfileConfig profile;
    private final FraudEvaluationRepository evaluations;
    private final Clock clock;

    public FraudService(List<FraudRule> rules, FraudProperties properties, FraudProfileConfig profile,
                        FraudEvaluationRepository evaluations, Clock clock) {
        this.rules = rules;
        this.properties = properties;
        this.profile = profile;
        this.evaluations = evaluations;
        this.clock = clock;
    }

    public FraudResult evaluate(FraudCheck check) {
        long start = System.nanoTime();
        FraudProfile active = profile.active();
        int score = 0;
        int evaluated = 0;
        List<String> triggered = new ArrayList<>();
        for (FraudRule rule : rules) {
            if (!rule.profiles().contains(active)) {
                continue;
            }
            evaluated++;
            int points = rule.evaluate(check);
            if (points > 0) {
                score += points;
                triggered.add(rule.name());
            }
        }
        FraudDecision decision = score >= properties.rejectThreshold() ? FraudDecision.REJECTED : FraudDecision.APPROVED;
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        FraudResult result = new FraudResult(score, decision, List.copyOf(triggered), evaluated, durationMs);
        evaluations.insert(check.paymentId(), result, Instant.now(clock));
        log.debug("fraud payment={} profile={} rules={} score={} decision={} triggered={} durationMs={}",
                check.paymentId(), active, evaluated, score, decision, triggered, durationMs);
        return result;
    }

    public List<String> activeRuleNames() {
        FraudProfile active = profile.active();
        return rules.stream().filter(r -> r.profiles().contains(active)).map(FraudRule::name).toList();
    }
}
