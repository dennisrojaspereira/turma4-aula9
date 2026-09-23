package com.techpix.fraud;

import java.util.EnumSet;
import java.util.Set;

/**
 * Uma regra de fraude devolve pontos de risco. Zero significa "não disparou".
 * A soma dos pontos de todas as regras forma o score do pagamento.
 */
public interface FraudRule {

    String name();

    int evaluate(FraudCheck check);

    /** Em quais perfis esta regra participa. Por padrão, em todos. */
    default Set<FraudProfile> profiles() {
        return EnumSet.allOf(FraudProfile.class);
    }
}
