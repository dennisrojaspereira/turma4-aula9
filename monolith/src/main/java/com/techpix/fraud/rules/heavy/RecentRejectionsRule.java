package com.techpix.fraud.rules.heavy;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudHistoryRepository;
import com.techpix.fraud.FraudHistoryRepository.PaymentRow;
import com.techpix.fraud.FraudProfile;
import com.techpix.fraud.FraudRule;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Pagador com rejeições recentes.
 * <p>
 * Carrega os pagamentos das últimas 24h e, para cada um, consulta a decisão de fraude.
 * O clássico N+1: 1 consulta para a lista, mais N consultas dentro do loop.
 */
@Component
public class RecentRejectionsRule implements FraudRule {

    private final FraudHistoryRepository history;

    public RecentRejectionsRule(FraudHistoryRepository history) {
        this.history = history;
    }

    @Override
    public String name() {
        return "recent-rejections";
    }

    @Override
    public Set<FraudProfile> profiles() {
        return EnumSet.of(FraudProfile.HEAVY);
    }

    @Override
    public int evaluate(FraudCheck check) {
        List<PaymentRow> recent = history.findByPayerSince(check.payerAccountId(), check.occurredAt().minus(Duration.ofHours(24)), check.paymentId());
        int rejected = 0;
        for (PaymentRow row : recent) {
            if ("REJECTED".equals(history.decisionOf(row.id()))) {
                rejected++;
            }
        }
        return rejected >= 2 ? 30 : 0;
    }
}
