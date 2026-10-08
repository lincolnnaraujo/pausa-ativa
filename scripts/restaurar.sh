#!/bin/sh
# Restauração de um backup do Pausa Ativa (spec H5, seção 3.4). Roda no serviço "restauracao" do compose:
#
#   docker compose stop backend
#   docker compose run --rm restauracao pausa-ativa-AAAA-MM-DD-HHMMSS.dump
#   docker compose up -d --wait
#
# Substitui o banco inteiro pelo do backup, numa transação só: ou volta tudo, ou nada muda. Antes, exige o
# backend parado, pede a confirmação (que --sim pula) e guarda um backup do banco atual, se ele tiver dados.
set -eu

PASTA=/backups
PASTA_DE_BACKUPS=${PASTA_DE_BACKUPS:-./backups}

falhar() {
  echo "$1" >&2
  exit 1
}

consultar() {
  psql --no-psqlrc --tuples-only --no-align --command="$1"
}

confirmado=nao
arquivo=""
for argumento in "$@"; do
  case "$argumento" in
    --sim) confirmado=sim ;;
    *) arquivo=$argumento ;;
  esac
done

if [ -z "$arquivo" ]; then
  echo "Diga qual backup restaurar: docker compose run --rm restauracao <arquivo>" >&2
  echo "Backups em $PASTA_DE_BACKUPS:" >&2
  achou=nao
  for backup in "$PASTA"/*.dump; do
    if [ -f "$backup" ]; then
      echo "  $(basename "$backup")" >&2
      achou=sim
    fi
  done
  if [ "$achou" = nao ]; then
    echo "  (nenhum)" >&2
  fi
  exit 1
fi
nome=$(basename "$arquivo")
if [ ! -f "$PASTA/$nome" ]; then
  falhar "Backup não encontrado: $nome. Os backups ficam em $PASTA_DE_BACKUPS."
fi

if ! pg_isready --quiet --timeout=5; then
  falhar "O banco não respondeu. Suba só o banco com docker compose up -d --wait postgres e tente de novo."
fi

outras=$(consultar "select count(*) from pg_stat_activity
  where datname = current_database() and pid <> pg_backend_pid()")
if [ "$outras" != "0" ]; then
  falhar "O backend está conectado ao banco. Pare-o com docker compose stop backend e tente de novo."
fi

if [ "$confirmado" != sim ]; then
  echo "Isto substitui todos os dados atuais pelos do backup $nome."
  printf "Digite RESTAURAR para continuar: "
  resposta=""
  read -r resposta || true
  # Só as letras contam: o pipe do PowerShell 5.1 manda um BOM antes da palavra e \r\n no fim.
  resposta=$(printf '%s' "$resposta" | tr -cd 'A-Za-z')
  if [ "$resposta" != RESTAURAR ]; then
    falhar "Restauração cancelada: nada mudou."
  fi
fi

# O backup vira SQL antes de tocar no banco: um arquivo que não pode ser lido inteiro para aqui.
sql=/tmp/restauracao.sql
if ! pg_restore --no-owner --file="$sql" "$PASTA/$nome"; then
  falhar "O backup $nome não pôde ser lido: nada mudou."
fi

tabelas=$(consultar "select count(*) from information_schema.tables where table_schema = 'public'")
if [ "$tabelas" != "0" ]; then
  seguranca="antes-da-restauracao-$(date +%Y-%m-%d-%H%M%S).dump"
  if ! pg_dump --format=custom --file="$PASTA/$seguranca.parcial"; then
    rm -f "$PASTA/$seguranca.parcial"
    falhar "Não deu para guardar o banco atual antes de restaurar: nada mudou."
  fi
  mv "$PASTA/$seguranca.parcial" "$PASTA/$seguranca"
  chmod 644 "$PASTA/$seguranca"
  echo "O banco atual foi guardado em $PASTA_DE_BACKUPS/$seguranca."
fi

# O esquema é apagado e recriado a partir do backup na mesma transação: nada do banco atual sobra, nem uma
# tabela de uma versão mais nova, que impediria o Flyway de migrar o backup.
if ! psql --no-psqlrc --quiet --single-transaction --set=ON_ERROR_STOP=1 --output=/dev/null \
  --command="set client_min_messages = warning; drop schema public cascade; create schema public;" \
  --file="$sql"; then
  falhar "A restauração falhou e foi desfeita: o banco continua como estava."
fi

jornadas=$(consultar "select count(*) from jornada")
ultimo=$(consultar "select coalesce(to_char(max(data_referencia), 'DD/MM/YYYY'), 'nenhum') from jornada")
echo "Backup $nome restaurado: $jornadas jornadas, a última em $ultimo."
echo "Suba a aplicação com docker compose up -d --wait."
