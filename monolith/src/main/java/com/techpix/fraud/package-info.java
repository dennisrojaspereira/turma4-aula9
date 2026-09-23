/**
 * Módulo Fraud: decide se um pagamento é suspeito.
 * <p>
 * <b>API pública</b> (este pacote): {@link com.techpix.fraud.FraudEvaluator}, {@link com.techpix.fraud.FraudCheck},
 * {@link com.techpix.fraud.FraudResult}, {@link com.techpix.fraud.FraudDecision}, {@link com.techpix.fraud.FraudProfile}.
 * <p>
 * <b>Privado</b> ({@code internal}): regras, repositórios, perfis, configuração. Nenhum outro módulo pode importar nada dali.
 * Protegido por {@code ModuleBoundaryTest}.
 * <p>
 * Fraud é uma Business Capability: "avaliar risco de um pagamento". Não é "FraudRepositoryService" nem
 * "FraudControllerService". A fronteira existe pelo domínio, não pela camada técnica.
 */
package com.techpix.fraud;
