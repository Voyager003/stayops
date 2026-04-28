import test from "node:test";
import assert from "node:assert/strict";

import {
  assertStayWindowWithinInventory,
  buildBookableRoomTypes,
  buildWeightedFlowSchedule,
  countUniqueReservationCombinations,
  assertReservationCapacity,
  assertStayWindowsDisjoint,
  countScheduledFlowOccurrences,
  estimateRampingArrivalIterations,
  pickUniquePendingReservation,
  selectDuplicateReservationPair,
  selectScheduledFlow,
  selectUniqueReservationCombination,
  selectStayWindowOffsets,
} from "./cuj-policy.js";

test("assertStayWindowWithinInventory accepts stays fully covered by seeded inventory", () => {
  assert.doesNotThrow(() =>
    assertStayWindowWithinInventory({
      checkInOffsets: [3, 5, 7, 14, 21, 30, 45],
      nightsPool: [1, 2, 3],
      inventoryDays: 60,
    }),
  );
});

test("assertStayWindowWithinInventory rejects stays outside seeded inventory", () => {
  assert.throws(
    () =>
      assertStayWindowWithinInventory({
        checkInOffsets: [3, 60],
        nightsPool: [1, 2, 3],
        inventoryDays: 60,
      }),
    /exceeds seeded inventory range/,
  );
});

test("assertStayWindowsDisjoint accepts seed reservations outside the k6 write window", () => {
  assert.doesNotThrow(() =>
    assertStayWindowsDisjoint({
      seedStartOffset: 50,
      seedDaySpan: 8,
      seedNights: 1,
      checkInOffsets: [3, 5, 7, 14, 21, 30, 45],
      nightsPool: [1, 2, 3],
    }),
  );
});

test("assertStayWindowsDisjoint rejects seed reservations that occupy k6 write slots", () => {
  assert.throws(
    () =>
      assertStayWindowsDisjoint({
        seedStartOffset: 45,
        seedDaySpan: 4,
        seedNights: 1,
        checkInOffsets: [3, 5, 7, 14, 21, 30, 45],
        nightsPool: [1, 2, 3],
      }),
    /overlaps k6 write window/,
  );
});

test("selectStayWindowOffsets cycles inside the configured safe offsets", () => {
  assert.deepEqual(
    [0, 1, 2, 3, 4, 5].map((sequence) =>
      selectStayWindowOffsets({
        sequence,
        checkInOffsets: [3, 5, 7],
        nightsPool: [1, 2],
      }),
    ),
    [
      { checkInOffset: 3, nights: 1 },
      { checkInOffset: 5, nights: 1 },
      { checkInOffset: 7, nights: 1 },
      { checkInOffset: 3, nights: 2 },
      { checkInOffset: 5, nights: 2 },
      { checkInOffset: 7, nights: 2 },
    ],
  );
});

test("pickUniquePendingReservation never returns the same pending reservation twice for in-range iterations", () => {
  const pending = [
    { reservationId: "pending-1" },
    { reservationId: "pending-2" },
  ];

  assert.equal(pickUniquePendingReservation(pending, 0).reservationId, "pending-1");
  assert.equal(pickUniquePendingReservation(pending, 1).reservationId, "pending-2");
  assert.equal(pickUniquePendingReservation(pending, 2), null);
});

test("buildBookableRoomTypes flattens property room types and keeps roomTypeId unique", () => {
  assert.deepEqual(
    buildBookableRoomTypes({
      "property-1": ["rt-1", "rt-2"],
      "property-2": ["rt-2", "rt-3"],
      "property-3": [],
    }),
    [
      { propertyId: "property-1", roomTypeId: "rt-1" },
      { propertyId: "property-1", roomTypeId: "rt-2" },
      { propertyId: "property-2", roomTypeId: "rt-3" },
    ],
  );
});

test("selectUniqueReservationCombination emits unique duplicate-check keys until capacity is exhausted", () => {
  const customerSessions = [{ email: "c1" }, { email: "c2" }];
  const bookableRoomTypes = [
    { propertyId: "p1", roomTypeId: "rt-1" },
    { propertyId: "p1", roomTypeId: "rt-2" },
  ];
  const checkInOffsets = [3, 5, 7];
  const nightsPool = [1, 2];
  const guestsPool = [1, 2, 3];
  const capacity = countUniqueReservationCombinations({
    customerSessions,
    bookableRoomTypes,
    checkInOffsets,
    nightsPool,
  });

  assert.equal(capacity, 24);

  const seen = new Set();
  for (let sequence = 0; sequence < capacity; sequence += 1) {
    const combination = selectUniqueReservationCombination({
      sequence,
      customerSessions,
      bookableRoomTypes,
      checkInOffsets,
      nightsPool,
      guestsPool,
    });
    const key = `${combination.session.email}/${combination.roomTypeId}/${combination.checkInOffset}/${combination.nights}`;
    assert.equal(seen.has(key), false, `duplicate key emitted: ${key}`);
    seen.add(key);
  }

  assert.equal(
    selectUniqueReservationCombination({
      sequence: capacity,
      customerSessions,
      bookableRoomTypes,
      checkInOffsets,
      nightsPool,
      guestsPool,
    }),
    null,
  );
});

