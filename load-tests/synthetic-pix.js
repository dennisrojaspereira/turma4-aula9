// Synthetic test da Tech Pix (Aula 9): NAO e teste de carga.
// Um robo faz a jornada COMPLETA de um Pix, como um cliente faria, e valida o
// resultado de negocio - antes de qualquer cliente reclamar.
//
//   k6 run -e BASE_URL=http://localhost:8090 load-tests/synthetic-pix.js
//
// Rodando de tempos em tempos: scripts/synthetic-monitor.py start
// (resultado vira metrica no Prometheus e painel no Grafana)
import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8090';
const journeyMs = new Trend('synthetic_journey_ms', true);

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    // o synthetic FALHA se qualquer verificacao de negocio falhar...
    checks: ['rate==1.0'],
    // ...ou se a jornada estourar o SLO de 5s (o mesmo SLI do lab 23)
    synthetic_journey_ms: ['max<5000'],
  },
};

export default function () {
  const t0 = Date.now();
  const params = { headers: { 'Content-Type': 'application/json' } };

  // 1. duas contas novas (o robo nao depende de dados pre-existentes)
  const payer = http.post(`${BASE}/accounts`,
    JSON.stringify({ ownerName: 'Synthetic Probe', initialBalance: 10 }), params);
  const payee = http.post(`${BASE}/accounts`,
    JSON.stringify({ ownerName: 'Synthetic Sink', initialBalance: 0 }), params);
  check(payer, { 'conta pagadora criada': (r) => r.status === 201 || r.status === 200 });
  check(payee, { 'conta recebedora criada': (r) => r.status === 201 || r.status === 200 });

  // 2. o Pix em si
  const pay = http.post(`${BASE}/payments`, JSON.stringify({
    payerAccountId: payer.json('id'),
    payeeAccountId: payee.json('id'),
    amount: 1.0,
    deviceId: 'synthetic-probe',
  }), params);
  check(pay, {
    'pagamento aceito (201)': (r) => r.status === 201,
    'status APPROVED (nao basta responder: tem que aprovar)': (r) => r.json('status') === 'APPROVED',
  });

  // 3. o estado consultavel bate com o que aconteceu
  const id = pay.json('id');
  const get = http.get(`${BASE}/payments/${id}`);
  check(get, { 'GET /payments/{id} confirma APPROVED': (r) => r.status === 200 && r.json('status') === 'APPROVED' });

  // 4. o dinheiro CHEGOU (saude do negocio, nao do processo)
  const saldo = http.get(`${BASE}/accounts/${payee.json('id')}`);
  check(saldo, { 'recebedor foi creditado': (r) => r.status === 200 && Number(r.json('balance')) === 1.0 });

  journeyMs.add(Date.now() - t0);
}
