package com.techpix.fraud;

/**
 * Qual conjunto de regras está ativo.
 * <p>
 * SIMPLE reproduz a Tech Pix do início: 5 regras baratas.
 * HEAVY reproduz a Tech Pix depois do crescimento: dezenas de regras, histórico, velocity,
 * device fingerprint, blacklist, provider externo e um modelo de ML simulado.
 * <p>
 * O perfil pode ser trocado em tempo de execução pelo endpoint administrativo, para que a aula
 * mostre o "antes" e o "depois" sem reiniciar nada.
 */
public enum FraudProfile {
    SIMPLE, HEAVY
}
