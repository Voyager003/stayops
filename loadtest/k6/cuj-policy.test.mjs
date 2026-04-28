import test from "node:test";
import assert from "node:assert/strict";

import {
  assertStayWindowWithinInventory,
  buildBookableRoomTypes,
  buildWeightedFlowSchedule,
  countUniqueReservationCombinations,
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
