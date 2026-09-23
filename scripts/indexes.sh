#!/usr/bin/env bash
# Remove ou recria os indices de Fraud (V3) ao vivo, para mostrar o efeito na carga.
#   scripts/indexes.sh drop
#   scripts/indexes.sh create
set -euo pipefail
cd "$(dirname "$0")/.."
PSQL="docker compose exec -T postgres psql -U techpix -d techpix"
case "${1:-}" in
  drop)
    $PSQL -c "DROP INDEX IF EXISTS idx_payments_payer_created, idx_payments_payee_created, idx_payments_device_created, idx_fraud_evaluations_payment, idx_fraud_blacklist_kind_value;"
    ;;
  create)
    $PSQL -c "CREATE INDEX IF NOT EXISTS idx_payments_payer_created ON payments (payer_account_id, created_at);
              CREATE INDEX IF NOT EXISTS idx_payments_payee_created ON payments (payee_account_id, created_at);
              CREATE INDEX IF NOT EXISTS idx_payments_device_created ON payments (device_id, created_at);
              CREATE INDEX IF NOT EXISTS idx_fraud_evaluations_payment ON fraud_evaluations (payment_id);
              CREATE UNIQUE INDEX IF NOT EXISTS idx_fraud_blacklist_kind_value ON fraud_blacklist (kind, value);
              ANALYZE;"
    ;;
  *) echo "uso: $0 drop|create"; exit 1 ;;
esac
$PSQL -c "SELECT indexname FROM pg_indexes WHERE tablename IN ('payments','fraud_evaluations','fraud_blacklist') ORDER BY 1;"
