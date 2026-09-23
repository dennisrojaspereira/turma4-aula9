package com.techpix.fraud.internal.strangler;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retry limitado, com backoff exponencial e jitter. Quarenta linhas, sem biblioteca, para que cada
 * decisão fique visível:
 * <ul>
 *   <li><b>limitado</b>: {@code maxAttempts}. Retry infinito transforma um incidente pequeno em um grande.</li>
 *   <li><b>backoff</b>: espera cresce a cada tentativa. Bater de novo imediatamente em um serviço
 *       sobrecarregado só o afunda mais.</li>
 *   <li><b>jitter</b>: espera aleatória em torno do backoff. Sem jitter, mil clientes que falharam
 *       juntos tentam de novo juntos (thundering herd).</li>
 *   <li><b>só o que vale a pena</b>: {@code retryable} decide. Timeout e 5xx, sim. 4xx, não: a
 *       requisição está errada, repetir não conserta.</li>
 * </ul>
 * O que este retry NÃO garante: que a chamada anterior não teve efeito. Se o Fraud Service gravou a
 * avaliação e a resposta se perdeu, a segunda tentativa grava de novo. Retry sem idempotência
 * do lado de lá duplica efeitos. Fraud tolera (avaliar duas vezes é inofensivo); um débito não toleraria.
 */
public final class RetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(RetryPolicy.class);

    private final int maxAttempts;
    private final long baseBackoffMs;
    private final long maxBackoffMs;
    private final double jitter;

    public RetryPolicy(int maxAttempts, long baseBackoffMs, long maxBackoffMs, double jitter) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseBackoffMs = baseBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
        this.jitter = jitter;
    }

    public interface Attempt<T> {
        T call() throws Exception;
    }

    public <T> T execute(String operation, Attempt<T> attempt, java.util.function.Predicate<Exception> retryable) throws Exception {
        Exception last = null;
        for (int n = 1; n <= maxAttempts; n++) {
            try {
                return attempt.call();
            } catch (Exception e) {
                last = e;
                if (n == maxAttempts || !retryable.test(e)) {
                    throw e;
                }
                long wait = backoffFor(n);
                log.warn("{} tentativa {}/{} falhou ({}); nova tentativa em {}ms", operation, n, maxAttempts, e.getMessage(), wait);
                sleep(wait);
            }
        }
        throw last;
    }

    /** exponencial (base * 2^(n-1)), limitado, com jitter uniforme de ±jitter. */
    long backoffFor(int attempt) {
        long exponential = Math.min(maxBackoffMs, baseBackoffMs * (1L << (attempt - 1)));
        double factor = 1 + (ThreadLocalRandom.current().nextDouble() * 2 - 1) * jitter;
        return Math.max(0, Math.round(exponential * factor));
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static Supplier<RetryPolicy> none() {
        return () -> new RetryPolicy(1, 0, 0, 0);
    }
}
