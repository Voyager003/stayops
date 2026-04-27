const runId = process.env.LOADTEST_RUN_ID || new Date().toISOString().replace(/[-:.TZ]/g, "").slice(0, 14);
const prefix = process.env.LOADTEST_PREFIX || `loadtest-${runId}`;
const propertyCount = parseInt(process.env.LOADTEST_PROPERTY_COUNT || "10", 10);
const customerCount = parseInt(process.env.LOADTEST_CUSTOMER_COUNT || "30", 10);
const inventoryDays = parseInt(process.env.LOADTEST_INVENTORY_DAYS || "60", 10);
const reservationCount = parseInt(process.env.LOADTEST_RESERVATION_COUNT || "5000", 10);
const batchSize = parseInt(process.env.LOADTEST_BATCH_SIZE || "500", 10);
const passwordHash = process.env.LOADTEST_PASSWORD_HASH || "$2a$10$n2sm6oAyceX3Q5cwTG05Je9k6rtLUbrX1s.cHmEeIEg59DuRs0AFu";

const database = db.getSiblingDB(process.env.LOADTEST_DB || "stayops");
const now = new Date();
const createdIds = {
  runId,
  prefix,
  propertyCount,
  customerCount,
  inventoryDays,
  reservationCount,
  batchSize,
};

function insertBatch(collection, docs) {
  if (docs.length === 0) {
    return;
  }
  collection.insertMany(docs, { ordered: false });
  docs.length = 0;
}

function pushAndFlush(collection, docs, doc) {
  docs.push(doc);
  if (docs.length >= batchSize) {
    insertBatch(collection, docs);
  }
}

function long(value) {
  return NumberLong(String(value));
}

function ymd(date) {
  return date.toISOString().slice(0, 10);
}

function addDays(days) {
  const date = new Date(now);
  date.setUTCDate(date.getUTCDate() + days);
  return date;
}

print(`Seeding load-test synthetic data: ${JSON.stringify(createdIds)}`);

const owners = [];
const customers = [];
for (let i = 1; i <= propertyCount; i += 1) {
  owners.push({
    _id: `${prefix}-owner-${String(i).padStart(4, "0")}`,
    email: `${prefix}-owner-${String(i).padStart(4, "0")}@example.com`,
    passwordHash,
    name: `Loadtest Owner ${i}`,
    role: "OWNER",
    propertyAccess: [{ propertyId: `${prefix}-property-${String(i).padStart(4, "0")}`, role: "OWNER" }],
    status: "ACTIVE",
    lastLoginAt: null,
    version: long(0),
    createdAt: now,
    updatedAt: now,
    _class: "com.stayops.member.infrastructure.persistence.MemberDocument",
  });
}
for (let i = 1; i <= customerCount; i += 1) {
  customers.push({
    _id: `${prefix}-customer-${String(i).padStart(4, "0")}`,
    email: `${prefix}-customer-${String(i).padStart(4, "0")}@example.com`,
    passwordHash,
    name: `Loadtest Customer ${i}`,
    role: "CUSTOMER",
    propertyAccess: [],
    status: "ACTIVE",
    lastLoginAt: null,
    version: long(0),
    createdAt: now,
    updatedAt: now,
    _class: "com.stayops.member.infrastructure.persistence.MemberDocument",
  });
}
insertBatch(database.members, owners);
insertBatch(database.members, customers);

