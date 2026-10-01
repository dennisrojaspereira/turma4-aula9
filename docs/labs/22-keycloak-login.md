# Lab 22 — Keycloak: identidade e login

**Slide suportado:** Aula 8 — Segurança Aplicada e Governança (identidade centralizada / OIDC)

## Problema

Qualquer pessoa com acesso à rede chama `POST /payments` e move dinheiro. Qualquer uma chama `POST /admin/seed` e enche o banco. Não há identidade: o log diz **o que** aconteceu, nunca **quem** fez. Não há como dizer "só o time de operação mexe em `/admin/fraud/mode`".

Autenticação não é um `if (password.equals(...))` dentro do monólito. É um componente próprio — um Identity Provider (IdP) — que emite tokens assinados que qualquer serviço valida sem perguntar de volta. A Tech Pix usa o Keycloak, um IdP open source self-hosted.

## O que observar

O fluxo **authorization code** (OIDC), o mesmo do "Entrar com Google":

```text
Navegador                 Monolito (:8080)                Keycloak (:8180)
    |                          |                               |
    |--- GET /payments ------->|  sem sessao                   |
    |<-- redirect /login ------|                               |
    |--- clica "Entrar" ------>|                               |
    |<-- redirect ------------------------------------------>  | tela de login do realm techpix
    |--- maria / techpix123 --------------------------------->|
    |<-- redirect /login/oauth2/code/keycloak?code=XYZ --------|  (code: efemero, de um uso)
    |--- GET ...?code=XYZ ---->|                               |
    |                          |--- troca code por tokens ---->|  (com o client secret)
    |                          |<-- ID token + access token ---|  (JWT assinados)
    |<-- sessao criada --------|                               |
    |--- GET /me ------------->|  "maria", roles: techpix-admin, techpix-user
```

A senha só passa pelo Keycloak. O monólito nunca a vê — recebe tokens assinados e uma sessão.

Para APIs (scripts, outros serviços), sem navegador: o chamador manda `Authorization: Bearer <JWT>` e o monólito valida a assinatura contra as chaves públicas do Keycloak (JWKS). Stateless, sem sessão.

## Mudança

| O quê | Onde | Por quê |
|---|---|---|
| Keycloak em container (profile `auth`) | [docker-compose.yml](../../docker-compose.yml) | IdP é componente próprio, não biblioteca |
| Realm `techpix` pré-provisionado: client `techpix-monolith`, roles `techpix-admin`/`techpix-user`, usuários `maria` e `joao` | [docker/keycloak/techpix-realm.json](../../docker/keycloak/techpix-realm.json) | import automático: a aula não começa clicando em console de admin |
| Spring profile `secure` liga a segurança | [application-secure.yml](../../monolith/src/main/resources/application-secure.yml) | **com a configuração default, nada muda** — labs 01–21 continuam funcionando sem Keycloak |
| `SecurityConfig` (`@Profile("secure")`): oauth2Login + resource server JWT | [shared/security/](../../monolith/src/main/java/com/techpix/shared/security/) | navegador usa sessão OIDC; scripts usam Bearer token — o mesmo filter chain |
| `PermitAllSecurityConfig` (`@Profile("!secure")`) | idem | comportamento idêntico ao de antes da dependência de security existir |
| Tela de login + home | [static/login.html](../../monolith/src/main/resources/static/login.html), [index.html](../../monolith/src/main/resources/static/index.html) | ver o fluxo de verdade, no navegador |
| `GET /me` | [MeController](../../monolith/src/main/java/com/techpix/shared/security/MeController.java) | quem sou eu: nome, e-mail, roles — para sessão, Bearer ou anônimo |

Decisões que valem ler no código:

- **Issuer mismatch** (comentado em `application-secure.yml`): o navegador alcança o Keycloak em `localhost:8180`; o monólito em container alcança em `keycloak:8080`. Um `issuer-uri` único não serve. Usamos **endpoints explícitos**: `authorization-uri` com a URL pública (navegador) e `token-uri`/`jwk-set-uri` com a URL que o monólito alcança. O Bearer token é validado pela **assinatura** (JWKS), não pelo campo `iss` — trade-off aceitável em laboratório, documentado no YAML.
- **Roles**: o realm role `techpix-admin` vira a authority `ROLE_techpix-admin` (minúsculas, igual ao Keycloak). `/admin/**` exige essa authority; o resto só exige estar autenticado. O client tem um protocol mapper que coloca as realm roles na claim `roles` do ID token e do access token.
- **CSRF**: ligado para o fluxo web; `/payments`, `/accounts` e `/admin` ficam fora (`ignoringRequestMatchers`) para chamadas Bearer de scripts funcionarem. Logout é `GET /logout` para a home usar um link simples — trade-off comentado no `SecurityConfig`.

