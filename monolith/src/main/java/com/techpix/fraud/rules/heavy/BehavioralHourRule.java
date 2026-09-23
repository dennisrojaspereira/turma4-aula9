package com.techpix.fraud.rules.heavy;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudHistoryRepository;
import com.techpix.fraud.FraudHistoryRepository.PaymentRow;
import com.techpix.fraud.FraudProfile;
import com.techpix.fraud.FraudRule;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * O pagador nunca paga nesse horário. Monta um histograma de hora do dia com todo o histórico.
 * Carrega o mesmo histórico completo que AverageTicketRule acabou de carregar. Ninguém percebeu.
 */
@Component
public class BehavioralHourRule implements FraudRule {

    private final FraudHistoryRepository history;

    public BehavioralHourRule(FraudHistoryRepository history) {
        this.history = history;
    }

    @Override
    public String name() {
        return "behavioral-hour";
    }

    @Override
    public Set<FraudProfile> profiles() {
        return EnumSet.of(FraudProfile.HEAVY);
    }

    @Override
    public int evaluate(FraudCheck check) {
        List<PaymentRow> all = history.findAllByPayer(check.payerAccountId(), check.paymentId());
        if (all.size() < 50) {
            return 0;
        }
        int[] histogram = new int[24];
        for (PaymentRow row : all) {
            histogram[row.createdAt().atZone(ZoneOffset.UTC).getHour()]++;
        }
        int hour = check.occurredAt().atZone(ZoneOffset.UTC).getHour();
        double share = (double) histogram[hour] / all.size();
        return share < 0.02 ? 15 : 0;
    }
}
