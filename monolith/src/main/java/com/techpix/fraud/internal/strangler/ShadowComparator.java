package com.techpix.fraud.internal.strangler;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Compara a decisão oficial (legado) com a decisão em shadow (novo) e registra o que viu.
 * <pre>
 * legacyScore   newScore   difference   legacyLatency   newLatency   decisionMismatch
 * </pre>
 * Cada comparação vira uma linha de log, um contador por desfecho e uma entrada no relatório
 * em memória ({@code GET /admin/fraud/parallel-run}). É com isso que se decide se o novo está pronto.
 */
@Component
public class ShadowComparator {

    private static final Logger log = LoggerFactory.getLogger(ShadowComparator.class);
    private static final int KEEP_LAST = 50;

    public enum Outcome {
        MATCH, SCORE_MISMATCH, DECISION_MISMATCH, ERROR, TIMEOUT, SKIPPED
    }

    public record Comparison(UUID paymentId, Outcome outcome, Integer legacyScore, Integer newScore,
                             String legacyDecision, String newDecision, long legacyLatencyMs, Long newLatencyMs,
                             String detail, Instant at) {
    }

    private final MeterRegistry metrics;
    private final DistributionSummary scoreDifference;
    private final Timer legacyLatency;
    private final Timer newLatency;
    private final AtomicLong total = new AtomicLong();
    private final Map<Outcome, AtomicLong> byOutcome = new java.util.EnumMap<>(Outcome.class);
    private final Deque<Comparison> divergences = new ArrayDeque<>();

    public ShadowComparator(MeterRegistry metrics) {
        this.metrics = metrics;
        this.scoreDifference = DistributionSummary.builder("fraud.parallel.score.difference")
                .description("|newScore - legacyScore| por pagamento").register(metrics);
        this.legacyLatency = Timer.builder("fraud.parallel.latency").tag("impl", "legacy").register(metrics);
        this.newLatency = Timer.builder("fraud.parallel.latency").tag("impl", "new").register(metrics);
        for (Outcome o : Outcome.values()) {
            byOutcome.put(o, new AtomicLong());
        }
    }

    public void compare(FraudCheck check, FraudResult legacy, long legacyMs, FraudResult fresh, long newMs) {
        Outcome outcome;
        if (legacy.decision() != fresh.decision()) {
            outcome = Outcome.DECISION_MISMATCH;
        } else if (legacy.score() != fresh.score()) {
            outcome = Outcome.SCORE_MISMATCH;
        } else {
            outcome = Outcome.MATCH;
        }
        scoreDifference.record(Math.abs(fresh.score() - legacy.score()));
        legacyLatency.record(legacyMs, TimeUnit.MILLISECONDS);
        newLatency.record(newMs, TimeUnit.MILLISECONDS);
        record(new Comparison(check.paymentId(), outcome, legacy.score(), fresh.score(),
                legacy.decision().name(), fresh.decision().name(), legacyMs, newMs,
                "legacyRules=" + legacy.triggeredRules() + " newRules=" + fresh.triggeredRules(), Instant.now()));
    }

    public void shadowFailed(FraudCheck check, FraudResult legacy, long legacyMs, Outcome outcome, String detail) {
        record(new Comparison(check.paymentId(), outcome, legacy.score(), null, legacy.decision().name(), null,
                legacyMs, null, detail, Instant.now()));
    }

    private void record(Comparison c) {
        total.incrementAndGet();
        byOutcome.get(c.outcome()).incrementAndGet();
        Counter.builder("fraud.parallel.comparisons").tag("outcome", c.outcome().name().toLowerCase()).register(metrics).increment();
        if (c.outcome() == Outcome.MATCH) {
            log.debug("parallel-run payment={} outcome=MATCH score={} legacyMs={} newMs={}",
                    c.paymentId(), c.legacyScore(), c.legacyLatencyMs(), c.newLatencyMs());
            return;
        }
        log.warn("parallel-run payment={} outcome={} legacyScore={} newScore={} difference={} legacyDecision={} newDecision={} legacyMs={} newMs={} {}",
                c.paymentId(), c.outcome(), c.legacyScore(), c.newScore(),
                c.newScore() == null ? "n/a" : Math.abs(c.newScore() - c.legacyScore()),
                c.legacyDecision(), c.newDecision(), c.legacyLatencyMs(), c.newLatencyMs(), c.detail());
        synchronized (divergences) {
            divergences.addFirst(c);
            while (divergences.size() > KEEP_LAST) {
                divergences.removeLast();
            }
        }
    }

    public Map<String, Object> summary() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (Outcome o : Outcome.values()) {
            counts.put(o.name().toLowerCase(), byOutcome.get(o).get());
        }
        long t = total.get();
        long matches = byOutcome.get(Outcome.MATCH).get();
        List<Comparison> last;
        synchronized (divergences) {
            last = List.copyOf(divergences);
        }
        Map<String, Object> summary = new java.util.LinkedHashMap<>();
        summary.put("total", t);
        summary.put("matchRate", t == 0 ? null : (double) matches / t);
        summary.put("outcomes", counts);
        summary.put("legacyLatencyMeanMs", legacyLatency.mean(TimeUnit.MILLISECONDS));
        summary.put("newLatencyMeanMs", newLatency.mean(TimeUnit.MILLISECONDS));
        summary.put("scoreDifferenceMean", scoreDifference.mean());
        summary.put("lastDivergences", last);
        return summary;
    }

    public long count(Outcome outcome) {
        return byOutcome.get(outcome).get();
    }

    public void reset() {
        total.set(0);
        byOutcome.values().forEach(v -> v.set(0));
        synchronized (divergences) {
            divergences.clear();
        }
    }
}
