const runId = process.env.LOADTEST_RUN_ID || new Date().toISOString().replace(/[-:.TZ]/g, "").slice(0, 14);
const prefix = process.env.LOADTEST_PREFIX || `loadtest-${runId}`;
const propertyCount = parseInt(process.env.LOADTEST_PROPERTY_COUNT || "50", 10);
const customerCount = parseInt(process.env.LOADTEST_CUSTOMER_COUNT || "100", 10);
const inventoryDays = parseInt(process.env.LOADTEST_INVENTORY_DAYS || "180", 10);
const reservationCount = parseInt(process.env.LOADTEST_RESERVATION_COUNT || "50000", 10);
const passwordHash = process.env.LOADTEST_PASSWORD_HASH || "$2a$10$dXJ3SW6G7P50lGmMQgel3uGqGa7lL2kWGEf5bYLT9hEVwQH3.HkqK";

const database = db.getSiblingDB(process.env.LOADTEST_DB || "stayops");
const now = new Date();
const createdIds = {
  runId,
  prefix,
  propertyCount,
  customerCount,
  inventoryDays,
  reservationCount,
};

function batchInsert(collection, docs, batchSize = 1000) {
  for (let i = 0; i < docs.length; i += batchSize) {
    collection.insertMany(docs.slice(i, i + batchSize), { ordered: false });
  }
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
    version: NumberLong(0),
    createdAt: now,
    updatedAt: now,
    _class: "com.stayops.member.infrastructure.persistence.MemberDocument",
  });
}
for (let i = 1; i <= customerCount; i += 1) {
  customers.push({
    _id: `${prefix}-customer-${String(i).padStart(4, "0")}`,
    email: `loadtest-customer-${String(i).padStart(4, "0")}@example.com`,
    passwordHash,
    name: `Loadtest Customer ${i}`,
    role: "CUSTOMER",
    propertyAccess: [],
    status: "ACTIVE",
    lastLoginAt: null,
    version: NumberLong(0),
    createdAt: now,
    updatedAt: now,
    _class: "com.stayops.member.infrastructure.persistence.MemberDocument",
  });
}
batchInsert(database.members, owners.concat(customers));

const properties = [];
const roomTypes = [];
const rooms = [];
const inventories = [];
const channels = [];
for (let p = 1; p <= propertyCount; p += 1) {
  const propertyNo = String(p).padStart(4, "0");
  const propertyId = `${prefix}-property-${propertyNo}`;
  properties.push({
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
    version: NumberLong(0),
    createdAt: now,
    updatedAt: now,
    _class: "com.stayops.property.infrastructure.persistence.PropertyDocument",
  });

  channels.push({
    _id: `${prefix}-channel-${propertyNo}-direct`,
    propertyId,
    code: "DIRECT",
    name: "Direct",
    type: "DIRECT",
    commissionRate: NumberDecimal("0.0"),
    connectionInfo: null,
    status: "ACTIVE",
    version: NumberLong(0),
    createdAt: now,
    updatedAt: now,
    _class: "com.stayops.channel.infrastructure.persistence.ChannelDocument",
  });

  const roomTypeCount = 3 + (p % 3);
  for (let r = 1; r <= roomTypeCount; r += 1) {
    const roomTypeId = `${prefix}-rt-${propertyNo}-${r}`;
    roomTypes.push({
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
      version: NumberLong(0),
      createdAt: now,
      updatedAt: now,
      _class: "com.stayops.room.infrastructure.persistence.RoomTypeDocument",
    });

    const roomCount = 6 + r;
    for (let room = 1; room <= roomCount; room += 1) {
      rooms.push({
        _id: `${prefix}-room-${propertyNo}-${r}-${room}`,
        propertyId,
        roomTypeId,
        roomNumber: `${r}${String(room).padStart(2, "0")}`,
        floor: r,
        status: "AVAILABLE",
        memo: null,
        version: NumberLong(0),
        createdAt: now,
        updatedAt: now,
        _class: "com.stayops.room.infrastructure.persistence.RoomDocument",
      });
    }

    for (let day = 0; day < inventoryDays; day += 1) {
      const date = ymd(addDays(day));
      inventories.push({
        _id: `${prefix}-inv-${propertyNo}-${r}-${date}`,
        propertyId,
        roomTypeId,
        date,
        totalCount: roomCount,
        reservedCount: 0,
        blockedCount: 0,
        version: NumberLong(0),
        createdAt: now,
        updatedAt: now,
        _class: "com.stayops.inventory.infrastructure.persistence.RoomInventoryDocument",
      });
    }
  }
}
batchInsert(database.properties, properties);
batchInsert(database.channels, channels);
batchInsert(database.room_types, roomTypes);
batchInsert(database.rooms, rooms);
batchInsert(database.room_inventories, inventories);

