-- Fraud Store: o que o Fraud Service possui. Migrations proprias, ciclo de vida proprio.
-- Nenhuma tabela aqui e de Payment ou Account. O historico de pagamentos e uma VISAO LOCAL,
-- alimentada por eventos (lab 19), nao a tabela original.

CREATE TABLE fraud_evaluations (
    id               UUID PRIMARY KEY,
    payment_id       UUID        NOT NULL,
    score            INTEGER     NOT NULL,
    decision         VARCHAR(20) NOT NULL,
    triggered_rules  TEXT        NOT NULL,
    duration_ms      BIGINT      NOT NULL,
    evaluated_at     TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_fraud_evaluations_payment ON fraud_evaluations (payment_id);

CREATE TABLE fraud_blacklist (
    id          UUID PRIMARY KEY,
    kind        VARCHAR(20)  NOT NULL,
    value       VARCHAR(64)  NOT NULL,
    reason      VARCHAR(200),
    created_at  TIMESTAMPTZ  NOT NULL,
    UNIQUE (kind, value)
);

-- A visao local: uma copia INTENCIONAL dos fatos de pagamento que Fraud precisa. Nada mais.
-- Sem rejection_reason, sem saldo, sem ledger. Duplicacao deliberada, com dono claro.
CREATE TABLE payment_history (
    payment_id        UUID PRIMARY KEY,
    payer_account_id  UUID           NOT NULL,
    payee_account_id  UUID           NOT NULL,
    amount            NUMERIC(18, 2) NOT NULL,
    device_id         VARCHAR(64),
    status            VARCHAR(20)    NOT NULL,   -- EVALUATED | APPROVED | REJECTED
    payer_opened_at   TIMESTAMPTZ,
    occurred_at       TIMESTAMPTZ    NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL
);
CREATE INDEX idx_payment_history_payer ON payment_history (payer_account_id, occurred_at);
CREATE INDEX idx_payment_history_payee ON payment_history (payee_account_id, occurred_at);
CREATE INDEX idx_payment_history_device ON payment_history (device_id, occurred_at);

-- Idempotencia do consumidor de eventos (lab 19): um evento processado duas vezes muda o estado uma vez.
CREATE TABLE processed_events (
    event_id      UUID PRIMARY KEY,
    event_type    VARCHAR(40) NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL
);
