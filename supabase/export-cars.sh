#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_FILE=''

usage() {
  cat <<'USAGE'
Uso:
  ./supabase/export-cars.sh [--output arquivo.csv]

Exporta somente o catálogo público de carros (CSV). Sem --output, escreve
no stdout. O script nunca exporta contas, clientes, agentes ou pedidos.
USAGE
}

while (($#)); do
  case "$1" in
    --output)
      (($# >= 2)) || { usage >&2; exit 2; }
      OUTPUT_FILE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      exit 2
      ;;
  esac
done

command -v psql >/dev/null 2>&1 || {
  printf 'Erro: psql não encontrado. Instale o cliente PostgreSQL.\n' >&2
  exit 1
}

if [[ -f "$ROOT_DIR/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env"
  set +a
fi

DB_HOST="${SUPABASE_DB_HOST:-127.0.0.1}"
DB_PORT="${SUPABASE_DB_PORT:-54322}"
DB_NAME="${SUPABASE_DB_NAME:-postgres}"
DB_USER="${SPRING_DATASOURCE_USERNAME:-${SUPABASE_DB_USER:-postgres}}"
DB_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-${SUPABASE_LOCAL_DB_PASSWORD:-}}"

[[ "$DB_PORT" =~ ^[0-9]+$ ]] && ((DB_PORT > 0 && DB_PORT < 65536)) || {
  printf 'Erro: SUPABASE_DB_PORT deve ser uma porta entre 1 e 65535.\n' >&2
  exit 2
}
[[ -n "$DB_PASSWORD" ]] || {
  printf 'Erro: defina SUPABASE_LOCAL_DB_PASSWORD ou SPRING_DATASOURCE_PASSWORD no .env.\n' >&2
  exit 2
}

# A senha é entregue ao libpq pelo ambiente, nunca como argumento ou log.
export PGPASSWORD="$DB_PASSWORD"
unset DB_PASSWORD

PSQL_ARGS=(--no-psqlrc --set ON_ERROR_STOP=1 --host "$DB_HOST" --port "$DB_PORT" --username "$DB_USER" --dbname "$DB_NAME")
QUERY="COPY (
  SELECT id, make, model, year, category, transmission, fuel, daily_price, image_url, available
  FROM public.cars
  WHERE available = true
  ORDER BY make, model
) TO STDOUT WITH (FORMAT CSV, HEADER TRUE)"

if [[ -n "$OUTPUT_FILE" ]]; then
  mkdir -p -- "$(dirname -- "$OUTPUT_FILE")"
  psql "${PSQL_ARGS[@]}" --command "$QUERY" >"$OUTPUT_FILE"
  printf 'Catálogo público de carros exportado para %s.\n' "$OUTPUT_FILE" >&2
else
  psql "${PSQL_ARGS[@]}" --command "$QUERY"
fi
