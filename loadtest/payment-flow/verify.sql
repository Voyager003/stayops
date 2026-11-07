-- Post-run invariant checks for the payment-flow experiment.
--
-- Every section prints a verdict column so a pass needs no interpretation:
-- anything other than OK is a defect. Section 1 is the only informational one.

\pset footer off

\echo '===== 1. 상태 분포 (참고) ====='
SELECT 'intent' AS entity, status, count(*)
FROM reservation_intents WHERE property_id = 'payflow-prop-1'
GROUP BY 1, 2
UNION ALL
SELECT 'payment', p.status, count(*)
FROM payments p
JOIN reservation_intents i ON i.id = p.reservation_intent_id
WHERE i.property_id = 'payflow-prop-1'
GROUP BY 1, 2
UNION ALL
SELECT 'outbox', o.status, count(*)
FROM payment_outbox_messages o
JOIN reservation_intents i ON i.id = o.reservation_intent_id
WHERE i.property_id = 'payflow-prop-1'
GROUP BY 1, 2
UNION ALL
SELECT 'reservation', status, count(*)
FROM reservations WHERE property_id = 'payflow-prop-1'
GROUP BY 1, 2
ORDER BY 1, 2;

\echo ''
\echo '===== 2. 승인 결제 수 == 예약 수 ====='
-- An APPROVED payment that produced no reservation means money was taken for nothing
SELECT approved_payments, reservations,
       CASE WHEN approved_payments = reservations THEN 'OK' ELSE 'FAIL' END AS verdict
FROM (
    SELECT
        (SELECT count(*) FROM payments p
           JOIN reservation_intents i ON i.id = p.reservation_intent_id
          WHERE i.property_id = 'payflow-prop-1' AND p.status = 'APPROVED') AS approved_payments,
        (SELECT count(*) FROM reservations WHERE property_id = 'payflow-prop-1') AS reservations
) counts;

\echo ''
\echo '===== 3. 예약 없이 승인된 결제 (고아 결제) ====='
SELECT count(*) AS orphan_approved_payments,
       CASE WHEN count(*) = 0 THEN 'OK' ELSE 'FAIL' END AS verdict
FROM payments p
JOIN reservation_intents i ON i.id = p.reservation_intent_id
WHERE i.property_id = 'payflow-prop-1'
  AND p.status = 'APPROVED'
  AND p.reservation_id IS NULL;

\echo ''
\echo '===== 4. RESERVED intent의 결제/예약 연결 누락 ====='
SELECT count(*) AS broken_reserved_intents,
       CASE WHEN count(*) = 0 THEN 'OK' ELSE 'FAIL' END AS verdict
FROM reservation_intents i
LEFT JOIN payments p ON p.reservation_intent_id = i.id
WHERE i.property_id = 'payflow-prop-1'
  AND i.status = 'RESERVED'
  AND (i.reservation_id IS NULL OR p.status <> 'APPROVED' OR p.reservation_id <> i.reservation_id);

\echo ''
\echo '===== 5. 동일 조건 중복 예약 (중복 승인 요청이 예약을 2건 만들었는지) ====='
SELECT count(*) AS duplicated_bookings,
       CASE WHEN count(*) = 0 THEN 'OK' ELSE 'FAIL' END AS verdict
FROM (
    SELECT member_id, room_type_id, check_in, check_out
    FROM reservations
    WHERE property_id = 'payflow-prop-1' AND status <> 'CANCELLED'
    GROUP BY 1, 2, 3, 4
    HAVING count(*) > 1
) duplicates;

\echo ''
\echo '===== 6. 미처리 Outbox 잔여 ====='
-- PENDING/IN_PROGRESS survivors mean the worker never drained; FAILED means it gave up
SELECT
    count(*) FILTER (WHERE o.status IN ('PENDING', 'IN_PROGRESS')) AS unprocessed,
    count(*) FILTER (WHERE o.status = 'FAILED') AS failed,
    CASE WHEN count(*) FILTER (WHERE o.status IN ('PENDING', 'IN_PROGRESS', 'FAILED')) = 0
         THEN 'OK' ELSE 'FAIL' END AS verdict
FROM payment_outbox_messages o
JOIN reservation_intents i ON i.id = o.reservation_intent_id
WHERE i.property_id = 'payflow-prop-1';

\echo ''
\echo '===== 7. 재고 held_count 잔여 (hold 해제/소비 누락) ====='
SELECT sum(held_count) AS leftover_held,
       CASE WHEN coalesce(sum(held_count), 0) = 0 THEN 'OK' ELSE 'FAIL' END AS verdict
FROM room_inventories
WHERE property_id = 'payflow-prop-1';

\echo ''
\echo '===== 8. 재고 reserved_count == 확정 예약 숙박일 수 ====='
SELECT reserved_nights, confirmed_nights,
       CASE WHEN reserved_nights = confirmed_nights THEN 'OK' ELSE 'FAIL' END AS verdict
FROM (
    SELECT
        (SELECT coalesce(sum(reserved_count), 0) FROM room_inventories
          WHERE property_id = 'payflow-prop-1') AS reserved_nights,
        (SELECT coalesce(sum(night_count), 0) FROM reservations
          WHERE property_id = 'payflow-prop-1' AND status <> 'CANCELLED') AS confirmed_nights
) counts;

\echo ''
\echo '===== 9. 종료되지 않은 hold ====='
SELECT count(*) AS open_holds,
       CASE WHEN count(*) = 0 THEN 'OK' ELSE 'FAIL' END AS verdict
FROM inventory_holds
WHERE property_id = 'payflow-prop-1'
  AND status NOT IN ('CONSUMED', 'RELEASED');

\echo ''
\echo '===== 10. 정리되지 않은 활성 예약 시도 ====='
SELECT count(*) AS open_attempts,
       CASE WHEN count(*) = 0 THEN 'OK' ELSE 'FAIL' END AS verdict
FROM active_reservation_attempts a
JOIN reservation_intents i ON i.id = a.reservation_intent_id
WHERE i.property_id = 'payflow-prop-1';
