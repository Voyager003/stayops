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
