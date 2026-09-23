package com.techpix.fraud.rules.heavy;

import com.techpix.fraud.FraudCheck;
import com.techpix.fraud.FraudProfile;
import com.techpix.fraud.FraudProperties;
import com.techpix.fraud.FraudRule;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Consulta a um provider externo de score (bureau). Simulado com uma espera fixa.
 * <p>
 * A espera acontece DENTRO da transação de banco do pagamento. Enquanto a thread dorme,
 * a conexão do pool continua ocupada. Esse é um dos motivos do pool saturar.
 */
@Component
public class ExternalProviderRule implements FraudRule {

    private final FraudProperties properties;

    public ExternalProviderRule(FraudProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "external-provider";
    }

    @Override
    public Set<FraudProfile> profiles() {
        return EnumSet.of(FraudProfile.HEAVY, FraudProfile.HEAVY_OPTIMIZED);
    }

    @Override
    public int evaluate(FraudCheck check) {
        try {
            Thread.sleep(properties.providerLatencyMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // O provider devolve um score determinístico a partir do destinatário: cerca de 5% dos destinatários são suspeitos.
        return Math.floorMod(check.payeeAccountId().hashCode(), 100) < 5 ? 20 : 0;
    }
}
