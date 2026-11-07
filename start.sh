#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

MODE="${1:-run}"
case "$MODE" in
  run|--diagnose) ;;
  *) printf 'Uso: %s [--diagnose]\n' "$0" >&2; exit 2 ;;
esac

fail() {
  printf 'Erro: %s\n' "$1" >&2
  exit 1
}

if [[ ! -f .env ]]; then
  cp .env.example .env || fail 'Não foi possível criar .env a partir de .env.example.'
  printf 'Criei .env a partir de .env.example. Confira os valores locais antes de continuar.\n'
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

SUPABASE_DB_NAME="${SUPABASE_DB_NAME:-postgres}"
SUPABASE_DB_USER="${SUPABASE_DB_USER:-postgres}"
SUPABASE_LOCAL_DB_PASSWORD="${SUPABASE_LOCAL_DB_PASSWORD:-postgres}"
[[ "$SUPABASE_DB_NAME" == 'postgres' ]] || fail "O Supabase local usa o banco 'postgres'; ajuste SUPABASE_DB_NAME em .env."
[[ "$SUPABASE_DB_USER" == 'postgres' ]] || fail "O Supabase local usa o usuário 'postgres'; ajuste SUPABASE_DB_USER em .env."
[[ -n "$SUPABASE_LOCAL_DB_PASSWORD" ]] || fail 'SUPABASE_LOCAL_DB_PASSWORD não pode ficar vazio.'

export SUPABASE_DB_NAME SUPABASE_DB_USER SUPABASE_LOCAL_DB_PASSWORD
export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://127.0.0.1:54322/${SUPABASE_DB_NAME}?sslmode=disable}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-$SUPABASE_DB_USER}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-$SUPABASE_LOCAL_DB_PASSWORD}"
export SPRING_JPA_DDL_AUTO="${SPRING_JPA_DDL_AUTO:-validate}"
export SERVER_PORT="${SERVER_PORT:-8088}"
export AGENT_INVITE_CODE="${AGENT_INVITE_CODE:-rota-agente-local}"
export SESSION_HOURS="${SESSION_HOURS:-12}"
export CORS_ALLOWED_ORIGIN="${CORS_ALLOWED_ORIGIN:-http://localhost:5173}"
export VITE_API_URL="${VITE_API_URL:-http://localhost:${SERVER_PORT}/api}"
export OPENAI_API_KEY="${OPENAI_API_KEY:-${MARITACA_API_KEY:-${APIKEY:-}}}"
export OPENAI_BASE_URL="${OPENAI_BASE_URL:-https://chat.maritaca.ai/api}"
export OPENAI_MODEL="${OPENAI_MODEL:-sabiazinho-4}"
export OPENAI_MAX_TOKENS="${OPENAI_MAX_TOKENS:-900}"
export SUPABASE_TELEMETRY_DISABLED="${SUPABASE_TELEMETRY_DISABLED:-true}"
STARTUP_TIMEOUT_SECONDS="${STARTUP_TIMEOUT_SECONDS:-180}"

