package com.techpix.fraud.internal.strangler;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudEvaluator;
import com.techpix.fraud.FraudResult;
import com.techpix.fraud.internal.FraudProperties;
import com.techpix.fraud.internal.FraudService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Parallel Run.
 * <pre>
 *                  +--> LegacyFraud ----+
 * Payment ---------|                    |--> Comparator
 *                  +--> NewFraud -------+
 * </pre>
 * Só o legado produz o efeito oficial. O novo roda em shadow, em outro pool, com timeout.
 * Se o novo falhar, demorar ou ser descartado, o pagamento não sente: vira uma linha no relatório.
 */
@Component
public class ParallelRunEvaluator implements FraudEvaluator {

    private final FraudService legacy;
    private final RemoteFraudEvaluator remote;
    private final ShadowComparator comparator;
    private final ThreadPoolExecutor shadowExecutor;
    private final long shadowTimeoutMs;

    public ParallelRunEvaluator(FraudService legacy, RemoteFraudEvaluator remote, ShadowComparator comparator,
                                @Qualifier("shadowExecutor") ThreadPoolExecutor shadowExecutor, FraudProperties properties) {
        this.legacy = legacy;
        this.remote = remote;
        this.comparator = comparator;
        this.shadowExecutor = shadowExecutor;
        this.shadowTimeoutMs = properties.parallel().shadowTimeoutMs();
    }

    @Override
    public FraudResult evaluate(FraudCheck check) {
        // 1. Shadow parte primeiro, em outra thread. Nao bloqueia.
        CompletableFuture<TimedResult> shadow;
        try {
            shadow = CompletableFuture.supplyAsync(() -> {
                long start = System.nanoTime();
                FraudResult r = remote.evaluate(check);
                return new TimedResult(r, (System.nanoTime() - start) / 1_000_000);
            }, shadowExecutor).orTimeout(shadowTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            shadow = CompletableFuture.failedFuture(e);
        }

        // 2. O legado decide, na thread da requisicao. Este e o resultado oficial.
        long start = System.nanoTime();
        FraudResult official = legacy.evaluate(check);
        long legacyMs = (System.nanoTime() - start) / 1_000_000;

        // 3. A comparacao acontece quando o shadow terminar, sem segurar a resposta ao cliente.
        shadow.whenComplete((timed, error) -> {
            if (error == null) {
                comparator.compare(check, official, legacyMs, timed.result(), timed.ms());
                return;
            }
            Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
            ShadowComparator.Outcome outcome = cause instanceof TimeoutException ? ShadowComparator.Outcome.TIMEOUT
                    : cause instanceof RejectedExecutionException ? ShadowComparator.Outcome.SKIPPED
                    : ShadowComparator.Outcome.ERROR;
            comparator.shadowFailed(check, official, legacyMs, outcome, cause.getClass().getSimpleName() + ": " + cause.getMessage());
        });
        return official;
    }

    private record TimedResult(FraudResult result, long ms) {
    }
}
