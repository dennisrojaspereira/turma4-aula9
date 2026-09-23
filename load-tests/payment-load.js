// Gerador de carga da Tech Pix.
//
//   k6 run -e VUS=5  -e DURATION=30s load-tests/payment-load.js   # "10 TPS": a Tech Pix do inicio
//   k6 run -e VUS=40 -e DURATION=60s load-tests/payment-load.js   # crescimento
//
// Antes de rodar, popule o historico: scripts/seed.sh
//
// Alem de POST /payments, uma fracao das iteracoes faz GET /accounts/{id}: uma rota leve, sem Fraud.
// Se a latencia dela subir junto com a de Fraud, e porque os dois disputam o mesmo processo (CPU, threads, pool).
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 5);
const DURATION = __ENV.DURATION || '30s';
const THINK = Number(__ENV.THINK_MS || 100);
const READ_SHARE = Number(__ENV.READ_SHARE || 0.2);

const fraudMs = new Trend('fraud_duration_ms', true);
const paymentMs = new Trend('payment_http_ms', true);
const accountReadMs = new Trend('account_read_http_ms', true);
const rulesEvaluated = new Trend('fraud_rules_evaluated');
const rejected = new Counter('payments_rejected');

export const options = {
  vus: VUS,
  duration: DURATION,
  thresholds: {
    http_req_failed: ['rate<0.05'],
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const res = http.get(`${BASE}/admin/seed/accounts?limit=2000`);
  const accounts = res.json();
  if (!accounts || accounts.length < 2) {
    throw new Error('Nenhuma conta seed encontrada. Rode scripts/seed.sh primeiro.');
  }
  const profile = http.get(`${BASE}/admin/fraud/profile`).json();
  console.log(`accounts=${accounts.length} fraudProfile=${profile.profile} rules=${profile.ruleCount}`);
  return { accounts };
}

function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

export default function (data) {
  const payer = pick(data.accounts);

  if (Math.random() < READ_SHARE) {
    const res = http.get(`${BASE}/accounts/${payer}`, { tags: { name: 'GET /accounts/{id}' } });
    check(res, { 'account 200': (r) => r.status === 200 });
    accountReadMs.add(res.timings.duration);
    sleep(THINK / 1000);
    return;
  }

  let payee = pick(data.accounts);
  if (payee === payer) {
    payee = data.accounts[(data.accounts.indexOf(payer) + 1) % data.accounts.length];
  }
  // Valores pequenos na maioria, alguns altos: parecido com uma carteira digital real.
  const amount = Math.random() < 0.95 ? (1 + Math.random() * 300).toFixed(2) : (1000 + Math.random() * 9000).toFixed(2);
  const deviceId = `seed-device-${Math.floor(Math.random() * 1000)}`;

  const res = http.post(`${BASE}/payments`, JSON.stringify({
    payerAccountId: payer,
    payeeAccountId: payee,
    amount: Number(amount),
    deviceId,
  }), { headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /payments' } });

  const ok = check(res, { 'payment 201': (r) => r.status === 201 });
  paymentMs.add(res.timings.duration);
  if (ok) {
    const body = res.json();
    fraudMs.add(body.fraudDurationMs);
    rulesEvaluated.add(body.fraudRulesEvaluated);
    if (body.status === 'REJECTED') {
      rejected.add(1);
    }
  }
  sleep(THINK / 1000);
}
