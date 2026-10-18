#!/usr/bin/env bash
# LOCAL DEVELOPMENT ONLY. One-shot setup for the read-only Postgres MCP role.
# Runs: tenant pick -> provision-mcp-readonly.sql -> RLS + write checks
#       -> register fabric-db-ro in Claude Desktop config -> docker pull.
# Safe to re-run (rotates the password, re-derives grants).
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SQL="$HERE/provision-mcp-readonly.sql"
PG=fabric-postgres
DB=fabric_management
OWNER=fabric_owner
HOST_PORT=5433

say() { printf '\n== %s\n' "$*"; }

docker ps --format '{{.Names}}' | grep -qx "$PG" \
  || { echo "Container $PG is not running. Start it first: docker compose up -d postgres"; exit 1; }

say "1/5 Pick the tenant the assistant will read as"
rows=$(docker exec "$PG" psql -U "$OWNER" -d "$DB" -At -F '|' \
  -c "SELECT id, slug, name, status FROM common_tenant.common_tenant ORDER BY name;")
total=0
while IFS='|' read -r id slug name status; do
  [ -n "$id" ] || continue
  total=$((total+1)); ids[$total]=$id
  printf '%3d) %s  [%s, %s]\n' "$total" "$name" "$slug" "$status"
done <<< "$rows"
read -rp "Tenant number: " n
TENANT="${ids[$n]:-}"
[ -n "$TENANT" ] || { echo "Invalid choice"; exit 1; }

say "2/5 Provision fabric_mcp_ro"
PW=$(openssl rand -hex 24)
docker exec -i "$PG" psql -q -v ON_ERROR_STOP=1 -U "$OWNER" -d "$DB" \
  -v mcp_pw="$PW" -v tenant_id="$TENANT" < "$SQL"

say "3/5 Check: RLS binds the role to the chosen tenant"
seen=$(docker exec "$PG" psql -U fabric_mcp_ro -d "$DB" -At \
  -c "SELECT count(*) || ':' || coalesce(max(id::text),'') FROM common_tenant.common_tenant;")
if [ "$seen" = "1:$TENANT" ]; then
  echo "PASS  owner sees $total tenants, fabric_mcp_ro sees only its own"
else
  echo "FAIL  fabric_mcp_ro saw: $seen"; exit 1
fi

say "4/5 Check: writes are refused"
if docker exec "$PG" psql -U fabric_mcp_ro -d "$DB" \
     -c "CREATE TABLE public.mcp_probe(x int);" >/dev/null 2>&1; then
  docker exec "$PG" psql -U "$OWNER" -d "$DB" -c "DROP TABLE IF EXISTS public.mcp_probe;" >/dev/null
  echo "FAIL  fabric_mcp_ro could create a table"; exit 1
fi
echo "PASS  write refused"
docker exec -e PGPASSWORD="$PW" "$PG" psql -h 127.0.0.1 -U fabric_mcp_ro -d "$DB" -At -c "SELECT 1" >/dev/null \
  && echo "PASS  password login over TCP works"

say "5/5 Register the MCP server in Claude Desktop"
DOCKER_BIN="$(command -v docker)"
CFG="$HOME/Library/Application Support/Claude/claude_desktop_config.json"
mkdir -p "$(dirname "$CFG")"
[ -f "$CFG" ] && cp "$CFG" "$CFG.bak-$(date +%Y%m%d%H%M%S)" && echo "Backed up existing config"
URI="postgresql://fabric_mcp_ro:${PW}@host.docker.internal:${HOST_PORT}/${DB}" \
DOCKER_BIN="$DOCKER_BIN" python3 - "$CFG" <<'PY'
import json, os, sys
path = sys.argv[1]
try:
    with open(path) as f:
        cfg = json.load(f)
except FileNotFoundError:
    cfg = {}
cfg.setdefault("mcpServers", {})["fabric-db-ro"] = {
    "command": os.environ["DOCKER_BIN"],
    "args": ["run", "-i", "--rm", "-e", "DATABASE_URI",
             "crystaldba/postgres-mcp", "--access-mode=restricted"],
    "env": {"DATABASE_URI": os.environ["URI"]},
}
with open(path, "w") as f:
    json.dump(cfg, f, indent=2)
print("Registered fabric-db-ro")
PY
docker pull -q crystaldba/postgres-mcp

say "Done. Quit Claude Desktop completely (Cmd+Q) and reopen it."