[[ "$SPRING_JPA_DDL_AUTO" == 'validate' ]] || fail 'O schema é controlado pelas migrations; SPRING_JPA_DDL_AUTO deve permanecer validate.'
[[ "$SERVER_PORT" =~ ^[0-9]+$ ]] && (( SERVER_PORT > 0 && SERVER_PORT < 65536 )) || fail 'SERVER_PORT deve ser uma porta entre 1 e 65535.'
[[ "$STARTUP_TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] && (( STARTUP_TIMEOUT_SECONDS > 0 )) || fail 'STARTUP_TIMEOUT_SECONDS deve ser um número inteiro positivo.'
[[ "$OPENAI_MAX_TOKENS" =~ ^[0-9]+$ ]] && (( OPENAI_MAX_TOKENS >= 128 && OPENAI_MAX_TOKENS <= 4096 )) || fail 'OPENAI_MAX_TOKENS deve estar entre 128 e 4096.'

for command_name in docker supabase java mvn node npm curl; do
  command -v "$command_name" >/dev/null 2>&1 || fail "Comando '$command_name' não encontrado. Instale a dependência e tente novamente."
done

printf 'Java: '; java -version 2>&1 | sed -n '1p'
printf 'Maven: '; mvn -version 2>/dev/null | sed -n '1p'
printf 'Node.js: '; node --version
printf 'Iniciando o ambiente local (JPA ddl-auto=%s).\n' "$SPRING_JPA_DDL_AUTO"
if [[ -n "$OPENAI_API_KEY" ]]; then
  printf 'Assistente conversacional configurado (modelo %s).\n' "$OPENAI_MODEL"
else
  printf 'Assistente conversacional sem chave; defina OPENAI_API_KEY no .env para habilitá-lo.\n'
fi

RUN_DIR="$(mktemp -d "${TMPDIR:-/tmp}/lab2-start.XXXXXX")"
SUPABASE_LOG="$RUN_DIR/supabase.log"
MIGRATION_LOG="$RUN_DIR/migrations.log"
MIGRATION_STATUS_LOG="$RUN_DIR/migration-status.log"
FRONTEND_BUILD_LOG="$RUN_DIR/frontend-build.log"
BACKEND_LOG="$RUN_DIR/backend.log"
FRONTEND_LOG="$RUN_DIR/frontend.log"
CORS_HEADERS="$RUN_DIR/cors-headers.txt"
child_pids=()

safe_tail() {
  local logfile="$1"
  local label="$2"
  [[ -s "$logfile" ]] || return 0
  printf '\n--- %s (trecho final, segredos ocultados) ---\n' "$label" >&2
  tail -n 100 "$logfile" | sed -E \
    -e 's/(password|passwd|token|secret|anon key|service_role key)([[:space:]]*[:=][[:space:]]*|[[:space:]]+)[^[:space:],;]+/\1\2[OCULTO]/Ig' \
    -e 's#((jdbc:)?(postgresql|postgres)://)[^/@[:space:]]+:[^/@[:space:]]+@#\1[OCULTO]@#Ig' \
    -e 's/eyJ[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}/[TOKEN JWT OCULTO]/g' >&2
}

cleanup() {
  local pid
  for pid in "${child_pids[@]:-}"; do
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      kill -TERM "$pid" 2>/dev/null || true
    fi
  done
  for pid in "${child_pids[@]:-}"; do
    if [[ -n "$pid" ]]; then wait "$pid" 2>/dev/null || true; fi
  done
  [[ -n "${RUN_DIR:-}" ]] && rm -rf -- "$RUN_DIR"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if ! docker info >/dev/null 2>"$SUPABASE_LOG"; then
  safe_tail "$SUPABASE_LOG" 'Docker'
  fail 'O cliente Docker está instalado, mas o daemon não respondeu. Inicie o serviço Docker e execute novamente.'
fi

printf 'Iniciando ou verificando o Supabase local...\n'
if ! supabase start >"$SUPABASE_LOG" 2>&1; then
  safe_tail "$SUPABASE_LOG" 'supabase start'
  fail 'Supabase não iniciou. Os logs acima foram filtrados para ocultar chaves e credenciais.'
fi
printf 'Supabase local disponível.\n'

printf 'Aplicando migrations pendentes sem resetar o banco...\n'
if ! supabase migration up --local >"$MIGRATION_LOG" 2>&1; then
  safe_tail "$MIGRATION_LOG" 'supabase migration up --local'
  fail 'Não foi possível aplicar as migrations locais. Nenhum reset do banco foi executado.'
fi
if ! supabase migration list --local >"$MIGRATION_STATUS_LOG" 2>&1; then
  safe_tail "$MIGRATION_STATUS_LOG" 'supabase migration list --local'
  fail 'As migrations foram solicitadas, mas não foi possível confirmar o estado aplicado.'
fi
for migration_version in 20261106000000 20261106000001; do
  if ! grep -Fq "$migration_version" "$MIGRATION_STATUS_LOG"; then
    safe_tail "$MIGRATION_STATUS_LOG" 'estado das migrations'
    fail "A migration $migration_version não aparece como aplicada."
  fi
done
printf 'Migrations de esquema e catálogo de veículos confirmadas.\n'

if [[ ! -d frontend/node_modules ]]; then
  printf 'Instalando dependências do frontend...\n'
  if ! npm --prefix frontend ci --no-audit --no-fund >"$FRONTEND_BUILD_LOG" 2>&1; then
    safe_tail "$FRONTEND_BUILD_LOG" 'instalação do frontend'
    fail 'Não foi possível instalar as dependências do frontend.'
  fi
fi

printf 'Validando o build do frontend...\n'
if ! npm --prefix frontend run build >"$FRONTEND_BUILD_LOG" 2>&1; then
  safe_tail "$FRONTEND_BUILD_LOG" 'build do frontend'
  fail 'O frontend não compilou; consulte o erro acima.'
fi

API_BASE_URL="http://127.0.0.1:${SERVER_PORT}/api"
API_HEALTH_URL="${API_BASE_URL}/health"
FRONTEND_URL='http://127.0.0.1:5173/'
BACKEND_PID=''
FRONTEND_PID=''

if ! curl --silent --show-error --fail --max-time 2 "$API_HEALTH_URL" >/dev/null 2>&1; then
  printf 'Iniciando Spring Boot na porta %s...\n' "$SERVER_PORT"
  (cd backend && exec mvn -B spring-boot:run) >"$BACKEND_LOG" 2>&1 &
  BACKEND_PID=$!
  child_pids+=("$BACKEND_PID")
else
  printf 'API já responde na porta %s.\n' "$SERVER_PORT"
fi

if ! curl --silent --show-error --fail --max-time 2 "$FRONTEND_URL" >/dev/null 2>&1; then
  printf 'Iniciando Vite na porta 5173...\n'
  (cd frontend && exec npm run dev) >"$FRONTEND_LOG" 2>&1 &
  FRONTEND_PID=$!
  child_pids+=("$FRONTEND_PID")
else
  printf 'Frontend já responde na porta 5173.\n'
fi

wait_for_http() {
  local name="$1"
  local url="$2"
  local pid="$3"
  local logfile="$4"
  local attempt
  for ((attempt = 1; attempt <= STARTUP_TIMEOUT_SECONDS; attempt++)); do
    if curl --silent --show-error --fail --max-time 2 "$url" >/dev/null 2>&1; then
      printf '%s respondeu.\n' "$name"
      return 0
    fi
    if [[ -n "$pid" ]] && ! kill -0 "$pid" 2>/dev/null; then
      safe_tail "$logfile" "$name"
      fail "$name encerrou antes de ficar pronto."
    fi
    sleep 1
  done
  safe_tail "$logfile" "$name"
  fail "Tempo esgotado aguardando $name em $url."
}

wait_for_http 'Backend Spring Boot' "$API_HEALTH_URL" "$BACKEND_PID" "$BACKEND_LOG"
wait_for_http 'Frontend Vite' "$FRONTEND_URL" "$FRONTEND_PID" "$FRONTEND_LOG"

if ! curl --silent --show-error --fail --max-time 5 \
    -H "Origin: $CORS_ALLOWED_ORIGIN" -D "$CORS_HEADERS" -o /dev/null "$API_HEALTH_URL"; then
  safe_tail "$BACKEND_LOG" 'verificação CORS da API'
  fail 'A API não respondeu à verificação de origem CORS.'
fi
if ! grep -Fqi "Access-Control-Allow-Origin: $CORS_ALLOWED_ORIGIN" "$CORS_HEADERS"; then
  safe_tail "$BACKEND_LOG" 'verificação CORS da API'
  fail "A API não autorizou CORS para a origem configurada em CORS_ALLOWED_ORIGIN."
fi

printf '\nAmbiente validado:\n'
printf '  PostgreSQL/Supabase: local e migrations aplicadas\n'
printf '  JPA: Hibernate iniciou com ddl-auto=validate\n'
printf '  API: %s\n' "$API_HEALTH_URL"
printf '  Frontend: %s\n' "$FRONTEND_URL"
printf '  CORS: origem %s aceita pela API\n' "$CORS_ALLOWED_ORIGIN"

if [[ "$MODE" == '--diagnose' ]]; then
  printf 'Diagnóstico concluído. Os processos Spring Boot e Vite iniciados por este comando serão encerrados; o Supabase permanece ativo.\n'
  exit 0
fi

printf 'Pressione Ctrl+C para encerrar Spring Boot e Vite.\n'
while true; do
  sleep 2
  if [[ -n "$BACKEND_PID" ]] && ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    safe_tail "$BACKEND_LOG" 'Backend Spring Boot'
    fail 'O backend encerrou inesperadamente.'
  fi
  if [[ -n "$FRONTEND_PID" ]] && ! kill -0 "$FRONTEND_PID" 2>/dev/null; then
    safe_tail "$FRONTEND_LOG" 'Frontend Vite'
    fail 'O frontend encerrou inesperadamente.'
  fi
done
