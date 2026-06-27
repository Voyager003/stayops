alter table room_inventories
    add column held_count integer not null default 0;

create table reservation_intents (
    id varchar(64) primary key,
    member_id varchar(64) not null,
    property_id varchar(64) not null,
    room_type_id varchar(64) not null,
    guest_name varchar(120) not null,
    guest_phone varchar(80) not null,
    guest_email varchar(255),
    check_in date not null,
    check_out date not null,
    night_count integer not null,
    number_of_guests integer not null,
    channel_code varchar(80) not null,
    external_reservation_id varchar(120),
    commission_rate numeric(8, 5) not null,
    room_rate_amount numeric(19, 2) not null,
    room_rate_currency varchar(3) not null,
    additional_charges_amount numeric(19, 2) not null,
    total_amount numeric(19, 2) not null,
    commission_amount numeric(19, 2) not null,
    net_amount numeric(19, 2) not null,
    payment_id varchar(64) not null,
    hold_id varchar(64) not null,
    reservation_id varchar(64),
    status varchar(40) not null,
    expires_at timestamp with time zone not null,
    version bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_reservation_intents_member foreign key (member_id) references members (id),
    constraint fk_reservation_intents_property foreign key (property_id) references properties (id),
    constraint fk_reservation_intents_room_type foreign key (room_type_id) references room_types (id),
    constraint fk_reservation_intents_reservation foreign key (reservation_id) references reservations (id)
);

create table inventory_holds (
    id varchar(64) primary key,
    reservation_intent_id varchar(64) not null,
    property_id varchar(64) not null,
    room_type_id varchar(64) not null,
    quantity integer not null,
    status varchar(40) not null,
    expires_at timestamp with time zone not null,
    version bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_inventory_holds_reservation_intent foreign key (reservation_intent_id) references reservation_intents (id),
    constraint fk_inventory_holds_property foreign key (property_id) references properties (id),
    constraint fk_inventory_holds_room_type foreign key (room_type_id) references room_types (id)
);

create table inventory_hold_dates (
    hold_id varchar(64) not null,
    hold_date date not null,
    quantity integer not null,
    primary key (hold_id, hold_date),
    constraint fk_inventory_hold_dates_hold foreign key (hold_id) references inventory_holds (id)
);
