# ADR 002 — Modularizar o monólito, com Fraud como Business Capability

**Status:** aceito
**Data:** 2026-09-23
**Etapa:** 5

## Context

Depois de otimizar (ADR 001 continua válido: o monólito atende), o código de Fraud tem 17 regras, três repositórios e um cache, espalhados por pacotes que qualquer classe pode importar. `PaymentService` dependia diretamente de `FraudService`, a implementação. Nada impedia uma regra de fraude de chamar `PaymentRepository`.

Ainda não decidimos extrair Fraud. Mas já sabemos que Fraud tem perfil operacional diferente de Payment (lab 04: seu custo dominante virou rede e CPU, não banco). Se um dia extrairmos, precisamos saber exatamente o que sai.

## Decision

Cada módulo (`account`, `payment`, `ledger`, `fraud`, `notification`) tem:

- um pacote raiz com sua **API pública**: serviços e tipos que outros módulos podem usar;
- um pacote `internal` que **nenhum outro módulo pode importar**.

Fraud expõe uma única interface, `FraudEvaluator`, mais os tipos `FraudCheck` e `FraudResult`. Payment depende da interface, nunca da implementação.

Fraud não depende de nenhum outro módulo. Payment é o único módulo que conhece os demais.

As regras são verificadas por `ModuleBoundaryTest` (ArchUnit). Violar uma fronteira quebra o build.

Fraud é definido como **Business Capability**: "avaliar risco de um pagamento". A fronteira é do domínio, não da camada técnica. Não existe `FraudRepositoryService` nem `FraudControllerService`.

## Alternatives

- **Módulos Maven separados.** Considerado. Dá a mesma proteção em tempo de compilação, mas exige mais cerimônia (poms, versões). ArchUnit dá a proteção com um teste. Se o time crescer, migrar para módulos Maven é direto porque os pacotes já estão separados.
- **Spring Modulith.** Considerado. Faz exatamente isso, com verificação e documentação automáticas. Rejeitado para o laboratório por adicionar um framework quando um teste de 80 linhas basta. Em um projeto real, é uma boa escolha.
- **Não modularizar, extrair direto.** Rejeitado. Extrair um módulo cujas fronteiras não existem significa descobrir as dependências durante a extração, que é o pior momento.

## Consequences

Positivas:

- Sabemos exatamente o que é Fraud: tudo em `com.techpix.fraud`. Nada mais.
- Payment pode receber outra implementação de `FraudEvaluator` sem mudar. Isso é o que torna o Strangler Fig possível.
- Um desenvolvedor novo lê `package-info.java` e sabe o que pode e o que não pode importar.

Negativas:

- Fraud continua lendo a tabela `payments` diretamente via SQL. A fronteira de **código** existe; a fronteira de **dados** não. ArchUnit não vê SQL. Isso é uma dívida conhecida e será tratada no ADR 006.
- O endpoint administrativo de perfil ficou dentro de `fraud.internal`, o que é correto, mas significa que `shared.admin` não pode mais mexer em Fraud. Cada módulo administra a si mesmo.
- Modularizar não mudou nenhuma métrica de performance. Isso é esperado. Modularidade é decisão de design; distribuição é decisão operacional.
