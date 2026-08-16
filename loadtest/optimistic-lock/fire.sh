#!/bin/bash
# Sends one reservation-intent request as customer $1 and prints "<status> <code>".
# Split out of run.sh because xargs cannot carry this much inline script.
set -uo pipefail

i="$1"
jar="$WORK_DIR/jar-$i.txt"
csrf="$(cat "$WORK_DIR/csrf-$i.txt")"

response=$(curl -s -b "$jar" -X POST "$BASE_URL/api/v1/customer/reservation-intents" \
    -H 'Content-Type: application/json' \
    -H "X-CSRF-TOKEN: $csrf" \
    -H "Idempotency-Key: $(uuidgen)" \
    -w '\nHTTP=%{http_code}' \
    -d "{\"propertyId\":\"$PROPERTY_ID\",\"roomTypeId\":\"$ROOM_TYPE_ID\",\"checkIn\":\"$CHECK_IN\",\"checkOut\":\"$CHECK_OUT\",\"numberOfGuests\":2,\"guestName\":\"tester$i\",\"guestPhone\":\"010-1234-5678\"}")

status=$(printf '%s' "$response" | sed -n 's/^HTTP=//p')
code=$(printf '%s' "$response" | sed -n 's/.*"code":"\([^"]*\)".*/\1/p')

# 401/429 carry no error code — keep them visually distinct from a real success
echo "$status ${code:-NO_CODE}"
