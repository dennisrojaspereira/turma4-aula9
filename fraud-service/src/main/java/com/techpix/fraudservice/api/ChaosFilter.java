package com.techpix.fraudservice.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Injeção de falhas, só para o laboratório: latência extra e/ou erro 500 em uma fração das avaliações.
 * <p>
 * É com isto que o lab 20 mostra, do lado do Payment, o que timeout, retry e fallback fazem de verdade.
 * Em produção, isso se chama chaos engineering e tem ferramenta própria. Aqui são 40 linhas.
 */
@Component
public class ChaosFilter extends OncePerRequestFilter {

    public record Chaos(long latencyMs, double errorRate) {
        static final Chaos OFF = new Chaos(0, 0);
    }

    private final AtomicReference<Chaos> chaos = new AtomicReference<>(Chaos.OFF);
    private final AtomicLong injectedErrors = new AtomicLong();
    private final AtomicLong injectedDelays = new AtomicLong();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/fraud/evaluations");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Chaos c = chaos.get();
        if (c.latencyMs() > 0) {
            injectedDelays.incrementAndGet();
            try {
                Thread.sleep(c.latencyMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (c.errorRate() > 0 && ThreadLocalRandom.current().nextDouble() < c.errorRate()) {
            injectedErrors.incrementAndGet();
            response.setStatus(500);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"chaos: injected failure\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    public void set(Chaos value) {
        chaos.set(value);
    }

    public Map<String, Object> status() {
        Chaos c = chaos.get();
        return Map.of("latencyMs", c.latencyMs(), "errorRate", c.errorRate(),
                "injectedErrors", injectedErrors.get(), "injectedDelays", injectedDelays.get());
    }
}
