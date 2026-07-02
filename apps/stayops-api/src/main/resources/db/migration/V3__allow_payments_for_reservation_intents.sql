alter table payments
    alter column reservation_id drop not null;

alter table payments
    add column reservation_intent_id varchar(64);

alter table payments
    add constraint fk_payments_reservation_intent foreign key (reservation_intent_id) references reservation_intents (id);
