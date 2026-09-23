# Mapa dos 26 slides para o código

| # | Slide | Lab | Demonstrar com | Ao vivo? |
|---|---|---|---|---|
| 1 | O monólito que funciona | [01](../labs/01-working-monolith.md) | `scripts/demo-payment.sh`, `PaymentService` de uma transação | leitura |
| 2 | O crescimento muda o problema | [02](../labs/02-growing-load.md) | `scripts/fraud-profile.sh HEAVY` + `scripts/load-test.sh growth` | **sim** |
| 3 | Incidente: banco no limite | [02](../labs/02-growing-load.md) | `docker stats techpix-postgres` durante a carga | **sim** |
| 4 | Sintoma não é causa | [03](../labs/03-investigating-performance.md) | `scripts/show-metrics.sh` | **sim** |
| 5 | Encontramos o hotspot | [03](../labs/03-investigating-performance.md) | `scripts/slow-queries.sh` | **sim** |
| 6 | Otimizar antes de distribuir | [04](../labs/04-optimizing-fraud.md) | `scripts/fraud-profile.sh HEAVY_OPTIMIZED` + carga; tabela antes/depois | **sim** |
| 7 | Modular Monolith | [05](../labs/05-modular-monolith.md) | `ModuleBoundaryTest`, `package-info.java` | leitura |
| 8 | A dor persiste | [06](../labs/06-extraction-decision.md) | `account_read_http_ms` com ML barato vs caro | leitura (números no lab) |
| 9 | Decision Framework | [06](../labs/06-extraction-decision.md) | [decision-framework.md](decision-framework.md) | leitura |
| 10 | Boundary antes do serviço | [06](../labs/06-extraction-decision.md) | [boundaries.md](boundaries.md), `FraudEvaluator` | leitura |
| 11 | Strangler Fig | [07](../labs/07-strangler.md) | `FraudFacade`, `scripts/fraud-mode.sh NEW` sem serviço = 503, `rollback.sh` | **sim** |
| 12 | Self-contained Service + ACL | [08](../labs/08-fraud-service.md) | `fraud-service/`, `LegacySchemaPaymentHistory`, `legacy-schema.sql` | leitura |
| 13 | Onde Fraud roda? | [09](../labs/09-kubernetes.md) | `docker compose --profile app up`, `scripts/k8s-up.sh` | **sim** (cluster criado antes) |
| 14 | Deployment, Pod e Service | [09](../labs/09-kubernetes.md) | `kubectl -n techpix get pods,svc,endpoints` | **sim** |
| 15 | Service Discovery | [10](../labs/10-service-discovery.md) | `scripts/k8s-discovery-demo.sh` | **sim** |
| 16 | Escala independente | [11](../labs/11-scaling.md) | `scripts/k8s-scale.sh 2 5`, `scripts/k8s-scale.sh hpa` | **sim** |
| 17 | Readiness/Liveness/Startup | [12](../labs/12-health-checks.md) | `kubectl get pods -w` após apagar um Pod (0/1 Running) | **sim** |
| 18 | Feature Flag | [13](../labs/13-feature-flags.md) | `FraudMode`, `scripts/fraud-mode.sh` | leitura |
| 19 | Parallel Run | [14](../labs/14-parallel-run.md) | `scripts/enable-parallel-run.sh`, `scripts/parallel-run-report.sh` com SIMPLE vs 13 regras | **sim** |
| 20 | Canary | [15](../labs/15-canary.md) | `scripts/canary-10.sh`, `canary-status.sh`, `scripts/chaos.sh errors 0.5`, `rollback.sh` | **sim** |
| 21 | Configuração dos ambientes | [16](../labs/16-gitops.md) | `diff <(kubectl kustomize gitops/overlays/qa) <(kubectl kustomize gitops/overlays/prod)` | leitura |
| 22 | GitOps + Argo CD | [17](../labs/17-argocd.md) | `scripts/argocd-drift-demo.sh` | **sim** (Argo CD instalado antes) |
| 23 | Shared Database | [18](../labs/18-database-per-service.md) | `scripts/shared-db-breakage-demo.sh` | **sim** (Fraud em modo etapa 8) |
| 24 | Database per Service | [18](../labs/18-database-per-service.md) | `psql -U fraud -d techpix` = permission denied; `FraudStoreIT.theJoinIsGone` | leitura |
| 25 | Kafka | [19](../labs/19-kafka.md) | `scripts/kafka-tail.sh`, `scripts/replay-event.sh` + log `DUPLICADO` | **sim** |
| 26 | Complexidade dos sistemas distribuídos | [20](../labs/20-distributed-systems.md) | `scripts/chaos.sh`, `TimeoutRetryIT`, `TECHPIX_LEDGER_FAIL_AMOUNT=13.37` | leitura + 1 demo |

## Roteiro de 2 horas (sugestão)

Nove demonstrações ao vivo; o resto é leitura guiada com o código aberto.

| Tempo | Bloco | Slides | Demo |
|---|---|---|---|
| 0:00 | O monólito e o crescimento | 1 a 3 | carga SIMPLE vs HEAVY, `docker stats` |
| 0:15 | Investigar e otimizar | 4 a 6 | `show-metrics`, `slow-queries`, HEAVY_OPTIMIZED |
| 0:35 | Modularizar e decidir | 7 a 10 | leitura: ArchUnit, decision framework |
| 0:45 | Extrair | 11, 12 | `fraud-mode NEW` sem serviço; com serviço |
| 0:55 | Rodar | 13 a 17 | `k8s-discovery-demo`, `k8s-scale`, Pod 0/1 |
| 1:15 | Migrar com segurança | 18 a 20 | parallel-run com divergência; canary + chaos + rollback |
| 1:35 | Governar | 21, 22 | `argocd-drift-demo` |
| 1:45 | Dados e eventos | 23 a 25 | `shared-db-breakage-demo`; `replay-event` + DUPLICADO |
| 1:55 | Fechar | 26 | a tabela de trade-offs; "por que essa arquitetura existe?" |

Antes da aula: `docker compose --profile app up -d --build`, `scripts/seed.sh`, `scripts/k8s-up.sh`, `scripts/local-git-server.sh`, `scripts/argocd-up.sh`. As imagens e o Argo CD levam minutos na primeira vez.
