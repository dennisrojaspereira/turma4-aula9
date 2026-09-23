#!/usr/bin/env bash
# Relatorio do Parallel Run: quantas comparacoes, quantas divergencias, latencias, ultimas divergencias.
set -euo pipefail
BASE="${TECHPIX_URL:-http://localhost:8080}"
curl -s "$BASE/admin/fraud/parallel-run" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print('== Parallel Run')
print(f\"  comparacoes:        {d['total']}\")
rate = d.get('matchRate')
print(f\"  match rate:         {'n/a' if rate is None else f'{rate:.1%}'}\")
for k, v in d['outcomes'].items():
    print(f'    {k:<18} {v}')
print(f\"  legacy latency ms:  {d['legacyLatencyMeanMs']:.1f}\")
print(f\"  new latency ms:     {d['newLatencyMeanMs']:.1f}\")
print(f\"  score diff (media): {d['scoreDifferenceMean']:.2f}\")
print()
print('== Ultimas divergencias')
for c in d['lastDivergences'][:10]:
    print(f\"  {c['paymentId'][:8]}  {c['outcome']:<18} legacy={c['legacyScore']}/{c['legacyDecision']}  new={c['newScore']}/{c['newDecision']}  {c['detail'][:80]}\")
" 2>/dev/null || curl -s "$BASE/admin/fraud/parallel-run"
