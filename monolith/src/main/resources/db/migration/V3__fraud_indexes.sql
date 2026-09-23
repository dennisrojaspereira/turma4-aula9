-- Etapa 4. Os índices que o lab 03 provou estarem faltando.
-- Cada um corresponde a uma consulta do topo do pg_stat_statements.

-- velocity, average-ticket, behavioral-hour, recent-rejections, high-frequency, new-payee
CREATE INDEX idx_payments_payer_created ON payments (payer_account_id, created_at);

-- payee-reputation, payee-velocity
CREATE INDEX idx_payments_payee_created ON payments (payee_account_id, created_at);

-- device-fingerprint
CREATE INDEX idx_payments_device_created ON payments (device_id, created_at);

-- recent-rejections (N+1) e qualquer consulta por pagamento
CREATE INDEX idx_fraud_evaluations_payment ON fraud_evaluations (payment_id);

-- blacklist
CREATE UNIQUE INDEX idx_fraud_blacklist_kind_value ON fraud_blacklist (kind, value);
