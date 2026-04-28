export function assertStayWindowWithinInventory({ checkInOffsets, nightsPool, inventoryDays }) {
  if (!Number.isInteger(inventoryDays) || inventoryDays <= 0) {
    throw new Error("LOADTEST_INVENTORY_DAYS must be a positive integer.");
  }
  if (!Array.isArray(checkInOffsets) || checkInOffsets.length === 0) {
    throw new Error("CHECK_IN_OFFSETS must contain at least one offset.");
  }
  if (!Array.isArray(nightsPool) || nightsPool.length === 0) {
    throw new Error("NIGHTS_POOL must contain at least one night count.");
  }

  const maxOffset = Math.max(...checkInOffsets);
  const maxNights = Math.max(...nightsPool);
  if (maxOffset + maxNights > inventoryDays) {
    throw new Error(
      `Stay window exceeds seeded inventory range: maxOffset=${maxOffset}, maxNights=${maxNights}, inventoryDays=${inventoryDays}.`,
    );
  }
}

export function assertStayWindowsDisjoint({ seedStartOffset, seedDaySpan, seedNights = 1, checkInOffsets, nightsPool }) {
  assertPositiveInteger(seedStartOffset, "seedStartOffset");
  assertPositiveInteger(seedDaySpan, "seedDaySpan");
  assertPositiveInteger(seedNights, "seedNights");
  assertNonEmptyArray(checkInOffsets, "checkInOffsets");
  assertNonEmptyArray(nightsPool, "nightsPool");

  const writeKeys = new Set(checkInOffsets.flatMap((offset) => nightsPool.map((nights) => stayKey(offset, nights))));
  for (let index = 0; index < seedDaySpan; index += 1) {
    const seedOffset = seedStartOffset + index;
    const key = stayKey(seedOffset, seedNights);
    if (writeKeys.has(key)) {
      throw new Error(
        `Seed reservation window overlaps k6 write window: checkInOffset=${seedOffset}, nights=${seedNights}. ` +
          "Move LOADTEST_SEED_RESERVATION_START_OFFSET/DAY_SPAN or CHECK_IN_OFFSETS/NIGHTS_POOL.",
      );
    }
  }
}

export function selectStayWindowOffsets({ sequence, checkInOffsets, nightsPool }) {
  if (!Array.isArray(checkInOffsets) || checkInOffsets.length === 0) {
    throw new Error("CHECK_IN_OFFSETS must contain at least one offset.");
  }
  if (!Array.isArray(nightsPool) || nightsPool.length === 0) {
    throw new Error("NIGHTS_POOL must contain at least one night count.");
  }

  const normalizedSequence = Math.max(0, Math.trunc(sequence));
  return {
    checkInOffset: checkInOffsets[normalizedSequence % checkInOffsets.length],
    nights: nightsPool[Math.floor(normalizedSequence / checkInOffsets.length) % nightsPool.length],
  };
}

export function estimateRampingArrivalIterations({ startRate, stages }) {
  if (!Number.isFinite(startRate) || startRate < 0) {
    throw new Error("startRate must be a non-negative number.");
  }
  assertNonEmptyArray(stages, "stages");

  let previousRate = startRate;
  let iterations = 0;
  for (const stage of stages) {
    if (!Number.isFinite(stage.target) || stage.target < 0) {
      throw new Error("stage.target must be a non-negative number.");
    }
    const durationSeconds = parseDurationSeconds(stage.duration);
    iterations += ((previousRate + stage.target) / 2) * durationSeconds;
    previousRate = stage.target;
  }

  return Math.ceil(iterations);
}

export function countScheduledFlowOccurrences(schedule, value, totalIterations) {
  assertNonEmptyArray(schedule, "schedule");
  if (!Number.isInteger(totalIterations) || totalIterations < 0) {
    throw new Error("totalIterations must be a non-negative integer.");
  }

  const occurrencesPerCycle = schedule.filter((item) => item === value).length;
  const fullCycles = Math.floor(totalIterations / schedule.length);
  const remainder = totalIterations % schedule.length;
  const remainderOccurrences = schedule.slice(0, remainder).filter((item) => item === value).length;

  return fullCycles * occurrencesPerCycle + remainderOccurrences;
}

