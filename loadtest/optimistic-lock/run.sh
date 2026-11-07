#!/bin/bash
# Fires N concurrent reservation-intent requests at a single-room inventory
# and reports how each request ended.
#
# Usage: ./run.sh [concurrency] [base-url]
#
# Rate limiting is disabled by default (stayops.rate-limit.enabled). If it is
# turned back on via STAYOPS_RATE_LIMIT_ENABLED=true, account setup dies on
# customer-auth-signup (5 per 600s per IP) long before the inventory is touched.
set -euo pipefail

CONCURRENCY="${1:-30}"
BASE_URL="${2:-http://localhost:8080}"
PROPERTY_ID="oplock-prop-1"
ROOM_TYPE_ID="oplock-rt-1"
CHECK_IN="2026-09-01"
CHECK_OUT="2026-09-02"
PASSWORD="password123"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

csrf_token() {
    curl -s -b "$1" -c "$1" "$BASE_URL/api/v1/csrf" \
        | sed 's/.*"token":"\([^"]*\)".*/\1/'
}

echo "==> 1/4 재고 초기화 (total_count=1)"
docker exec -i stayops-postgres-1 psql -q -v ON_ERROR_STOP=1 -U stayops -d stayops \
    < "$SCRIPT_DIR/seed.sql" > /dev/null

echo "==> 2/4 고객 계정 $CONCURRENCY개 준비"
# Each request needs its own member: the duplicate guard keys on
# memberId + roomTypeId + checkIn + checkOut, and would mask inventory contention
for i in $(seq 1 "$CONCURRENCY"); do
    jar="$WORK_DIR/jar-$i.txt"
    token="$(csrf_token "$jar")"
    curl -s -o /dev/null -b "$jar" -c "$jar" \
        -X POST "$BASE_URL/api/v1/customer/auth/signup" \
        -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $token" \
        -d "{\"email\":\"oplock-$i@example.com\",\"password\":\"$PASSWORD\",\"name\":\"테스터$i\"}" || true

    token="$(csrf_token "$jar")"
    curl -s -o /dev/null -b "$jar" -c "$jar" \
        -X POST "$BASE_URL/api/v1/customer/auth/login" \
        -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $token" \
        -d "{\"email\":\"oplock-$i@example.com\",\"password\":\"$PASSWORD\"}"
    csrf_token "$jar" > "$WORK_DIR/csrf-$i.txt"
done

echo "==> 3/4 워밍업"
# The first request pays for JIT and connection-pool startup; without this the
# rest would queue behind it and never actually contend
warm_jar="$WORK_DIR/jar-1.txt"
curl -s -o /dev/null -b "$warm_jar" -c "$warm_jar" \
    "$BASE_URL/api/v1/customer/properties/$PROPERTY_ID/room-types"

# Remember where the log ends so the summary counts only this run, not earlier ones
LOG_OFFSET=0
if [[ -n "${LOG_FILE:-}" && -f "${LOG_FILE}" ]]; then
    LOG_OFFSET=$(wc -l < "$LOG_FILE")
fi

echo "==> 4/4 동시 요청 $CONCURRENCY건 발사"
export BASE_URL WORK_DIR PROPERTY_ID ROOM_TYPE_ID CHECK_IN CHECK_OUT
seq 1 "$CONCURRENCY" \
    | xargs -P "$CONCURRENCY" -I {} "$SCRIPT_DIR/fire.sh" {} \
    > "$WORK_DIR/results.txt"

echo
echo "===== 요청 결과 ====="
sort "$WORK_DIR/results.txt" | uniq -c | sort -rn

echo
echo "===== 재고 최종 상태 ====="
docker exec -i stayops-postgres-1 psql -U stayops -d stayops <<'SQL'
SELECT total_count, reserved_count, held_count, blocked_count, version,
       total_count - reserved_count - held_count - blocked_count AS available
FROM room_inventories WHERE id = 'oplock-inv-1';
SQL

# Pass LOG_FILE=<app log> to also summarise what the app itself recorded
if [[ -n "${LOG_FILE:-}" && -f "${LOG_FILE}" ]]; then
    echo
    echo "===== 애플리케이션 로그 (이번 실행분) ====="
    run_log="$WORK_DIR/run.log"
    tail -n "+$((LOG_OFFSET + 1))" "$LOG_FILE" | sed 's/\x1b\[[0-9;]*m//g' > "$run_log"

    grep -o '\[재고 저장\].*' "$run_log" || true
    grep -o '\[재고 낙관적 락 충돌\].*' "$run_log" | sort | uniq -c || true
    echo "-- 충돌을 기록한 스레드 수: $(
        grep '재고 낙관적 락 충돌' "$run_log" \
            | grep -o 'http-nio-8080-exec-[0-9]*' | sort -u | wc -l | tr -d ' '
    )"
fi
