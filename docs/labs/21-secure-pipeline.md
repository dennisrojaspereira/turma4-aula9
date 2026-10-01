# Lab 21 — Pipeline de segurança: SAST + DAST

**Aula:** 8 — Segurança Aplicada e Governança
**Tag:** *(sem tag git ainda)*

## Problema

A Tech Pix tem GitOps (lab 17): o que está no Git vai para o cluster. Mas **nada verifica** o que está no Git. Uma injeção de SQL, um segredo commitado, um header de segurança faltando — tudo isso chega a produção com um `git push`, reconciliado em minutos pelo Argo CD, sem que ninguém olhe.

Deploy automatizado sem verificação automatizada é só uma forma mais rápida de entregar vulnerabilidades.

A resposta: uma pipeline que roda **antes** do deploy e que **falha** quando encontra problema. Pipeline = gate, não relatório.

## O que observar

```text
       Git (Gitea local)
            |
          clone                    Task git-clone
            |
          SAST                     Task maven-sonar
     (analisa o CODIGO)            build + SonarQube scanner
            |
      quality gate  --- reprovou? --> pipeline FALHA
            |
          DAST                     Task zap-baseline
  (ataca a APLICACAO rodando)      OWASP ZAP baseline scan
            |
       FAIL do ZAP? --------------> pipeline FALHA
            |
        pipeline OK
```

```bash
kubectl -n techpix-ci get pipelineruns                # historico de execucoes
kubectl -n techpix-ci get pipelinerun <nome> -o wide  # Succeeded True/False
```

## Mudança

Uma pipeline Tekton em [tekton/](../../tekton/), instalada por [scripts/tekton-up.sh](../../scripts/tekton-up.sh):

| Arquivo | O que é |
|---|---|
| `tekton/namespace.yaml` | Namespace `techpix-ci` + PVC `maven-cache` (cache de dependências entre execuções) |
| `tekton/tasks/git-clone.yaml` | Task `git-clone`: clone raso do repositório no workspace |
| `tekton/tasks/maven-sonar.yaml` | Task `maven-sonar`: build Maven + scanner SonarQube, com `qualitygate.wait=true` |
| `tekton/tasks/zap-baseline.yaml` | Task `zap-baseline`: OWASP ZAP baseline scan contra a URL alvo |
| `tekton/pipeline.yaml` | Pipeline `security-pipeline`: fetch → sast → dast |

**SAST** (Static Application Security Testing) analisa o **código-fonte** sem executá-lo: o SonarQube procura padrões vulneráveis (SQL concatenado, criptografia fraca, segredos), bugs e code smells. Encontra problemas antes mesmo de a aplicação existir como processo.

**DAST** (Dynamic Application Security Testing) ataca a **aplicação rodando**, de fora, como um atacante faria: o ZAP navega pelos endpoints e verifica respostas HTTP — headers de segurança faltando, cookies sem flags, mensagens de erro vazando detalhes. Encontra problemas que só existem em tempo de execução.

O SonarQube roda como container Docker no host (mesmo padrão do Gitea do lab 17); o scanner, dentro do cluster, o alcança via `host.docker.internal`.

## Como executar

Pré-requisitos: cluster kind rodando com a aplicação (`scripts/k8s-up.sh`) e o código publicado no Gitea local (`scripts/local-git-server.sh`).

```bash
scripts/tekton-up.sh        # instala Tekton, sobe SonarQube, cria Secret, tasks e pipeline
scripts/pipeline-run.sh     # dispara uma execucao e acompanha
```

A primeira execução é lenta: o Maven baixa todas as dependências para o PVC `maven-cache`. Da segunda em diante, o cache corta vários minutos.

Saída típica:

```text
== push do codigo atual para o Gitea local
== alvo do DAST: http://monolith.techpix.svc.cluster.local:8080
== criando PipelineRun
== security-pipeline-x7k2f criado
   status= reason=Running
   ...
   status=True reason=Succeeded

Resultados:
  SAST: http://localhost:9000/dashboard?id=tech-pix
  DAST: relatorio ZAP nos logs da task dast (acima)
```

