package com.techpix.fraud;

import com.techpix.shared.observability.QueryCounter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Executa as regras do perfil ativo, soma os pontos e decide.
 * <p>
 * No início eram cinco regras baratas. Depois vieram dezenas. Cada uma foi adicionada
 * por um motivo de negócio legítimo. Ninguém olhou o custo somado.
 * <p>
 * A partir da etapa 3, cada regra é cronometrada e o número de consultas é contado.
 * As métricas ficam em {@code fraud.rule} (por regra), {@code fraud.evaluation} e {@code fraud.queries}.
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
    private final MeterRegistry metrics;

    public FraudService(List<FraudRule> rules, FraudProperties properties, FraudProfileConfig profile,
                        FraudEvaluationRepository evaluations, Clock clock, MeterRegistry metrics) {
        this.rules = rules;
        this.properties = properties;
        this.profile = profile;
        this.evaluations = evaluations;
        this.clock = clock;
        this.metrics = metrics;
    }

    public FraudResult evaluate(FraudCheck check) {
        long start = System.nanoTime();
        long queriesBefore = QueryCounter.current();
        FraudProfile active = profile.active();
        int score = 0;
        int evaluated = 0;
        List<String> triggered = new ArrayList<>();
        for (FraudRule rule : rules) {
            if (!rule.profiles().contains(active)) {
                continue;
            }
            evaluated++;
            long ruleStart = System.nanoTime();
            long ruleQueriesBefore = QueryCounter.current();
            int points = rule.evaluate(check);
            ruleTimer(rule, active).record(System.nanoTime() - ruleStart, TimeUnit.NANOSECONDS);
            ruleQueries(rule, active).record(QueryCounter.current() - ruleQueriesBefore);
            if (points > 0) {
                score += points;
                triggered.add(rule.name());
            }
        }
        FraudDecision decision = score >= properties.rejectThreshold() ? FraudDecision.REJECTED : FraudDecision.APPROVED;
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        FraudResult result = new FraudResult(score, decision, List.copyOf(triggered), evaluated, durationMs);
        evaluations.insert(check.paymentId(), result, Instant.now(clock));

        long queries = QueryCounter.current() - queriesBefore;
        Timer.builder("fraud.evaluation").tag("profile", active.name()).register(metrics)
                .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        DistributionSummary.builder("fraud.queries").tag("profile", active.name()).register(metrics).record(queries);
        log.debug("fraud payment={} profile={} rules={} queries={} score={} decision={} triggered={} durationMs={}",
                check.paymentId(), active, evaluated, queries, score, decision, triggered, durationMs);
        return result;
    }

    private Timer ruleTimer(FraudRule rule, FraudProfile active) {
        return Timer.builder("fraud.rule").tag("rule", rule.name()).tag("profile", active.name()).register(metrics);
    }

    private DistributionSummary ruleQueries(FraudRule rule, FraudProfile active) {
        return DistributionSummary.builder("fraud.rule.queries").tag("rule", rule.name()).tag("profile", active.name()).register(metrics);
    }

    public List<String> activeRuleNames() {
        FraudProfile active = profile.active();
        return rules.stream().filter(r -> r.profiles().contains(active)).map(FraudRule::name).toList();
    }
}