test("selectScheduledFlow returns compact per-flow sequences without spending slots on other flows", () => {
  const schedule = buildWeightedFlowSchedule(
    [
      { weight: 0.5, value: "read" },
      { weight: 0.25, value: "createReservation" },
      { weight: 0.25, value: "pmsList" },
    ],
    8,
  );

  const createSequences = [];
  for (let iterationIndex = 0; iterationIndex < 16; iterationIndex += 1) {
    const selected = selectScheduledFlow(schedule, iterationIndex);
    if (selected.value === "createReservation") {
      createSequences.push(selected.sequence);
    }
  }

  assert.deepEqual(createSequences, [0, 1, 2, 3]);
});

test("estimateRampingArrivalIterations calculates planned baseline iterations", () => {
  assert.equal(
    estimateRampingArrivalIterations({
      startRate: 1,
      stages: [
        { target: 10, duration: "2m" },
        { target: 10, duration: "10m" },
        { target: 0, duration: "1m" },
      ],
    }),
    6960,
  );
});

test("countScheduledFlowOccurrences counts planned create flows from the deterministic schedule", () => {
  assert.equal(countScheduledFlowOccurrences(["search", "createReservation", "detail", "createReservation"], "createReservation", 10), 5);
});

test("assertReservationCapacity rejects plans that would reuse success reservation slots", () => {
  assert.throws(
    () =>
      assertReservationCapacity({
        capacity: 10,
        requiredCreates: 6,
        sequenceOffset: 5,
      }),
    /Reservation combination capacity exhausted/,
  );
});

test("default-sized synthetic data has enough success slots for breakpoint and overload plans", () => {
  const defaultCheckInOffsets = Array.from({ length: 43 }, (_, index) => index + 3);
  const capacity = countUniqueReservationCombinations({
    customerSessions: Array.from({ length: 30 }, (_, index) => ({ email: `c${index}` })),
    bookableRoomTypes: Array.from({ length: 40 }, (_, index) => ({ propertyId: `p${index}`, roomTypeId: `rt${index}` })),
    checkInOffsets: defaultCheckInOffsets,
    nightsPool: [1, 2, 3],
  });
  const breakpointIterations = estimateRampingArrivalIterations({
    startRate: 20,
    stages: [
      { target: 20, duration: "5m" },
      { target: 40, duration: "5m" },
      { target: 80, duration: "5m" },
      { target: 120, duration: "5m" },
      { target: 160, duration: "5m" },
      { target: 220, duration: "5m" },
      { target: 0, duration: "1m" },
    ],
  });
  const overloadIterations = estimateRampingArrivalIterations({
    startRate: 160,
    stages: [
      { target: 160, duration: "3m" },
      { target: 240, duration: "3m" },
      { target: 320, duration: "3m" },
      { target: 480, duration: "3m" },
      { target: 0, duration: "1m" },
    ],
  });
  const schedule = buildWeightedFlowSchedule(
    [
      { weight: 0.2, value: "search" },
      { weight: 0.15, value: "detail" },
      { weight: 0.2, value: "offers" },
      { weight: 0.2, value: "createReservation" },
      { weight: 0.1, value: "myReservations" },
      { weight: 0.12, value: "pmsList" },
    ],
    100,
  );

  assertReservationCapacity({
    capacity,
    requiredCreates: countScheduledFlowOccurrences(schedule, "createReservation", breakpointIterations),
  });
  assertReservationCapacity({
    capacity,
    requiredCreates: countScheduledFlowOccurrences(schedule, "createReservation", overloadIterations),
  });
});

test("selectDuplicateReservationPair reuses the duplicate-check key for the second create request", () => {
  const pair = selectDuplicateReservationPair({
    sequence: 0,
    customerSessions: [{ email: "c1" }],
    bookableRoomTypes: [{ propertyId: "p1", roomTypeId: "rt-1" }],
    checkInOffsets: [3],
    nightsPool: [2],
    guestsPool: [1, 2],
  });

  assert.equal(pair.initial.session.email, pair.duplicate.session.email);
  assert.equal(pair.initial.propertyId, pair.duplicate.propertyId);
  assert.equal(pair.initial.roomTypeId, pair.duplicate.roomTypeId);
  assert.equal(pair.initial.checkInOffset, pair.duplicate.checkInOffset);
  assert.equal(pair.initial.nights, pair.duplicate.nights);
  assert.notEqual(pair.initial.guests, pair.duplicate.guests);
});
