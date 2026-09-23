package com.techpix.fraud.internal.rules.optimized;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudProfile;
import com.techpix.fraud.internal.FraudRule;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Uma consulta, quatro regras: velocity (1h/24h/7d), average-ticket, behavioral-hour e recent-rejections.
 * <p>
 * Mesmos limiares, mesmos pesos do perfil HEAVY. O que mudou foi o custo, não a cobertura.
 * Antes: 7 consultas mais N (uma por pagamento recente), carregando centenas de linhas.
 * Depois: 1 consulta agregada, usando índice, devolvendo 1 linha.
 */
@Component
public class PayerHistoryRule implements FraudRule {

    private final FraudHistoryAggregateRepository history;

    public PayerHistoryRule(FraudHistoryAggregateRepository history) {
        this.history = history;
    }

    @Override
    public String name() {
        return "payer-history";
    }

    @Override
    public Set<FraudProfile> profiles() {
        return EnumSet.of(FraudProfile.HEAVY_OPTIMIZED);
    }

    @Override
    public int evaluate(FraudCheck check) {
        PayerHistorySnapshot s = history.payerSnapshot(check.payerAccountId(), check.occurredAt(), check.paymentId());
        int points = 0;
        // velocity
        if (s.lastHour() > 10) {
            points += 20;
        }
        if (s.lastDay() > 50) {
            points += 20;
        }
        if (s.lastWeekTotal().compareTo(new BigDecimal("20000.00")) > 0) {
            points += 20;
        }
        // average-ticket
        if (s.total() >= 5 && check.amount().compareTo(s.averageAmount().multiply(BigDecimal.valueOf(5))) > 0) {
            points += 25;
        }
        // behavioral-hour
        if (s.total() >= 50 && (double) s.sameHourCount() / s.total() < 0.02) {
            points += 15;
        }
        // recent-rejections
        if (s.rejectedLastDay() >= 2) {
            points += 30;
        }
        return points;
    }
}
