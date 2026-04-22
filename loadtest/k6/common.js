export const BASE_URL = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/$/, "");

export function envInt(name, fallback) {
  const raw = __ENV[name];
  if (raw === undefined || raw === "") {
    return fallback;
  }
  const value = Number.parseInt(raw, 10);
  if (Number.isNaN(value)) {
    throw new Error(`Environment variable ${name} must be an integer.`);
  }
  return value;
}

export function envFloat(name, fallback) {
  const raw = __ENV[name];
  if (raw === undefined || raw === "") {
    return fallback;
  }
  const value = Number.parseFloat(raw);
  if (Number.isNaN(value)) {
    throw new Error(`Environment variable ${name} must be a number.`);
  }
  return value;
}

export function parseCsv(raw) {
  if (!raw) {
    return [];
  }
  return raw
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);
}

export function pickWeighted(weightedItems) {
  const total = weightedItems.reduce((sum, item) => sum + item.weight, 0);
  const point = Math.random() * total;

  let cumulative = 0;
  for (const item of weightedItems) {
    cumulative += item.weight;
    if (point <= cumulative) {
      return item.value;
    }
  }
  return weightedItems[weightedItems.length - 1].value;
}

export function addDays(date, days) {
  const next = new Date(date);
  next.setUTCDate(next.getUTCDate() + days);
  return next;
}

export function formatDate(date) {
  return date.toISOString().slice(0, 10);
}

export function buildStayWindow(checkInOffsets, nightsPool) {
  const today = new Date();
  const offset = pickRandom(checkInOffsets);
  const nights = pickRandom(nightsPool);
  const checkIn = addDays(today, offset);
  const checkOut = addDays(checkIn, nights);

  return {
    checkIn: formatDate(checkIn),
    checkOut: formatDate(checkOut),
    nights,
  };
}

export function pickRandom(items) {
  if (!items || items.length === 0) {
    throw new Error("Cannot pick from an empty array.");
  }
  return items[Math.floor(Math.random() * items.length)];
}

export function normalizeWeights(searchRate, detailRate, offersRate) {
  const total = searchRate + detailRate + offersRate;
  if (total <= 0) {
    throw new Error("At least one read scenario weight must be greater than zero.");
  }

  return {
    search: searchRate / total,
    detail: detailRate / total,
    offers: offersRate / total,
  };
}
