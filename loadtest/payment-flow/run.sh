#!/bin/bash
# Runs the payment-flow verification: seed -> k6 -> outbox drain -> invariant checks.
#
# Usage: ./run.sh [iterations] [vus]
#
# The app must run with STAYOPS_PAYMENT_GATEWAY=loadtest. With the default `toss`
# gateway every synthetic paymentKey is rejected by the real Toss API, so the run
# would only ever exercise the failure path (and send external traffic while doing it).
#
# Pass LOG_FILE=<app log> to also aggregate what the app recorded for this run.
set -euo pipefail

ITERATIONS="${1:-200}"
VUS="${2:-20}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
PGCONTAINER="${PGCONTAINER:-stayops-postgres-1}"
RUN_ID="${PAYFLOW_RUN_ID:-$(date +%H%M%S)}"

# Shared by seed.sql and k6 so the stay window cannot drift between them
STAY_START_OFFSET="${STAY_START_OFFSET:-30}"
STAY_DAYS="${STAY_DAYS:-20}"
CUSTOMERS="${CUSTOMERS:-40}"
INVENTORY_TOTAL="${INVENTORY_TOTAL:-60}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

psql_run() {
    docker exec -i "$PGCONTAINER" psql -q -v ON_ERROR_STOP=1 -U stayops -d stayops "$@"
}

echo "==> 1/4 시드 (재고 ${STAY_DAYS}일, 일별 ${INVENTORY_TOTAL}실)"
psql_run \
    -v "days=$STAY_DAYS" \
    -v "total=$INVENTORY_TOTAL" \
    -v "start_offset=$STAY_START_OFFSET" \
    < "$SCRIPT_DIR/seed.sql"

# Remember where the log ends so the summary counts only this run
LOG_OFFSET=0
if [[ -n "${LOG_FILE:-}" && -f "${LOG_FILE}" ]]; then
    LOG_OFFSET=$(wc -l < "$LOG_FILE")
fi

echo
echo "==> 2/4 k6 실행 (iterations=$ITERATIONS, vus=$VUS)"
set +e
BASE_URL="$BASE_URL" \
PAYFLOW_RUN_ID="$RUN_ID" \
ITERATIONS="$ITERATIONS" \
VUS="$VUS" \
CUSTOMERS="$CUSTOMERS" \
STAY_START_OFFSET="$STAY_START_OFFSET" \
STAY_DAYS="$STAY_DAYS" \
k6 run "$SCRIPT_DIR/payment-flow.js"
K6_EXIT=$?
set -e

# The API only queues the confirm; the worker polls every 5s by default, so the
# last bookings need a grace period before the invariants can hold
DRAIN_TIMEOUT="${DRAIN_TIMEOUT:-60}"
echo
echo "==> 3/4 Outbox 소진 대기 (최대 ${DRAIN_TIMEOUT}s)"
for _ in $(seq 1 "$DRAIN_TIMEOUT"); do
    remaining=$(docker exec -i "$PGCONTAINER" psql -tA -U stayops -d stayops -c \
        "SELECT count(*) FROM payment_outbox_messages o
           JOIN reservation_intents i ON i.id = o.reservation_intent_id
          WHERE i.property_id = 'payflow-prop-1'
            AND o.status IN ('PENDING', 'IN_PROGRESS');")
    if [[ "$remaining" == "0" ]]; then
        echo "    남은 미처리 Outbox: 0"
        break
    fi
    sleep 1
done

echo
echo "==> 4/4 정합성 검증"
docker exec -i "$PGCONTAINER" psql -U stayops -d stayops < "$SCRIPT_DIR/verify.sql"

if [[ -n "${LOG_FILE:-}" && -f "${LOG_FILE}" ]]; then
    echo
    echo "===== 애플리케이션 로그 이벤트 (이번 실행분) ====="
    tail -n "+$((LOG_OFFSET + 1))" "$LOG_FILE" \
        | sed 's/\x1b\[[0-9;]*m//g' \
        | grep -o 'event=[a-z_]*' \
        | sort | uniq -c | sort -rn

    echo
    echo "===== 경고 이상 결제 로그 (최대 20건) ====="
    tail -n "+$((LOG_OFFSET + 1))" "$LOG_FILE" \
        | sed 's/\x1b\[[0-9;]*m//g' \
        | grep -E 'WARN|ERROR' \
        | grep -E 'payment|예약 intent' \
        | head -20 || echo "    없음"
fi

exit "$K6_EXIT"
