create table active_reservation_attempts (
    booking_key char(64) primary key,
    reservation_intent_id varchar(64) not null,
    member_id varchar(64) not null,
    expires_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    constraint uq_active_reservation_attempts_intent unique (reservation_intent_id),
    constraint fk_active_reservation_attempts_intent foreign key (reservation_intent_id) references reservation_intents (id),
    constraint fk_active_reservation_attempts_member foreign key (member_id) references members (id)
);
