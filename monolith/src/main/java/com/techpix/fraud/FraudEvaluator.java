package com.techpix.fraud;

/**
 * O único ponto de entrada do módulo Fraud.
 * <p>
 * Payment conhece esta interface e os tipos ao lado dela ({@link FraudCheck}, {@link FraudResult}).
 * Não conhece regras, repositórios, perfis nem como a avaliação é feita.
 * <p>
 * Hoje a implementação roda no mesmo processo. Amanhã pode rodar em outro. Payment não precisa saber.
 */
public interface FraudEvaluator {

    FraudResult evaluate(FraudCheck check);
}
