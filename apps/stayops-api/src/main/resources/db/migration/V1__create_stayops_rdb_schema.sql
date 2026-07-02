create table members (
    id varchar(64) primary key,
    email varchar(255) not null,
    password_hash varchar(255) not null,
    name varchar(120) not null,
    role varchar(40) not null,
    status varchar(40) not null,
    last_login_at timestamp with time zone,
    version bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table properties (
    id varchar(64) primary key,
    owner_id varchar(64) not null,
    name varchar(200) not null,
    type varchar(40) not null,
    address_street varchar(255) not null,
    address_city varchar(120) not null,
    address_state varchar(120) not null,
    address_zip_code varchar(40) not null,
    address_country varchar(80) not null,
    address_latitude double precision,
    address_longitude double precision,
    contact_phone varchar(80) not null,
    contact_email varchar(255) not null,
    contact_website varchar(255),
    description text not null,
    status varchar(40) not null,
    timezone varchar(80) not null,
    currency varchar(3) not null,
    version bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_properties_owner foreign key (owner_id) references members (id)
);

create table member_property_accesses (
    member_id varchar(64) not null,
    property_id varchar(64) not null,
    role varchar(40) not null,
    primary key (member_id, property_id),
    constraint fk_member_property_accesses_member foreign key (member_id) references members (id),
    constraint fk_member_property_accesses_property foreign key (property_id) references properties (id)
);

create table guests (
    id varchar(64) primary key,
    property_id varchar(64) not null,
    name varchar(120) not null,
    phone varchar(80) not null,
    email varchar(255),
    tier varchar(40) not null,
    memo text,
    total_visits integer not null,
    total_spend_amount bigint not null,
    last_visit_date date,
    average_stay_nights double precision not null,
    version bigint,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_guests_property foreign key (property_id) references properties (id)
);

create table room_types (
    id varchar(64) primary key,
    property_id varchar(64) not null,
    name varchar(120) not null,
    description text not null,
    max_occupancy integer not null,
    base_price_amount numeric(19, 2) not null,
    base_price_currency varchar(3) not null,
    version bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_room_types_property foreign key (property_id) references properties (id)
);

create table room_type_amenities (
    room_type_id varchar(64) not null,
    amenity varchar(120) not null,
    primary key (room_type_id, amenity),
    constraint fk_room_type_amenities_room_type foreign key (room_type_id) references room_types (id)
);

create table rooms (
    id varchar(64) primary key,
    property_id varchar(64) not null,
    room_type_id varchar(64) not null,
    room_number varchar(40) not null,
    floor integer not null,
    status varchar(40) not null,
    memo text,
    version bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_rooms_property foreign key (property_id) references properties (id),
    constraint fk_rooms_room_type foreign key (room_type_id) references room_types (id)
);

create table room_inventories (
    id varchar(64) primary key,
    property_id varchar(64) not null,
    room_type_id varchar(64) not null,
    inventory_date date not null,
    total_count integer not null,
    reserved_count integer not null,
    blocked_count integer not null,
    version bigint,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_room_inventories_property foreign key (property_id) references properties (id),
    constraint fk_room_inventories_room_type foreign key (room_type_id) references room_types (id)
);

create table rate_plans (
    id varchar(64) primary key,
    property_id varchar(64) not null,
    room_type_id varchar(64) not null,
    name varchar(120) not null,
    type varchar(40) not null,
    date_range_start date,
    date_range_end date,
    channel_code varchar(80),
    price_amount bigint not null,
    priority integer not null,
    status varchar(40) not null,
    version bigint,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_rate_plans_property foreign key (property_id) references properties (id),
    constraint fk_rate_plans_room_type foreign key (room_type_id) references room_types (id)
);

create table rate_plan_day_of_week_rules (
    rate_plan_id varchar(64) not null,
    day_of_week varchar(20) not null,
    price_amount bigint not null,
    primary key (rate_plan_id, day_of_week),
    constraint fk_rate_plan_day_rules_rate_plan foreign key (rate_plan_id) references rate_plans (id)
);

create table channels (
    id varchar(64) primary key,
    property_id varchar(64) not null,
    code varchar(80) not null,
    name varchar(120) not null,
    type varchar(40) not null,
    commission_rate numeric(8, 5) not null,
    api_endpoint varchar(500),
    status varchar(40) not null,
    version bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_channels_property foreign key (property_id) references properties (id)
);

create table channel_mappings (
    id varchar(64) primary key,
    property_id varchar(64) not null,
    channel_code varchar(80) not null,
    version bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_channel_mappings_property foreign key (property_id) references properties (id)
);

create table channel_mapping_entries (
    channel_mapping_id varchar(64) not null,
    internal_id varchar(64) not null,
    external_code varchar(120) not null,
    type varchar(40) not null,
    primary key (channel_mapping_id, internal_id, type),
    constraint fk_channel_mapping_entries_mapping foreign key (channel_mapping_id) references channel_mappings (id)
);

create table reservations (
    id varchar(64) primary key,
    property_id varchar(64) not null,
    room_type_id varchar(64) not null,
    room_id varchar(64),
    guest_id varchar(64) not null,
    guest_name varchar(120) not null,
    guest_phone varchar(80) not null,
    guest_email varchar(255),
    check_in date not null,
    check_out date not null,
    night_count integer not null,
    number_of_guests integer not null,
    status varchar(40) not null,
    channel_code varchar(80) not null,
    external_reservation_id varchar(120),
    commission_rate numeric(8, 5) not null,
    room_rate_amount numeric(19, 2) not null,
    room_rate_currency varchar(3) not null,
    additional_charges_amount numeric(19, 2) not null,
    total_amount numeric(19, 2) not null,
    commission_amount numeric(19, 2) not null,
    net_amount numeric(19, 2) not null,
    member_id varchar(64),
    expires_at timestamp with time zone,
    version bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_reservations_property foreign key (property_id) references properties (id),
    constraint fk_reservations_room_type foreign key (room_type_id) references room_types (id),
    constraint fk_reservations_room foreign key (room_id) references rooms (id),
    constraint fk_reservations_guest foreign key (guest_id) references guests (id),
    constraint fk_reservations_member foreign key (member_id) references members (id)
);

create table payments (
    id varchar(64) primary key,
    reservation_id varchar(64) not null,
    member_id varchar(64) not null,
    order_id varchar(120) not null,
    amount numeric(19, 2) not null,
    currency varchar(3) not null,
    status varchar(40) not null,
    payment_key varchar(200),
    method varchar(80),
    fail_reason text,
    approved_at timestamp with time zone,
    version bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_payments_reservation foreign key (reservation_id) references reservations (id),
    constraint fk_payments_member foreign key (member_id) references members (id)
);

create table payment_outbox_messages (
    id varchar(64) primary key,
    payment_id varchar(64) not null,
    reservation_id varchar(64) not null,
    member_id varchar(64) not null,
    type varchar(40) not null,
    payment_key varchar(200) not null,
    order_id varchar(120) not null,
    amount numeric(19, 2) not null,
    currency varchar(3) not null,
    cancel_reason text,
    idempotency_key varchar(160) not null,
    status varchar(40) not null,
    retry_count integer not null,
    max_retries integer not null,
    next_retry_at timestamp with time zone,
    locked_by varchar(120),
    locked_until timestamp with time zone,
    last_error text,
    version bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_payment_outbox_messages_payment foreign key (payment_id) references payments (id),
    constraint fk_payment_outbox_messages_reservation foreign key (reservation_id) references reservations (id),
    constraint fk_payment_outbox_messages_member foreign key (member_id) references members (id)
);

create table processed_payment_webhook_events (
    id varchar(64) primary key,
    transmission_id varchar(160) not null,
    event_type varchar(120) not null,
    payment_key varchar(200) not null,
    order_id varchar(120) not null,
    processed_at timestamp with time zone not null
);

create table sync_tasks (
    id varchar(64) primary key,
    property_id varchar(64) not null,
    channel_code varchar(80) not null,
    type varchar(40) not null,
    payload jsonb not null,
    idempotency_key varchar(160) not null,
    status varchar(40) not null,
    retry_count integer not null,
    max_retries integer not null,
    next_retry_at timestamp with time zone,
    locked_by varchar(120),
    locked_until timestamp with time zone,
    last_error text,
    version bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_sync_tasks_property foreign key (property_id) references properties (id)
);

create table processed_webhook_events (
    id varchar(64) primary key,
    event_id varchar(160) not null,
    channel_code varchar(80) not null,
    property_id varchar(64) not null,
    processed_at timestamp with time zone not null,
    constraint fk_processed_webhook_events_property foreign key (property_id) references properties (id)
);

create table scheduler_locks (
    name varchar(120) primary key,
    locked_by varchar(120) not null,
    locked_until timestamp with time zone not null,
    updated_at timestamp with time zone not null
);
