create table reservation_intent_requests (
    member_id varchar(64) not null,
    idempotency_key varchar(160) not null,
    request_hash char(64) not null,
    reservation_intent_id varchar(64) not null,
    created_at timestamp with time zone not null,
    primary key (member_id, idempotency_key),
    constraint uq_reservation_intent_requests_intent unique (reservation_intent_id),
    constraint fk_reservation_intent_requests_member foreign key (member_id) references members (id),
    constraint fk_reservation_intent_requests_intent foreign key (reservation_intent_id) references reservation_intents (id)
);