const properties = [];
const roomTypes = [];
const rooms = [];
const inventories = [];
const channels = [];
const propertySummaries = [];
for (let p = 1; p <= propertyCount; p += 1) {
  const propertyNo = String(p).padStart(4, "0");
  const propertyId = `${prefix}-property-${propertyNo}`;
  pushAndFlush(database.properties, properties, {
    _id: propertyId,
    ownerId: `${prefix}-owner-${propertyNo}`,
    name: `Loadtest Hotel ${propertyNo}`,
    type: "HOTEL",
    address: {
      street: `Loadtest-ro ${p}`,
      city: p % 2 === 0 ? "서울" : "부산",
      state: p % 2 === 0 ? "강남구" : "해운대구",
      zipCode: `9${String(p).padStart(4, "0")}`,
      country: "KR",
    },
    contactInfo: {
      phone: `02-${String(p).padStart(4, "0")}-0000`,
      email: `${prefix}-property-${propertyNo}@example.com`,
      website: null,
    },
    description: "Synthetic property for StayOps load testing",
    status: "ACTIVE",
    timezone: "Asia/Seoul",
    currency: "KRW",
    version: long(0),
    createdAt: now,
    updatedAt: now,
    _class: "com.stayops.property.infrastructure.persistence.PropertyDocument",
  });

  pushAndFlush(database.channels, channels, {
    _id: `${prefix}-channel-${propertyNo}-direct`,
    propertyId,
    code: "DIRECT",
    name: "Direct",
    type: "DIRECT",
    commissionRate: NumberDecimal("0.0"),
    connectionInfo: null,
    status: "ACTIVE",
    version: long(0),
    createdAt: now,
    updatedAt: now,
    _class: "com.stayops.channel.infrastructure.persistence.ChannelDocument",
  });

  const roomTypeCount = 3 + (p % 3);
  const summaryRoomTypeIds = [];
  for (let r = 1; r <= roomTypeCount; r += 1) {
    const roomTypeId = `${prefix}-rt-${propertyNo}-${r}`;
    summaryRoomTypeIds.push(roomTypeId);
    pushAndFlush(database.room_types, roomTypes, {
      _id: roomTypeId,
      propertyId,
      name: `Loadtest RoomType ${r}`,
      description: "Synthetic room type",
      maxOccupancy: 2 + (r % 3),
      basePrice: {
        amount: NumberDecimal(String(70000 + r * 20000)),
        currency: "KRW",
      },
      amenities: ["Wi-Fi", "TV", "AC"],
      version: long(0),
      createdAt: now,
      updatedAt: now,
      _class: "com.stayops.room.infrastructure.persistence.RoomTypeDocument",
    });

    const roomCount = 6 + r;
    for (let room = 1; room <= roomCount; room += 1) {
      pushAndFlush(database.rooms, rooms, {
        _id: `${prefix}-room-${propertyNo}-${r}-${room}`,
        propertyId,
        roomTypeId,
        roomNumber: `${r}${String(room).padStart(2, "0")}`,
        floor: r,
        status: "AVAILABLE",
        memo: null,
        version: long(0),
        createdAt: now,
        updatedAt: now,
        _class: "com.stayops.room.infrastructure.persistence.RoomDocument",
      });
    }

    for (let day = 0; day < inventoryDays; day += 1) {
      const date = ymd(addDays(day));
      pushAndFlush(database.room_inventories, inventories, {
        _id: `${prefix}-inv-${propertyNo}-${r}-${date}`,
        propertyId,
        roomTypeId,
        date,
        totalCount: roomCount,
        reservedCount: 0,
        blockedCount: 0,
        version: long(0),
        createdAt: now,
        updatedAt: now,
        _class: "com.stayops.inventory.infrastructure.persistence.RoomInventoryDocument",
      });
    }
  }
  propertySummaries.push({ id: propertyId, roomTypeIds: summaryRoomTypeIds });
}
insertBatch(database.properties, properties);
insertBatch(database.channels, channels);
insertBatch(database.room_types, roomTypes);
insertBatch(database.rooms, rooms);
insertBatch(database.room_inventories, inventories);