export function buildWeightedFlowSchedule(weightedItems, slotCount = 100) {
  if (!Number.isInteger(slotCount) || slotCount <= 0) {
    throw new Error("slotCount must be a positive integer.");
  }

  const activeItems = weightedItems.filter((item) => item.weight > 0);
  assertNonEmptyArray(activeItems, "weightedItems");

  const totalWeight = activeItems.reduce((sum, item) => sum + item.weight, 0);
  const allocations = activeItems.map((item) => {
    const exactCount = (item.weight / totalWeight) * slotCount;
    return {
      value: item.value,
      count: Math.floor(exactCount),
      remainder: exactCount - Math.floor(exactCount),
    };
  });

  let allocated = allocations.reduce((sum, item) => sum + item.count, 0);
  while (allocated < slotCount) {
    const target = allocations.reduce((best, item) => (item.remainder > best.remainder ? item : best), allocations[0]);
    target.count += 1;
    target.remainder = 0;
    allocated += 1;
  }

  const scheduled = [];
  for (const allocation of allocations) {
    for (let index = 0; index < allocation.count; index += 1) {
      scheduled.push({
        position: (index + 0.5) / allocation.count,
        value: allocation.value,
      });
    }
  }

  return scheduled
    .sort((left, right) => left.position - right.position || String(left.value).localeCompare(String(right.value)))
    .map((item) => item.value);
}

export function selectScheduledFlow(schedule, iterationIndex) {
  assertNonEmptyArray(schedule, "schedule");

  const normalizedIteration = Math.max(0, Math.trunc(iterationIndex));
  const slot = normalizedIteration % schedule.length;
  const cycle = Math.floor(normalizedIteration / schedule.length);
  const value = schedule[slot];

  let occurrenceInCycle = 0;
  let occurrencesPerCycle = 0;
  for (let index = 0; index < schedule.length; index += 1) {
    if (schedule[index] !== value) {
      continue;
    }
    if (index < slot) {
      occurrenceInCycle += 1;
    }
    occurrencesPerCycle += 1;
  }

  return {
    value,
    sequence: cycle * occurrencesPerCycle + occurrenceInCycle,
  };
}

export function buildBookableRoomTypes(roomTypesByPropertyId) {
  const seenRoomTypeIds = new Set();
  const bookableRoomTypes = [];

  for (const propertyId of Object.keys(roomTypesByPropertyId || {})) {
    const roomTypeIds = roomTypesByPropertyId[propertyId];
    if (!Array.isArray(roomTypeIds)) {
      continue;
    }

    for (const roomTypeId of roomTypeIds) {
      if (!propertyId || !roomTypeId || seenRoomTypeIds.has(roomTypeId)) {
        continue;
      }
      seenRoomTypeIds.add(roomTypeId);
      bookableRoomTypes.push({ propertyId, roomTypeId });
    }
  }

  return bookableRoomTypes;
}

export function countUniqueReservationCombinations({ customerSessions, bookableRoomTypes, checkInOffsets, nightsPool }) {
  assertNonEmptyArray(customerSessions, "customerSessions");
  assertNonEmptyArray(bookableRoomTypes, "bookableRoomTypes");
  assertNonEmptyArray(checkInOffsets, "checkInOffsets");
  assertNonEmptyArray(nightsPool, "nightsPool");

  return customerSessions.length * bookableRoomTypes.length * checkInOffsets.length * nightsPool.length;
}

