package com.techpix.fraud.internal.strangler;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudEvaluator;
import com.techpix.fraud.FraudResult;
import com.techpix.fraud.FraudUnavailableException;
import com.techpix.fraud.internal.FraudService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Canary: uma fração dos pagamentos é decidida DE VERDADE pelo Fraud Service.
 * <p>
 * Diferença para o Parallel Run: aqui o novo produz efeito. Um cliente pode ter o pagamento
 * rejeitado pelo novo. Por isso começa em 1%.
 * <p>
 * Se o canário falhar (timeout, erro), o legado decide. O fallback é contado: uma taxa de fallback
 * alta é motivo para NÃO subir o percentual, e para investigar antes de tudo virar 100%.
 */
@Component
public class CanaryEvaluator implements FraudEvaluator {

    private static final Logger log = LoggerFactory.getLogger(CanaryEvaluator.class);

    private final FraudService legacy;
    private final RemoteFraudEvaluator remote;
    private final CanaryRouter router;
    private final Counter routedNew;
    private final Counter routedLegacy;
    private final Counter fallback;

    public CanaryEvaluator(FraudService legacy, RemoteFraudEvaluator remote, CanaryRouter router, MeterRegistry metrics) {
        this.legacy = legacy;
        this.remote = remote;
        this.router = router;
        this.routedNew = Counter.builder("fraud.canary.routed").tag("target", "new").register(metrics);
        this.routedLegacy = Counter.builder("fraud.canary.routed").tag("target", "legacy").register(metrics);
        this.fallback = Counter.builder("fraud.canary.fallback").description("Canario falhou; legado decidiu").register(metrics);
    }

    @Override
    public FraudResult evaluate(FraudCheck check) {
        if (!router.routeToNew(check.paymentId())) {
            routedLegacy.increment();
            return legacy.evaluate(check);
        }
        routedNew.increment();
        try {
            return remote.evaluate(check);
        } catch (FraudUnavailableException e) {
            fallback.increment();
            log.warn("canary payment={} fallback=legacy reason={}", check.paymentId(), e.getMessage());
            return legacy.evaluate(check);
        }
    }
}
