-- Seed for the payment-flow verification scenario.
--
-- Driven by run.sh, which supplies :days, :total, :start_offset so that the
-- SQL and the k6 script cannot drift apart on the stay window.
--
-- Only payment-flow owned rows are removed. A previous run's intents would trip
-- the duplicate-reservation guard (memberId + roomTypeId + checkIn + checkOut)
-- before payment is ever reached, which would look like a pass while proving nothing.

BEGIN;

CREATE TEMP TABLE payflow_intent_ids ON COMMIT DROP AS
    SELECT id FROM reservation_intents WHERE property_id = 'payflow-prop-1';

CREATE TEMP TABLE payflow_reservation_ids ON COMMIT DROP AS
    SELECT id FROM reservations WHERE property_id = 'payflow-prop-1';

CREATE TEMP TABLE payflow_payment_ids ON COMMIT DROP AS
    SELECT id FROM payments
    WHERE reservation_intent_id IN (SELECT id FROM payflow_intent_ids)
       OR reservation_id IN (SELECT id FROM payflow_reservation_ids);

DELETE FROM payment_outbox_messages WHERE payment_id IN (SELECT id FROM payflow_payment_ids);
DELETE FROM payments WHERE id IN (SELECT id FROM payflow_payment_ids);
DELETE FROM inventory_hold_dates
    WHERE hold_id IN (SELECT id FROM inventory_holds WHERE property_id = 'payflow-prop-1');
DELETE FROM inventory_holds WHERE property_id = 'payflow-prop-1';
DELETE FROM active_reservation_attempts
    WHERE reservation_intent_id IN (SELECT id FROM payflow_intent_ids);
DELETE FROM reservation_intent_requests
    WHERE reservation_intent_id IN (SELECT id FROM payflow_intent_ids);
DELETE FROM reservation_intents WHERE id IN (SELECT id FROM payflow_intent_ids);
DELETE FROM reservations WHERE id IN (SELECT id FROM payflow_reservation_ids);
DELETE FROM guests WHERE property_id = 'payflow-prop-1';
DELETE FROM room_inventories WHERE property_id = 'payflow-prop-1';

-- Customer accounts are recreated by the k6 setup stage on every run; dropping
-- them here keeps signup the single place that owns account state
DELETE FROM member_property_accesses
    WHERE member_id IN (SELECT id FROM members WHERE email LIKE 'payflow-c%@example.com');
DELETE FROM members WHERE email LIKE 'payflow-c%@example.com';

INSERT INTO members (
    id, email, password_hash, name, role, status, version, created_at, updated_at
) VALUES (
    'payflow-owner-1', 'payflow-owner@stayops.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'payflow owner', 'OWNER', 'ACTIVE', 0, now(), now()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO properties (
    id, owner_id, name, type,
    address_street, address_city, address_state, address_zip_code, address_country,
    contact_phone, contact_email, description, status, timezone, currency,
    version, created_at, updated_at
) VALUES (
    'payflow-prop-1', 'payflow-owner-1', 'Payment Flow Hotel', 'HOTEL',
    '1 Payment Street', 'Seoul', 'Seoul', '00000', 'KR',
    '010-0000-0000', 'payflow-property@stayops.com', 'payment flow verification', 'ACTIVE',
    'Asia/Seoul', 'KRW', 0, now(), now()
) ON CONFLICT (id) DO UPDATE SET status = 'ACTIVE', updated_at = now();

INSERT INTO room_types (
    id, property_id, name, description, max_occupancy,
    base_price_amount, base_price_currency, version, created_at, updated_at
) VALUES (
    'payflow-rt-1', 'payflow-prop-1', '디럭스', 'payment flow verification', 4,
    150000.00, 'KRW', 0, now(), now()
) ON CONFLICT (id) DO UPDATE SET base_price_amount = 150000.00, updated_at = now();

-- One row per stay date, each with enough rooms that inventory is never the
-- limiting factor: this experiment must fail on payment defects, not on sold-out days
INSERT INTO room_inventories (
    id, property_id, room_type_id, inventory_date,
    total_count, reserved_count, blocked_count, held_count,
    version, created_at, updated_at
)
SELECT
    'payflow-inv-' || to_char(stay_date, 'YYYYMMDD'),
    'payflow-prop-1', 'payflow-rt-1', stay_date,
    :total, 0, 0, 0, 0, now(), now()
FROM generate_series(
    CURRENT_DATE + (:start_offset - 1) * INTERVAL '1 day',
    CURRENT_DATE + (:start_offset + :days + 1) * INTERVAL '1 day',
    INTERVAL '1 day'
) AS stay_date;

COMMIT;

SELECT count(*) AS inventory_days,
       min(inventory_date) AS first_date,
       max(inventory_date) AS last_date,
       sum(total_count) AS total_rooms
FROM room_inventories
WHERE property_id = 'payflow-prop-1';
