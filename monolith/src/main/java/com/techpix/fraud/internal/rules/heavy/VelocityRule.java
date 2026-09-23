package com.techpix.fraud.internal.rules.heavy;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.internal.FraudHistoryRepository;
import com.techpix.fraud.internal.FraudHistoryRepository.PaymentRow;
import com.techpix.fraud.FraudProfile;
import com.techpix.fraud.internal.FraudRule;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Velocity em três janelas: 1h, 24h e 7d.
 * <p>
 * Três consultas, cada uma carregando todas as linhas da janela para contar e somar em Java.
 * Sem índice em (payer_account_id, created_at), cada consulta varre a tabela inteira.
 */
@Component
public class VelocityRule implements FraudRule {

    private final FraudHistoryRepository history;

    public VelocityRule(FraudHistoryRepository history) {
        this.history = history;
    }

    @Override
    public String name() {
        return "velocity";
    }

    @Override
    public Set<FraudProfile> profiles() {
        return EnumSet.of(FraudProfile.HEAVY);
    }

    @Override
    public int evaluate(FraudCheck check) {
        int points = 0;
        List<PaymentRow> lastHour = history.findByPayerSince(check.payerAccountId(), check.occurredAt().minus(Duration.ofHours(1)), check.paymentId());
        if (lastHour.size() > 10) {
            points += 20;
        }
        List<PaymentRow> lastDay = history.findByPayerSince(check.payerAccountId(), check.occurredAt().minus(Duration.ofHours(24)), check.paymentId());
        if (lastDay.size() > 50) {
            points += 20;
        }
        List<PaymentRow> lastWeek = history.findByPayerSince(check.payerAccountId(), check.occurredAt().minus(Duration.ofDays(7)), check.paymentId());
        BigDecimal weekTotal = lastWeek.stream().map(PaymentRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weekTotal.compareTo(new BigDecimal("20000.00")) > 0) {
            points += 20;
        }
        return points;
    }
}
