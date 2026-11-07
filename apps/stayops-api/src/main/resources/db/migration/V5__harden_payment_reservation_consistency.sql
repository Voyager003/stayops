create unique index uq_payments_reservation_intent
    on payments (reservation_intent_id)
    where reservation_intent_id is not null;

create unique index uq_inventory_holds_reservation_intent
    on inventory_holds (reservation_intent_id);

create unique index uq_payment_outbox_payment_type
    on payment_outbox_messages (payment_id, type);

create unique index uq_payment_outbox_idempotency_key
    on payment_outbox_messages (idempotency_key);

create index idx_payment_outbox_pending_ready
    on payment_outbox_messages (next_retry_at, created_at, id)
    where status = 'PENDING';

create index idx_payment_outbox_expired_lease
    on payment_outbox_messages (locked_until, created_at, id)
    where status = 'IN_PROGRESS';

alter table room_inventories
    add constraint ck_room_inventories_non_negative_counts
        check (
            total_count >= 0
            and reserved_count >= 0
            and blocked_count >= 0
            and held_count >= 0
        );

alter table room_inventories
    add constraint ck_room_inventories_capacity
        check (reserved_count + blocked_count + held_count <= total_count);
