package com.techpix.fraud.rules.heavy;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudHistoryRepository;
import com.techpix.fraud.FraudHistoryRepository.PaymentRow;
import com.techpix.fraud.FraudProfile;
import com.techpix.fraud.FraudRule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Valor muito acima do ticket médio histórico do pagador.
 * Carrega todo o histórico do pagador e calcula a média em Java. O banco sabe calcular médias.
 */
@Component
public class AverageTicketRule implements FraudRule {

    private final FraudHistoryRepository history;

    public AverageTicketRule(FraudHistoryRepository history) {
        this.history = history;
    }

    @Override
    public String name() {
        return "average-ticket";
    }

    @Override
    public Set<FraudProfile> profiles() {
        return EnumSet.of(FraudProfile.HEAVY);
    }

    @Override
    public int evaluate(FraudCheck check) {
        List<PaymentRow> all = history.findAllByPayer(check.payerAccountId(), check.paymentId());
        if (all.size() < 5) {
            return 0;
        }
        BigDecimal total = all.stream().map(PaymentRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = total.divide(BigDecimal.valueOf(all.size()), 2, RoundingMode.HALF_EVEN);
        return check.amount().compareTo(average.multiply(BigDecimal.valueOf(5))) > 0 ? 25 : 0;
    }
}