const guests = [];
const reservations = [];
const payments = [];
for (let i = 1; i <= reservationCount; i += 1) {
  const property = propertySummaries[i % propertySummaries.length];
  const roomTypeId = property.roomTypeIds[i % property.roomTypeIds.length];
  const customerNo = String((i % customerCount) + 1).padStart(4, "0");
  const customerId = `${prefix}-customer-${customerNo}`;
  const guestId = `${prefix}-guest-${String(i).padStart(6, "0")}`;
  const reservationId = `${prefix}-reservation-${String(i).padStart(6, "0")}`;
  const paymentId = `${prefix}-payment-${String(i).padStart(6, "0")}`;
  const checkIn = ymd(addDays(3 + (i % Math.max(inventoryDays - 3, 1))));
  const checkOut = ymd(addDays(4 + (i % Math.max(inventoryDays - 3, 1))));
  const amountValue = 90000 + (i % 5) * 20000;
  const amount = NumberDecimal(String(amountValue));

  pushAndFlush(database.guests, guests, {
    _id: guestId,
    propertyId: property.id,
    name: `Loadtest Guest ${i}`,
    phone: `019${String(i).padStart(8, "0").slice(-8)}`,
    email: `${prefix}-guest-${String(i).padStart(6, "0")}@example.com`,
    tier: "NEW",
    memo: null,
    totalVisits: 1,
    totalSpendAmount: long(amountValue),
    lastVisitDate: checkIn,
    averageStayNights: 1,
    version: long(0),
    createdAt: now,
    updatedAt: now,
    _class: "com.stayops.guest.infrastructure.persistence.GuestDocument",
  });

  pushAndFlush(database.reservations, reservations, {
    _id: reservationId,
    propertyId: property.id,
    roomTypeId,
    roomId: null,
    guestId,
    guestInfo: {
      name: `Loadtest Guest ${i}`,
      phone: `019${String(i).padStart(8, "0").slice(-8)}`,
      email: `${prefix}-guest-${String(i).padStart(6, "0")}@example.com`,
    },
    dateRange: { checkIn, checkOut },
    nightCount: 1,
    numberOfGuests: 1 + (i % 4),
    status: i % 4 === 0 ? "PENDING" : "CONFIRMED",
    channel: {
      channelCode: "DIRECT",
      externalReservationId: null,
      commissionRate: NumberDecimal("0.0"),
    },
    pricing: {
      roomRateAmount: amount,
      roomRateCurrency: "KRW",
      additionalChargesAmount: NumberDecimal("0"),
      totalAmount: amount,
      commissionAmount: NumberDecimal("0"),
      netAmount: amount,
    },
    memberId: customerId,
    expiresAt: i % 4 === 0 ? addDays(1) : null,
    version: long(0),
    createdAt: now,
    updatedAt: now,
    _class: "com.stayops.reservation.infrastructure.persistence.ReservationDocument",
  });

  pushAndFlush(database.payments, payments, {
    _id: paymentId,
    reservationId,
    memberId: customerId,
    orderId: `STAYOPS-${reservationId}-${now.getTime()}`,
    amount,
    currency: "KRW",
    status: i % 4 === 0 ? "PENDING" : "APPROVED",
    paymentKey: i % 4 === 0 ? null : `${prefix}-payment-key-${String(i).padStart(6, "0")}`,
    method: i % 4 === 0 ? null : "CARD",
    failReason: null,
    approvedAt: i % 4 === 0 ? null : now,
    version: long(0),
    createdAt: now,
    updatedAt: now,
    _class: "com.stayops.payment.infrastructure.persistence.PaymentDocument",
  });

  if (i % (batchSize * 10) === 0) {
    print(`Seed progress: reservations/payments ${i}/${reservationCount}`);
  }
}
insertBatch(database.guests, guests);
insertBatch(database.reservations, reservations);
insertBatch(database.payments, payments);

database.loadtest_runs.insertOne({
  _id: prefix,
  runId,
  prefix,
  propertyCount,
  customerCount,
  inventoryDays,
  reservationCount,
  createdAt: now,
});

print(`Load-test seed complete. prefix=${prefix}`);
print(`Customer emails: ${prefix}-customer-0001@example.com ... ${prefix}-customer-${String(customerCount).padStart(4, "0")}@example.com`);
print(`Owner emails: ${prefix}-owner-0001@example.com ... ${prefix}-owner-${String(propertyCount).padStart(4, "0")}@example.com`);
