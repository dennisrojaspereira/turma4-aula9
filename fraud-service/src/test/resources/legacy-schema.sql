-- ATENCAO: isto e uma COPIA do schema do monolito, so para os testes do Fraud Service.
--
-- Este arquivo e a prova material do acoplamento: para testar o Fraud Service, precisamos
-- reproduzir tabelas que pertencem a Payment e Account. Se o monolito mudar V1/V2/V3, este arquivo
-- fica desatualizado em silencio. Ver lab 18 (Shared Database).

CREATE TABLE accounts (
    id          UUID PRIMARY KEY,
    owner_name  VARCHAR(120)   NOT NULL,
    balance     NUMERIC(18, 2) NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL
);

CREATE TABLE payments (
    id                UUID PRIMARY KEY,
    payer_account_id  UUID           NOT NULL REFERENCES accounts (id),
    payee_account_id  UUID           NOT NULL REFERENCES accounts (id),
    amount            NUMERIC(18, 2) NOT NULL,
    device_id         VARCHAR(64),
    status            VARCHAR(20)    NOT NULL,
    rejection_reason  VARCHAR(300),
    created_at        TIMESTAMPTZ    NOT NULL
);

CREATE TABLE fraud_evaluations (
    id               UUID PRIMARY KEY,
    payment_id       UUID        NOT NULL,
    score            INTEGER     NOT NULL,
    decision         VARCHAR(20) NOT NULL,
    triggered_rules  TEXT        NOT NULL,
    duration_ms      BIGINT      NOT NULL,
    evaluated_at     TIMESTAMPTZ NOT NULL
);

CREATE TABLE fraud_blacklist (
    id          UUID PRIMARY KEY,
    kind        VARCHAR(20)  NOT NULL,
    value       VARCHAR(64)  NOT NULL,
    reason      VARCHAR(200),
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_payments_payer_created ON payments (payer_account_id, created_at);
CREATE INDEX idx_payments_payee_created ON payments (payee_account_id, created_at);
CREATE INDEX idx_payments_device_created ON payments (device_id, created_at);
