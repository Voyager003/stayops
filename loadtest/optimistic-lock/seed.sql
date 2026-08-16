-- Seed for the optimistic-lock verification scenario.
-- Creates a single room-type/date whose inventory holds exactly one room,
-- so that every concurrent request beyond the first must lose the race.

BEGIN;

-- Wipe everything a previous run left behind, in foreign-key order.
-- Leftover intents would trip the duplicate-reservation guard before the
-- inventory is ever reached, silently invalidating the experiment.
DELETE FROM payment_outbox_messages;
DELETE FROM payments;
DELETE FROM inventory_hold_dates;
DELETE FROM inventory_holds;
DELETE FROM active_reservation_attempts;
DELETE FROM reservation_intent_requests;
DELETE FROM reservation_intents;
DELETE FROM reservations;
-- The DIRECT channel is created lazily on first intent, so it also holds a property FK
DELETE FROM channel_mapping_entries;
DELETE FROM channel_mappings;
DELETE FROM processed_webhook_events;
DELETE FROM channels;

DELETE FROM room_inventories WHERE id = 'oplock-inv-1';
DELETE FROM room_types WHERE id = 'oplock-rt-1';
DELETE FROM properties WHERE id = 'oplock-prop-1';
DELETE FROM members WHERE id = 'oplock-owner-1';

INSERT INTO members (
    id, email, password_hash, name, role, status, version, created_at, updated_at
) VALUES (
    'oplock-owner-1', 'oplock-owner@stayops.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'oplock owner', 'OWNER', 'ACTIVE', 0, now(), now()
);

INSERT INTO properties (
    id, owner_id, name, type,
    address_street, address_city, address_state, address_zip_code, address_country,
    contact_phone, contact_email, description, status, timezone, currency,
    version, created_at, updated_at
) VALUES (
    'oplock-prop-1', 'oplock-owner-1', 'Optimistic Lock Hotel', 'HOTEL',
    '1 Test Street', 'Seoul', 'Seoul', '00000', 'KR',
    '010-0000-0000', 'oplock-property@stayops.com', 'concurrency verification', 'ACTIVE',
    'Asia/Seoul', 'KRW', 0, now(), now()
);

INSERT INTO room_types (
    id, property_id, name, description, max_occupancy,
    base_price_amount, base_price_currency, version, created_at, updated_at
) VALUES (
    'oplock-rt-1', 'oplock-prop-1', '디럭스', 'concurrency verification', 2,
    150000.00, 'KRW', 0, now(), now()
);

-- total_count = 1 is the whole point: the expected outcome is arithmetically obvious
INSERT INTO room_inventories (
    id, property_id, room_type_id, inventory_date,
    total_count, reserved_count, blocked_count, held_count,
    version, created_at, updated_at
) VALUES (
    'oplock-inv-1', 'oplock-prop-1', 'oplock-rt-1', DATE '2026-09-01',
    1, 0, 0, 0, 0, now(), now()
);

COMMIT;

SELECT id, inventory_date, total_count, reserved_count, held_count, version
FROM room_inventories
WHERE id = 'oplock-inv-1';