const guests = [];
const reservations = [];
const payments = [];
for (let i = 1; i <= reservationCount; i += 1) {
  const property = properties[i % properties.length];
  const availableRoomTypes = roomTypes.filter((roomType) => roomType.propertyId === property._id);
  const roomType = availableRoomTypes[i % availableRoomTypes.length];
  const customer = customers[i % customers.length];
  const guestId = `${prefix}-guest-${String(i).padStart(6, "0")}`;
  const reservationId = `${prefix}-reservation-${String(i).padStart(6, "0")}`;
  const paymentId = `${prefix}-payment-${String(i).padStart(6, "0")}`;
  const checkIn = ymd(addDays(3 + (i % Math.max(inventoryDays - 3, 1))));
  const checkOut = ymd(addDays(4 + (i % Math.max(inventoryDays - 3, 1))));
  const amountValue = 90000 + (i % 5) * 20000;
  const amount = NumberDecimal(String(amountValue));

  guests.push({
    _id: guestId,
    propertyId: property._id,
    name: `Loadtest Guest ${i}`,
    phone: `019${String(i).padStart(8, "0").slice(-8)}`,
    email: `${prefix}-guest-${String(i).padStart(6, "0")}@example.com`,
    tier: "NEW",
    memo: null,
    totalVisits: 1,
    totalSpendAmount: NumberLong(amountValue),
    lastVisitDate: checkIn,
    averageStayNights: 1,
    version: NumberLong(0),
    createdAt: now,
    updatedAt: now,
    _class: "com.stayops.guest.infrastructure.persistence.GuestDocument",
  });

  reservations.push({
    _id: reservationId,
    propertyId: property._id,
    roomTypeId: roomType._id,
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
    memberId: customer._id,
    expiresAt: i % 4 === 0 ? addDays(1) : null,
    version: NumberLong(0),
    createdAt: now,
    updatedAt: now,
    _class: "com.stayops.reservation.infrastructure.persistence.ReservationDocument",
  });

  payments.push({
    _id: paymentId,
    reservationId,
    memberId: customer._id,
    orderId: `STAYOPS-${reservationId}-${now.getTime()}`,
    amount,
    currency: "KRW",
    status: i % 4 === 0 ? "PENDING" : "APPROVED",
    paymentKey: i % 4 === 0 ? null : `${prefix}-payment-key-${String(i).padStart(6, "0")}`,
    method: i % 4 === 0 ? null : "CARD",
    failReason: null,
    approvedAt: i % 4 === 0 ? null : now,
    version: NumberLong(0),
    createdAt: now,
    updatedAt: now,
    _class: "com.stayops.payment.infrastructure.persistence.PaymentDocument",
  });
}
batchInsert(database.guests, guests);
batchInsert(database.reservations, reservations);
batchInsert(database.payments, payments);

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
print(`Customer emails: loadtest-customer-0001@example.com ... loadtest-customer-${String(customerCount).padStart(4, "0")}@example.com`);
