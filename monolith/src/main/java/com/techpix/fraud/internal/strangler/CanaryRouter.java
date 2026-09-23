package com.techpix.fraud.internal.strangler;

import com.techpix.fraud.internal.FraudProperties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Decide, por pagamento, se o canário (Fraud Service) ou o legado responde.
 * <p>
 * Determinístico: o mesmo {@code paymentId} sempre cai do mesmo lado, em qualquer réplica.
 * Isso permite reproduzir um caso específico depois ("esse pagamento foi pelo novo?").
 * <p>
 * A progressão é uma sequência de percentuais, não um botão:
 * <pre>
 * 1% -> 10% -> 50% -> 100%
 * </pre>
 * Cada degrau só é subido se os critérios do lab 15 passarem. Voltar é setar 0 ou trocar o modo.
 */
@Component
public class CanaryRouter {

    private final AtomicInteger percentage;

    public CanaryRouter(FraudProperties properties) {
        this.percentage = new AtomicInteger(clamp(properties.canary().percentage()));
    }

    public boolean routeToNew(UUID paymentId) {
        return bucket(paymentId) < percentage.get();
    }

    /** 0..99, estável para o mesmo id. */
    static int bucket(UUID paymentId) {
        long bits = paymentId.getMostSignificantBits() ^ paymentId.getLeastSignificantBits();
        return Math.floorMod(Long.hashCode(bits * 0x9E3779B97F4A7C15L), 100);
    }

    public int percentage() {
        return percentage.get();
    }

    public void setPercentage(int value) {
        percentage.set(clamp(value));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
