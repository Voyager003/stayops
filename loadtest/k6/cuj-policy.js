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
