# Tech Pix — Do Monólito ao Sistema Distribuído

Tech Pix começou como um monólito.

E isso não era um problema.

O sistema atendia 10 TPS com baixa latência. Cinco módulos, um processo, um banco.

Depois a empresa cresceu. Fraud ficou mais sofisticado. A latência aumentou. O banco começou a saturar.

A partir daqui investigamos o problema antes de mudar a arquitetura.

> **Distribuir é consequência, não objetivo.**

## Como executar

Pré-requisitos: Java 21, Docker. Só isso para as etapas de código.

```bash
docker compose up -d postgres
./mvnw test
./mvnw -pl monolith spring-boot:run
scripts/demo-payment.sh
```

No Windows, cada script em `scripts/` tem um equivalente `.ps1`.

## Labs

Os laboratórios em [docs/labs/](docs/labs/) seguem a evolução do sistema. Cada um informa o slide que suporta e a tag git que congela aquele momento.

| Lab | Tag |
|---|---|
| [01 — O monólito que funciona](docs/labs/01-working-monolith.md) | `aula07-step-01-monolith` |
| [02 — O crescimento muda o problema](docs/labs/02-growing-load.md) | `aula07-step-02-growth` |
| [03 — Sintoma não é causa](docs/labs/03-investigating-performance.md) | `aula07-step-03-observability` |
| [04 — Otimizar antes de distribuir](docs/labs/04-optimizing-fraud.md) | `aula07-step-04-optimized` |
| [05 — Modular Monolith](docs/labs/05-modular-monolith.md) | `aula07-step-05-modular` |
| [06 — A dor persiste: decidir extrair](docs/labs/06-extraction-decision.md) | `aula07-step-06-strangler` |
| [07 — Strangler Fig](docs/labs/07-strangler.md) | `aula07-step-06-strangler` |
| [08 — Self-contained Fraud Service + ACL](docs/labs/08-fraud-service.md) | `aula07-step-07-fraud-service` |
| [09 — Onde Fraud roda? Containers e Kubernetes](docs/labs/09-kubernetes.md) | `aula07-step-08-kubernetes` |
| [10 — Service Discovery](docs/labs/10-service-discovery.md) | `aula07-step-08-kubernetes` |
| [11 — Escala independente](docs/labs/11-scaling.md) | `aula07-step-08-kubernetes` |
| [12 — Readiness, Liveness e Startup](docs/labs/12-health-checks.md) | `aula07-step-08-kubernetes` |
| [13 — Branch by Abstraction + Feature Flag](docs/labs/13-feature-flags.md) | `aula07-step-09-feature-flag-parallel-run` |
| [14 — Parallel Run](docs/labs/14-parallel-run.md) | `aula07-step-09-feature-flag-parallel-run` |
| [15 — Canary](docs/labs/15-canary.md) | `aula07-step-10-canary` |

## Decisões

Todas as decisões arquiteturais estão em [docs/adr/](docs/adr/), com contexto, alternativas e, principalmente, consequências negativas.
