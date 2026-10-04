# Politicas de autorizacao da Tech Pix (Aula 8 - policy as code).
# O OPA roda como servidor de decisao: os servicos (clients) perguntam
# "posso?" mandando um input JSON; a resposta sai destas regras.
#   Decisao: POST /v1/data/techpix/authz  {"input": {...}}
# Edite este arquivo com o OPA no ar: ele recarrega sozinho (--watch).
package techpix.authz

import rego.v1

default allow := false

# ---------------------------------------------------------------------------
# Limites de Pix por papel (em R$). Papel vem das roles do token do Keycloak.
# ---------------------------------------------------------------------------
limits := {
	"techpix-user": 1000,
	"techpix-suporte": 0,       # suporte consulta, nao movimenta dinheiro
	"techpix-admin": 50000,
}

role_limit := max([limits[r] | some r in input.roles; limits[r]]) if {
	some r in input.roles
	limits[r]
}

# Pagamento permitido: dentro do limite do melhor papel do usuario...
allow if {
	input.action == "payment.create"
	input.amount <= role_limit
	not high_value
}

# ...e acima de R$ 10.000 so com aprovacao de compliance registrada.
allow if {
	input.action == "payment.create"
	input.amount <= role_limit
	high_value
	"techpix-compliance" in input.approvals
}

high_value if input.amount > 10000

# ---------------------------------------------------------------------------
# Servico chamando servico: so quem carrega a role de SERVICO certa.
# (As mesmas roles que o Keycloak poe no token client_credentials.)
# ---------------------------------------------------------------------------
allow if {
	input.action == "fraud.evaluate"
	"fraud-evaluate" in input.roles
}

allow if {
	input.action == "payments.read"
	some r in {"payments-read", "techpix-suporte", "techpix-admin"}
	r in input.roles
}

# ---------------------------------------------------------------------------
# Motivos (para o cliente explicar o 403 - negar sem explicar e cruel)
# ---------------------------------------------------------------------------
reasons contains msg if {
	input.action == "payment.create"
	not role_limit
	msg := "nenhum papel com limite de pagamento no token"
}

reasons contains msg if {
	input.action == "payment.create"
	role_limit
	input.amount > role_limit
	msg := sprintf("valor %v acima do limite %v do papel", [input.amount, role_limit])
}

reasons contains msg if {
	input.action == "payment.create"
	high_value
	not "techpix-compliance" in input.approvals
	msg := "Pix acima de R$ 10.000 exige aprovacao de compliance"
}

reasons contains msg if {
	input.action == "fraud.evaluate"
	not "fraud-evaluate" in input.roles
	msg := "token sem a role de servico fraud-evaluate"
}

decision := {"allow": allow, "reasons": reasons}