## Como executar

```bash
scripts/keycloak-up.sh          # sobe o Keycloak em :8180 e espera o realm techpix ficar pronto
```

Ligue a segurança (o caso principal da aula: monólito pelo Maven, na máquina):

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=secure ./mvnw -pl monolith spring-boot:run
```

Abra http://localhost:8080/login e entre com `maria` / `techpix123`. A home mostra "Olá, maria", as roles e o botão Sair. Compare com `joao` / `techpix123` (sem `techpix-admin`): `POST /admin/seed` responde **403**.

API com Bearer token (password grant, só para demonstração):

```bash
TOKEN=$(curl -s -X POST http://localhost:8180/realms/techpix/protocol/openid-connect/token \
  -d grant_type=password -d client_id=techpix-monolith -d client_secret=techpix-monolith-secret \
  -d username=maria -d password=techpix123 | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/me
curl -s http://localhost:8080/me        # sem token: {"authenticated":false}
curl -s -o /dev/null -w "%{http_code}\n" -X POST "http://localhost:8080/admin/seed?accounts=10&payments=10" \
  -H "Authorization: Bearer $TOKEN"     # 200 com maria; sem token, 401
```

Admin console do Keycloak: http://localhost:8180 (admin/admin) — veja o realm, o client e as sessões ativas.

## Como testar

```bash
./mvnw test     # continua verde, SEM Keycloak rodando: o profile default é permitAll
```

`SecurityOffIT` garante o requisito inegociável: sem o profile `secure`, `/me` responde `authenticated:false` e `POST /payments` funciona sem autenticação. `KeycloakRoleConverterTest` cobre o mapeamento `realm_access.roles` → authorities sem contexto Spring.

Com `secure` ligado:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/payments/00000000-0000-0000-0000-000000000000
# 401 sem token (curl) ; no navegador, redirect para /login
```

## Trade-offs

```text
Keycloak (IdP centralizado)
+ identidade com dono unico: usuarios, senhas e roles fora do codigo da aplicacao
+ SSO: a mesma sessao do realm serve para o proximo servico que aderir
+ tokens assinados: o fraud-service pode validar o mesmo JWT sem chamar ninguem
+ auditoria: o log do Keycloak sabe quem logou, quando, de onde

- mais um componente stateful para operar, versionar e monitorar
- issuer/URL: a rede do navegador e a rede do container veem o Keycloak por enderecos
  diferentes; resolvido com endpoints explicitos, ao custo da validacao de iss
- sessao no monolito (web) vs stateless (Bearer): dois modelos convivendo
- o realm.json versionado e otimo para aula e pessimo para producao (secrets em claro)
```

## Pergunta para discussão

> O secret do client (`techpix-monolith-secret`) está no `application-secure.yml` e no `techpix-realm.json`, dentro do Git. O que muda em produção?

(Secret sai do Git: Vault / External Secrets Operator injetando em runtime, `TECHPIX_KEYCLOAK_CLIENT_SECRET` por ambiente, rotação periódica — e o realm.json de produção não carrega senhas de usuários nem secrets, só a estrutura.)

## Professor Notes

```text
Pergunta:
Por que o monolito nunca ve a senha da maria?

Resposta:
O navegador digita a senha NO Keycloak. O monolito recebe um code de um uso e o troca
por tokens assinados, autenticando-se com o client secret. Roubo de senha no app vira impossivel
por construcao, nao por disciplina.

Erro comum:
Validar token chamando o Keycloak a cada request (introspection) quando um JWT assinado
resolve localmente. Ou o oposto: aceitar JWT sem validar assinatura.

Conceito:
Authorization code flow: autenticacao e um redirect, nao um POST de senha para a aplicacao.
Token = fato assinado e portavel; sessao = conveniencia local do canal web.

Transicao:
Identidade resolvida para pessoas. E para o pipeline? O build que empacota essa aplicacao
tambem precisa de identidade, assinatura e verificacao — e disso que trata o lab 21.
```

## Próximo problema

A aplicação agora sabe **quem** chama. Mas quem garante o **artefato** que está rodando? A pipeline segura do [Lab 21](21-secure-pipeline.md) ganha, com este lab, uma aplicação com autenticação de verdade para construir, escanear e assinar.
