#!/usr/bin/env bash
# Applies every migration to a throwaway Postgres (the Supabase image) and runs
# the SQL tests against it. Nothing touches a real Supabase project.
#   ./scripts/sql-tests.sh
set -euo pipefail

cd "$(dirname "$0")/.."
NAME="ngantriin-sql-tests-$$"
IMAGE="public.ecr.aws/supabase/postgres:17.6.1.171"

cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

psql_in() { docker exec -i "$NAME" psql -U supabase_admin -d postgres -v ON_ERROR_STOP=1 -q "$@"; }

docker run -d --name "$NAME" -e POSTGRES_PASSWORD=pw "$IMAGE" >/dev/null
echo "Waiting for Postgres..."
for _ in $(seq 1 60); do
  docker exec "$NAME" pg_isready -U supabase_admin -d postgres >/dev/null 2>&1 && break
  sleep 2
done
# The image restarts once while initialising; wait for it to settle.
sleep 5
until docker exec "$NAME" pg_isready -U supabase_admin -d postgres >/dev/null 2>&1; do sleep 2; done

psql_in < supabase/tests/ci_bootstrap.sql
for f in supabase/migrations/*.sql; do
  echo "migrate  $f"
  psql_in < "$f" >/dev/null 2>&1 || { echo "FAILED: $f"; psql_in < "$f"; exit 1; }
done

status=0
for t in rls journey fcm_token staff_admin concurrency; do
  echo "test     $t"
  out=$(psql_in < "supabase/tests/${t}_test.sql" 2>&1) || { echo "$out"; echo "FAILED: $t"; status=1; continue; }
  echo "$out" | grep -E 'PASSED|passed' || true
done
exit $status
