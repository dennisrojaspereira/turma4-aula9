-- Etapa 12: Database per Service.
--
-- Mesma INSTANCIA PostgreSQL (o laboratorio roda em um notebook), banco LOGICO separado, usuario separado.
-- A fronteira que importa e de OWNERSHIP e de ACESSO, nao de hardware:
--   * o usuario "fraud" nao consegue nem conectar ao banco "techpix";
--   * o usuario "techpix" nao tem privilegios em "fraud_db".
-- Em producao a fronteira pode ser uma instancia (isolamento de falha e de performance). A decisao e outra.

CREATE USER fraud WITH PASSWORD 'fraud';
CREATE DATABASE fraud_db OWNER fraud;

-- Sem isto, qualquer usuario conecta a qualquer banco (privilegio CONNECT padrao de PUBLIC).
REVOKE CONNECT ON DATABASE techpix FROM PUBLIC;
REVOKE CONNECT ON DATABASE fraud_db FROM PUBLIC;
GRANT CONNECT ON DATABASE techpix TO techpix;
GRANT CONNECT ON DATABASE fraud_db TO fraud;
