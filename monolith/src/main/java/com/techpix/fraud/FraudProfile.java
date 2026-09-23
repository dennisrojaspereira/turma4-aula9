package com.techpix.fraud;

/**
 * Qual conjunto de regras está ativo.
 * <p>
 * SIMPLE reproduz a Tech Pix do início: 5 regras baratas.
 * HEAVY reproduz a Tech Pix depois do crescimento: 17 regras escritas regra a regra, sem olhar o custo.
 * HEAVY_OPTIMIZED mantém a mesma cobertura de risco com consultas agregadas, índices e cache.
 * <p>
 * O perfil pode ser trocado em tempo de execução pelo endpoint administrativo, para que a aula
 * mostre o "antes" e o "depois" sem reiniciar nada.
 */
public enum FraudProfile {
    SIMPLE, HEAVY, HEAVY_OPTIMIZED
}
