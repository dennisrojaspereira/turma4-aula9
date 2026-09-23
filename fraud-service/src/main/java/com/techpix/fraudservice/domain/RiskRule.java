package com.techpix.fraudservice.domain;

/** Uma regra devolve pontos de risco. Zero significa "não disparou". */
public interface RiskRule {

    String name();

    int evaluate(PaymentFacts facts);
}