export function assertReservationCapacity({ capacity, requiredCreates, sequenceOffset = 0 }) {
  if (!Number.isInteger(capacity) || capacity < 0) {
    throw new Error("capacity must be a non-negative integer.");
  }
  if (!Number.isInteger(requiredCreates) || requiredCreates < 0) {
    throw new Error("requiredCreates must be a non-negative integer.");
  }
  if (!Number.isInteger(sequenceOffset) || sequenceOffset < 0) {
    throw new Error("sequenceOffset must be a non-negative integer.");
  }

  const requiredEndExclusive = sequenceOffset + requiredCreates;
  if (requiredEndExclusive > capacity) {
    throw new Error(
      `Reservation combination capacity exhausted. requiredEndExclusive=${requiredEndExclusive}, capacity=${capacity}. ` +
        "Increase CUSTOMER_COUNT/HOT_PROPERTY_COUNT/CHECK_IN_OFFSETS/NIGHTS_POOL, lower test rate/duration, or cleanup/reseed.",
    );
  }
}

export function selectUniqueReservationCombination({
  sequence,
  customerSessions,
  bookableRoomTypes,
  checkInOffsets,
  nightsPool,
  guestsPool,
}) {
  const capacity = countUniqueReservationCombinations({
    customerSessions,
    bookableRoomTypes,
    checkInOffsets,
    nightsPool,
  });
  assertNonEmptyArray(guestsPool, "guestsPool");

  const normalizedSequence = Math.trunc(sequence);
  if (normalizedSequence < 0 || normalizedSequence >= capacity) {
    return null;
  }

  const nightsIndex = normalizedSequence % nightsPool.length;
  const checkInIndex = Math.floor(normalizedSequence / nightsPool.length) % checkInOffsets.length;
  const roomTypeIndex = Math.floor(normalizedSequence / (nightsPool.length * checkInOffsets.length)) % bookableRoomTypes.length;
  const customerIndex =
    Math.floor(normalizedSequence / (nightsPool.length * checkInOffsets.length * bookableRoomTypes.length)) %
    customerSessions.length;
  const roomType = bookableRoomTypes[roomTypeIndex];

  return {
    session: customerSessions[customerIndex],
    propertyId: roomType.propertyId,
    roomTypeId: roomType.roomTypeId,
    checkInOffset: checkInOffsets[checkInIndex],
    nights: nightsPool[nightsIndex],
    guests: guestsPool[normalizedSequence % guestsPool.length],
  };
}

export function selectDuplicateReservationPair({
  sequence,
  customerSessions,
  bookableRoomTypes,
  checkInOffsets,
  nightsPool,
  guestsPool,
}) {
  const initial = selectUniqueReservationCombination({
    sequence,
    customerSessions,
    bookableRoomTypes,
    checkInOffsets,
    nightsPool,
    guestsPool,
  });
  if (!initial) {
    return null;
  }

  const duplicateGuestIndex = guestsPool.length > 1 ? (guestsPool.indexOf(initial.guests) + 1) % guestsPool.length : 0;
  return {
    initial,
    duplicate: {
      ...initial,
      guests: guestsPool[duplicateGuestIndex],
    },
  };
}

export function pickUniquePendingReservation(pendingReservations, iterationIndex) {
  if (!Array.isArray(pendingReservations) || pendingReservations.length === 0) {
    return null;
  }

  const index = Math.trunc(iterationIndex);
  if (index < 0 || index >= pendingReservations.length) {
    return null;
  }
  return pendingReservations[index];
}

function assertNonEmptyArray(value, name) {
  if (!Array.isArray(value) || value.length === 0) {
    throw new Error(`${name} must contain at least one item.`);
  }
}

function assertPositiveInteger(value, name) {
  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`${name} must be a positive integer.`);
  }
}

function stayKey(checkInOffset, nights) {
  return `${checkInOffset}:${nights}`;
}

function parseDurationSeconds(duration) {
  if (typeof duration === "number" && Number.isFinite(duration) && duration > 0) {
    return duration;
  }

  const match = typeof duration === "string" ? duration.trim().match(/^(\d+(?:\.\d+)?)(ms|s|m|h)$/) : null;
  if (!match) {
    throw new Error(`Unsupported duration: ${duration}`);
  }

  const value = Number.parseFloat(match[1]);
  const unit = match[2];
  if (unit === "ms") {
    return value / 1000;
  }
  if (unit === "s") {
    return value;
  }
  if (unit === "m") {
    return value * 60;
  }
  return value * 3600;
}
