#!/bin/sh
# Backup do banco do Pausa Ativa (spec H5, seção 3.3). Roda no serviço "backup" do compose, com a mesma
# imagem do Postgres, para o pg_dump bater com a versão do servidor:
#
#   docker compose run --rm backup
#
# Grava pausa-ativa-AAAA-MM-DD-HHMMSS.dump na pasta de backups (BACKUP_DIR no .env; padrão ./backups).
# Primeiro grava num ".parcial" e só renomeia no fim: um backup que falha não deixa arquivo pela metade.
set -eu

PASTA=/backups
PASTA_DE_BACKUPS=${PASTA_DE_BACKUPS:-./backups}

falhar() {
  echo "$1" >&2
  exit 1
}

if ! pg_isready --quiet --timeout=5; then
  falhar "O banco não respondeu. Suba a aplicação com docker compose up -d --wait e tente de novo."
fi

nome="pausa-ativa-$(date +%Y-%m-%d-%H%M%S).dump"
parcial="$PASTA/$nome.parcial"
trap 'rm -f "$parcial"' EXIT

if ! pg_dump --format=custom --file="$parcial"; then
  falhar "O pg_dump falhou, e o backup não foi gravado."
fi
mv "$parcial" "$PASTA/$nome"
chmod 644 "$PASTA/$nome"

jornadas=$(psql --no-psqlrc --tuples-only --no-align --command="select count(*) from jornada")
tamanho=$(du -h "$PASTA/$nome" | cut -f1)
echo "Backup gravado em $PASTA_DE_BACKUPS/$nome ($tamanho, $jornadas jornadas)."
