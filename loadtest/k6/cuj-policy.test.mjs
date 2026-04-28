import test from "node:test";
import assert from "node:assert/strict";

import {
  assertStayWindowWithinInventory,
  pickUniquePendingReservation,
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
