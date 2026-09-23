# A evolução da arquitetura, diagrama a diagrama

Nunca mostre o último diagrama no início. Cada um existe por causa de um problema medido no anterior.

## Lab 01 — O monólito que funciona

```mermaid
flowchart LR
    Client --> Monolith["Tech Pix Monolith<br/>Account · Payment · Ledger · Fraud · Notification"]
    Monolith --> PostgreSQL
```

Nenhum problema. 10 TPS, p95 180 ms.

## Labs 02 a 04 — Cresceu, mediu, otimizou

Mesmo diagrama. O que mudou foi o custo de Fraud (17 regras, 32 consultas por pagamento) e depois a solução dentro do monólito (índices, consultas agregadas, cache, fraude fora da transação). 9 TPS para 193 TPS sem mudar a arquitetura.

## Lab 05 — Modular Monolith

```mermaid
flowchart TB
    subgraph Monolith
        Payment --> Account
        Payment -->|FraudEvaluator| Fraud
        Payment --> Ledger
        Payment --> Notification
    end
    Monolith --> PostgreSQL
```

Fronteiras verificadas por ArchUnit. Fraud não depende de ninguém. Nenhuma métrica mudou.

## Labs 07 e 08 — Strangler Fig e Fraud Service

```mermaid
flowchart LR
    Payment --> Facade[FraudFacade]
    Facade -->|LEGACY| Legacy[Fraud in-process]
    Facade -->|NEW, HTTP| FS[Fraud Service]
    Legacy --> DB[(PostgreSQL)]
    FS -->|ACL| DB
```

Dois processos, um banco. A rede apareceu: `FraudUnavailableException`.

## Labs 09 a 12 — Kubernetes

```mermaid
flowchart TB
    subgraph K8s["Kubernetes (namespace techpix)"]
        M["Deployment monolith x2"] -->|http://fraud-service| S["Service fraud-service"]
        S --> F1[Pod] & F2[Pod] & F3[Pod]
        M --> PG["Service postgres"]
        F1 & F2 & F3 --> PG
    end
```

Descoberta pelo nome, escala independente, readiness durante o warm-up, HPA por CPU.

## Labs 13 a 15 — Feature Flag, Parallel Run, Canary

```mermaid
flowchart LR
    Payment --> Facade
    Facade -->|LEGACY| Legacy
    Facade -->|PARALLEL: legado decide, novo em shadow| Comparator
    Facade -->|CANARY: x% novo| Router
    Facade -->|NEW| FS[Fraud Service]
```

Migração reversível, medida em cada degrau.

## Labs 16 e 17 — GitOps e Argo CD

```mermaid
flowchart LR
    Git[(gitops/overlays/*)] --> Argo[Argo CD]
    Argo --> Dev[techpix-dev]
    Argo --> QA[techpix-qa]
    Argo --> Prod[techpix-prod]
```

Git é o desired state. Drift é detectado e (onde escolhemos) desfeito.

## Lab 18 — Database per Service

```mermaid
flowchart LR
    Payment --> FS[Fraud Service]
    Payment --> PDB[(techpix)]
    FS --> FDB[(fraud_db)]
    FS -.-x|REVOKE| PDB
```

O JOIN desapareceu. A visão local nasce vazia.

## Lab 19 — Kafka

```mermaid
flowchart LR
    Payment -->|HTTP| FS[Fraud Service]
    Payment -->|PaymentApproved / Rejected / Failed| K[(Kafka)]
    K --> FS
    Payment --> PDB[(techpix)]
    FS --> FDB[(fraud_db<br/>payment_history)]
```

Payment conta; Fraud escuta; consistência eventual; idempotência obrigatória.

## Lab 20 — O sistema distribuído

```mermaid
flowchart LR
    Client --> Payment
    Payment -->|HTTP: timeout, retry, backoff, jitter| FS[Fraud Service x N]
    Payment -->|eventos| K[(Kafka)]
    K -->|consumer group| FS
    Payment --> PDB[(techpix)]
    FS --> FDB[(fraud_db)]
    Git[(Git)] --> Argo[Argo CD] --> K8s[Kubernetes]
    K8s --- Payment
    K8s --- FS
```

Cada seta é um lugar onde algo pode falhar de um jeito novo. Cada uma existe por causa de um problema medido.

> Distribuir é consequência, não objetivo.