No relatório do ZAP, espere WARNs reais: `X-Content-Type-Options Header Missing`, `Content Security Policy (CSP) Header Not Set` — a aplicação nunca configurou headers de segurança, e é o DAST que torna isso visível.

## Como testar

O gate funciona quando **falha**:

- **SAST**: `-Dsonar.qualitygate.wait=true` faz o scanner esperar o veredicto do quality gate do SonarQube e retornar erro se ele reprovar — a task `sast` falha e a `dast` nem roda. Teste apertando o quality gate na UI (ex.: cobertura mínima em código novo, que é 0 porque os testes são pulados) e rodando de novo.
- **DAST**: o ZAP roda com `-I`, então WARNs **não** derrubam a pipeline (são material de discussão); apenas um FAIL derruba. O relatório com a lista de WARNs sai nos logs da task `dast`.

```bash
kubectl -n techpix-ci get pipelinerun    # Succeeded: False quando o gate reprova
```

## Trade-offs

```text
SAST + DAST na pipeline
+ shift-left: a vulnerabilidade aparece no push, nao no pentest anual
+ gate automatizado: ninguem precisa lembrar de rodar o scan; reprovou, nao passa
+ o relatorio do ZAP mostra headers de seguranca faltando que nenhum teste unitario pega

- SonarQube e pesado (Elasticsearch embutido): ~2 GB de RAM so para o lab
- o baseline scan do ZAP e raso: nao autentica, nao preenche formularios;
  um full scan autenticado acharia mais, mas levaria horas
- a pipeline compila com -DskipTests: Testcontainers exige Docker, que nao existe
  no Pod da task (Docker-in-Docker e outro problema). Os testes continuam rodando
  fora da pipeline
- latencia: cada push agora espera build + scan + quality gate + ZAP antes do OK
```

## Pergunta para discussão

> Sexta-feira, 18h. Um hotfix de produção está pronto e o quality gate reprovou por um code smell antigo que o commit nem tocou. Quem pode furar o gate? Como essa decisão fica **auditada** — e o que impede que "furar o gate" vire o caminho normal?

(Se a resposta é "o admin do SonarQube desliga o gate", não há auditoria nenhuma. Um bom desenho tem um caminho de exceção explícito — aprovação registrada, prazo para corrigir — decidido antes do incidente, como no lab 17.)

## Professor Notes

```text
Pergunta:
Por que o SAST roda antes do DAST, e nao em paralelo?

Resposta:
Custo crescente: o SAST so precisa do codigo; o DAST precisa da aplicacao no ar.
Se o codigo ja reprovou no gate, atacar a aplicacao e desperdicio. Fail fast.

Erro comum:
Tratar a pipeline como relatorio: o scan roda, gera um dashboard que ninguem abre,
e o deploy acontece do mesmo jeito. Sem o qualitygate.wait=true (ou equivalente),
SAST e teatro de seguranca.

Conceito:
Shift-left: mover a verificacao de seguranca para o mais cedo possivel no ciclo.
SAST ve o codigo (caixa branca); DAST ve o comportamento (caixa preta). Um nao
substitui o outro: o Sonar nao ve header HTTP faltando, o ZAP nao ve SQL concatenado.

Transicao:
A pipeline verifica o codigo e a superficie HTTP. Mas o ZAP entrou em /admin/fraud/mode
sem pedir senha, porque a aplicacao NAO TEM autenticacao. Nenhum scanner conserta isso.
```

## Próximo problema

A pipeline encontra vulnerabilidades, mas a maior delas não é um header: qualquer pessoa chama qualquer endpoint da Tech Pix, inclusive os administrativos. Precisamos de identidade — quem é você, o que você pode fazer. [Lab 22](22-keycloak-login.md).
