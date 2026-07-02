alter table payment_outbox_messages
    alter column reservation_id drop not null;

alter table payment_outbox_messages
    add column reservation_intent_id varchar(64);

alter table payment_outbox_messages
    add constraint fk_payment_outbox_reservation_intent foreign key (reservation_intent_id) references reservation_intents (id);
