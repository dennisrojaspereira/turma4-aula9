-- Tech Pix, etapa 1. Um schema, cinco capacidades de negócio, uma transação por pagamento.
-- Repare: nenhum índice além das chaves primárias. Com 10 TPS e poucas linhas, ninguém sente falta.

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

CREATE TABLE ledger_entries (
    id          UUID PRIMARY KEY,
    payment_id  UUID           NOT NULL REFERENCES payments (id),
    account_id  UUID           NOT NULL REFERENCES accounts (id),
    entry_type  VARCHAR(10)    NOT NULL,
    amount      NUMERIC(18, 2) NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL
);

CREATE TABLE notifications (
    id          UUID PRIMARY KEY,
    account_id  UUID         NOT NULL REFERENCES accounts (id),
    message     VARCHAR(300) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE fraud_evaluations (
    id               UUID PRIMARY KEY,
    payment_id       UUID        NOT NULL REFERENCES payments (id),
    score            INTEGER     NOT NULL,
    decision         VARCHAR(20) NOT NULL,
    triggered_rules  TEXT        NOT NULL,
    duration_ms      BIGINT      NOT NULL,
    evaluated_at     TIMESTAMPTZ NOT NULL
);
