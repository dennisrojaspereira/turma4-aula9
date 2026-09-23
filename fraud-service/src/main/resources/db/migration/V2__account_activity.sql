-- Um agregado mantido por INCREMENTO a cada evento. Diferente de payment_history (upsert por chave),
-- um incremento aplicado duas vezes conta duas vezes. E o exemplo perfeito de por que idempotencia importa.
CREATE TABLE account_activity (
    account_id             UUID PRIMARY KEY,
    approved_count         BIGINT         NOT NULL DEFAULT 0,
    approved_amount_total  NUMERIC(18, 2) NOT NULL DEFAULT 0,
    rejected_count         BIGINT         NOT NULL DEFAULT 0,
    updated_at             TIMESTAMPTZ    NOT NULL
);
