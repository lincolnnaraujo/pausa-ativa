#!/usr/bin/env bash
# Cenários 2 e 3 da H5 (spec H5, seção 8), na demonstração, que tem 45 dias de histórico de exemplo:
#   2. backup, volume novo, restauração: o banco e o Histórico voltam iguais;
#   3. backup com o banco parado: termina com erro, a mensagem certa e nenhum arquivo novo.
# Confere também as proteções da restauração: recusa com o backend no ar e cancela sem a confirmação.
#
# Roda da raiz do repositório, com a demonstração no ar:
#   bash .github/scripts/verificar-backup.sh
set -euo pipefail

compose=(docker compose -f docker-compose.yml -f docker-compose.demo.yml)
base="http://127.0.0.1:${FRONTEND_PORT_DEMO:-38743}"
pasta="${BACKUP_DIR:-./backups}"
temporario=$(mktemp -d)

falhar() {
  echo "::error::$1"
  exit 1
}

sql() {
  "${compose[@]}" run --rm -T --entrypoint psql backup --no-psqlrc --tuples-only --no-align --command="$1"
}

# Quantas jornadas, marcos e exercícios, e uma impressão digital de cada linha que importa.
impressao() {
  sql "select
    (select count(*) || ':' || md5(string_agg(id::text || status || data_referencia, ',' order by id)) from jornada)
    || ' ' || (select count(*) || ':' || md5(string_agg(id::text || status || coalesce(editado_em::text, '-'),
      ',' order by id)) from marco)
    || ' ' || (select count(*) from item_do_bloco)"
}

quantos_backups() {
  find "$pasta" -maxdepth 1 -type f | wc -l
}

echo "Esperando o histórico de exemplo da demonstração..."
jornadas=0
for _ in $(seq 1 30); do
  jornadas=$(sql "select count(*) from jornada")
  if [ "$jornadas" -gt 0 ]; then
    break
  fi
  sleep 2
done
[ "$jornadas" -gt 0 ] || falhar "A demonstração não criou o histórico de exemplo"

mes=$(date -d '20 days ago' +%Y-%m-%d)
antes=$(impressao)
curl -fsS "$base/api/v1/historico?periodo=MES&data=$mes" > "$temporario/historico-antes.json"
echo "Antes: $antes"

# Cenário 2: backup.
saida=$("${compose[@]}" run --rm -T backup)
echo "$saida"
arquivo=$(echo "$saida" | grep -o 'pausa-ativa-[0-9-]*\.dump')
[ -s "$pasta/$arquivo" ] || falhar "O backup não gravou $pasta/$arquivo"

# Com o backend no ar, a restauração recusa.
if "${compose[@]}" run --rm -T restauracao "$arquivo" --sim 2> "$temporario/erro.txt"; then
  falhar "A restauração aceitou o backend conectado ao banco"
fi
grep -q "O backend está conectado" "$temporario/erro.txt" \
  || falhar "Mensagem errada com o backend no ar: $(cat "$temporario/erro.txt")"

# Sem --sim, sem digitar RESTAURAR, nada acontece.
"${compose[@]}" stop backend
if echo "nao" | "${compose[@]}" run --rm -T restauracao "$arquivo" 2> "$temporario/erro.txt"; then
  falhar "A restauração seguiu sem a confirmação"
fi
grep -q "Restauração cancelada" "$temporario/erro.txt" \
  || falhar "Mensagem errada sem a confirmação: $(cat "$temporario/erro.txt")"

# Cenário 2: volume novo, só o banco no ar, restauração e a aplicação de novo.
"${compose[@]}" down -v
"${compose[@]}" up -d --wait postgres
"${compose[@]}" run --rm -T restauracao "$arquivo" --sim
"${compose[@]}" up -d --wait
depois=$(impressao)
echo "Depois: $depois"
[ "$antes" = "$depois" ] || falhar "O banco restaurado é diferente: antes $antes, depois $depois"
curl -fsS "$base/api/v1/historico?periodo=MES&data=$mes" > "$temporario/historico-depois.json"
cmp -s "$temporario/historico-antes.json" "$temporario/historico-depois.json" \
  || falhar "O Histórico de $mes mudou depois da restauração"
echo "Cenário 2: o backup voltou íntegro ($jornadas jornadas), e o Histórico do mês de $mes é o mesmo."

# Restaurar por cima de um banco com dados guarda antes um backup dele.
"${compose[@]}" stop backend
saida=$("${compose[@]}" run --rm -T restauracao "$arquivo" --sim)
echo "$saida"
seguranca=$(echo "$saida" | grep -o 'antes-da-restauracao-[0-9-]*\.dump') \
  || falhar "A restauração por cima de dados não guardou o banco atual"
[ -s "$pasta/$seguranca" ] || falhar "O backup de segurança $pasta/$seguranca não existe"
"${compose[@]}" up -d --wait
[ "$(impressao)" = "$antes" ] || falhar "A segunda restauração mudou o banco"
echo "Por cima de dados: o banco atual foi guardado em $seguranca antes de restaurar."

# Cenário 3: backup com o banco parado.
"${compose[@]}" stop postgres
backups_antes=$(quantos_backups)
if "${compose[@]}" run --rm -T backup 2> "$temporario/erro.txt"; then
  falhar "O backup terminou bem com o banco parado"
fi
grep -q "O banco não respondeu" "$temporario/erro.txt" \
  || falhar "Mensagem errada com o banco parado: $(cat "$temporario/erro.txt")"
[ "$(quantos_backups)" = "$backups_antes" ] || falhar "O backup com o banco parado deixou arquivo"
echo "Cenário 3: com o banco parado, o backup recusou com a mensagem certa e não deixou arquivo."

"${compose[@]}" up -d --wait
