-- Etapa 2. Fraud ganhou uma blacklist de contas e dispositivos.
-- Adicionada às pressas depois de um incidente. Sem índice: "a tabela é pequena".

CREATE TABLE fraud_blacklist (
    id          UUID PRIMARY KEY,
    kind        VARCHAR(20)  NOT NULL,   -- ACCOUNT | DEVICE
    value       VARCHAR(64)  NOT NULL,
    reason      VARCHAR(200),
    created_at  TIMESTAMPTZ  NOT NULL
);
