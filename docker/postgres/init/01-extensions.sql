-- Executado apenas na primeira inicialização do volume.
-- Se o volume já existia, scripts/slow-queries.sh cria a extensão sob demanda.
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
