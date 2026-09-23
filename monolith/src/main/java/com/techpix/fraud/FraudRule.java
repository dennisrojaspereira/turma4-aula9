package com.techpix.fraud;

/**
 * Uma regra de fraude devolve pontos de risco. Zero significa "não disparou".
 * A soma dos pontos de todas as regras forma o score do pagamento.
 */
public interface FraudRule {

    String name();

    int evaluate(FraudCheck check);
}
