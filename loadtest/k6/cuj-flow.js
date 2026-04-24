import http from "k6/http";
import { check, fail, sleep } from "k6";
import {
  BASE_URL,
  buildSequentialList,
  buildStayWindow,
  envFloat,
  envInt,
  envString,
  normalizeWeights,
  parseCsv,
  parseIntCsv,
  pickRandom,
  pickWeighted,
} from "./common.js";

const CUSTOMER_COUNT = envInt("CUSTOMER_COUNT", 100);
const CUSTOMER_EMAILS = resolveCustomerEmails();
const CUSTOMER_PASSWORD = envString("CUSTOMER_PASSWORD", "password123");
const HOT_PROPERTY_COUNT = envInt("HOT_PROPERTY_COUNT", 10);
const THINK_TIME = envFloat("CUJ_THINK_TIME", 0.5);

const SEARCH_RATE = envFloat("SEARCH_RATE", 0.25);
const DETAIL_RATE = envFloat("DETAIL_RATE", 0.20);
const OFFERS_RATE = envFloat("OFFERS_RATE", 0.25);
const RESERVATION_CREATE_RATE = envFloat("RESERVATION_CREATE_RATE", 0.20);
const MY_RESERVATIONS_RATE = envFloat("MY_RESERVATIONS_RATE", 0.10);

const CHECK_IN_OFFSETS = parseIntCsv(__ENV.CHECK_IN_OFFSETS || "3,5,7,14,21,30,45,60,90,120");
const NIGHTS_POOL = parseIntCsv(__ENV.NIGHTS_POOL || "1,2,3");
const GUESTS_POOL = parseIntCsv(__ENV.GUESTS_POOL || "1,2,3,4");
const NORMALIZED = normalizeCujWeights();

let loggedIn = false;

export function buildThresholds() {
  return {
    http_req_failed: ["rate<0.01"],
    checks: ["rate>0.99"],
    "http_req_duration{name:customer-login}": ["p(95)<1500"],
    "http_req_duration{name:customer-properties-list}": ["p(95)<1500"],
    "http_req_duration{name:customer-property-detail}": ["p(95)<1000"],
    "http_req_duration{name:customer-room-types}": ["p(95)<1000"],
    "http_req_duration{name:customer-property-offers}": ["p(95)<2000"],
    "http_req_duration{name:customer-reservation-create}": ["p(95)<2500"],
    "http_req_duration{name:customer-my-reservations}": ["p(95)<1500"],
  };
}

export function buildArrivalRateOptions({ startRate, stages, preAllocatedVUs, maxVUs }) {
  return {
    thresholds: buildThresholds(),
    scenarios: {
      customer_critical_journey: {
        executor: "ramping-arrival-rate",
        startRate,
        timeUnit: "1s",
        preAllocatedVUs,
        maxVUs,
        stages,
      },
    },
  };
}

export function buildSmokeOptions() {
  return {
    thresholds: buildThresholds(),
    scenarios: {
      smoke: {
        executor: "constant-vus",
        vus: 1,
        duration: "30s",
      },
    },
  };
}

export function setupCujData() {
  const propertyResponse = http.get(`${BASE_URL}/api/v1/customer/properties`, {
    tags: { name: "customer-properties-list-setup" },
  });

  if (!check(propertyResponse, { "setup property list status is 200": (r) => r.status === 200 })) {
    fail(`Setup failed. property list status=${propertyResponse.status}`);
  }

  const properties = propertyResponse.json();
  if (!Array.isArray(properties) || properties.length === 0) {
    fail("Setup failed. property list is empty.");
  }

  const configuredPropertyIds = parseCsv(__ENV.HOT_PROPERTY_IDS);
  const hotPropertyIds =
    configuredPropertyIds.length > 0
      ? configuredPropertyIds
      : properties
          .slice(0, HOT_PROPERTY_COUNT)
          .map((property) => property.id)
          .filter(Boolean);

  const roomTypesByPropertyId = {};
  for (const propertyId of hotPropertyIds) {
    const roomTypesResponse = http.get(`${BASE_URL}/api/v1/customer/properties/${propertyId}/room-types`, {
      tags: { name: "customer-room-types-setup" },
    });
    if (roomTypesResponse.status !== 200) {
      continue;
    }
    const roomTypes = roomTypesResponse.json();
    if (Array.isArray(roomTypes) && roomTypes.length > 0) {
      roomTypesByPropertyId[propertyId] = roomTypes.map((roomType) => roomType.id).filter(Boolean);
    }
  }

  const bookablePropertyIds = Object.keys(roomTypesByPropertyId);
  if (bookablePropertyIds.length === 0) {
    fail("Setup failed. no property with room types available.");
  }

  return {
    propertyIds: bookablePropertyIds,
    roomTypesByPropertyId,
  };
}

export function runCujIteration(data) {
  const flow = pickWeighted([
    { weight: NORMALIZED.search, value: "search" },
    { weight: NORMALIZED.detail, value: "detail" },
    { weight: NORMALIZED.offers, value: "offers" },
    { weight: NORMALIZED.createReservation, value: "createReservation" },
    { weight: NORMALIZED.myReservations, value: "myReservations" },
  ]);

  if (flow === "search") {
    runSearchFlow();
  } else if (flow === "detail") {
    runDetailFlow(data);
  } else if (flow === "offers") {
    runOffersFlow(data);
  } else if (flow === "createReservation") {
    ensureLoggedIn();
    runReservationCreateFlow(data);
  } else {
    ensureLoggedIn();
    runMyReservationsFlow();
  }

  sleep(THINK_TIME);
}

function resolveCustomerEmails() {
  const configured = parseCsv(__ENV.CUSTOMER_EMAILS);
  if (configured.length > 0) {
    return configured;
  }
  return buildSequentialList("loadtest-customer-", CUSTOMER_COUNT, "@example.com");
}

function normalizeCujWeights() {
  const browse = normalizeWeights(SEARCH_RATE, DETAIL_RATE, OFFERS_RATE);
  const total = SEARCH_RATE + DETAIL_RATE + OFFERS_RATE + RESERVATION_CREATE_RATE + MY_RESERVATIONS_RATE;
  if (total <= 0) {
    throw new Error("At least one CUJ scenario weight must be greater than zero.");
  }
  return {
    search: browse.search * ((SEARCH_RATE + DETAIL_RATE + OFFERS_RATE) / total),
    detail: browse.detail * ((SEARCH_RATE + DETAIL_RATE + OFFERS_RATE) / total),
    offers: browse.offers * ((SEARCH_RATE + DETAIL_RATE + OFFERS_RATE) / total),
    createReservation: RESERVATION_CREATE_RATE / total,
    myReservations: MY_RESERVATIONS_RATE / total,
  };
}

function ensureLoggedIn() {
  if (loggedIn) {
    return;
  }

  const email = CUSTOMER_EMAILS[(__VU - 1) % CUSTOMER_EMAILS.length];
  const response = http.post(
    `${BASE_URL}/api/v1/customer/auth/login`,
    JSON.stringify({ email, password: CUSTOMER_PASSWORD }),
    {
      headers: { "Content-Type": "application/json" },
      tags: { name: "customer-login" },
    },
  );

  loggedIn = check(response, {
    "login status is 200": (r) => r.status === 200,
  });
}

function runSearchFlow() {
  const response = http.get(`${BASE_URL}/api/v1/customer/properties`, {
    tags: { name: "customer-properties-list" },
  });

  check(response, {
    "property list status is 200": (r) => r.status === 200,
    "property list returns array": (r) => r.status === 200 && Array.isArray(r.json()),
  });
}

function runDetailFlow(data) {
  const propertyId = pickRandom(data.propertyIds);
  const propertyResponse = http.get(`${BASE_URL}/api/v1/customer/properties/${propertyId}`, {
    tags: { name: "customer-property-detail" },
  });

  check(propertyResponse, {
    "property detail status is 200": (r) => r.status === 200,
  });

  const roomTypeResponse = http.get(`${BASE_URL}/api/v1/customer/properties/${propertyId}/room-types`, {
    tags: { name: "customer-room-types" },
  });

  check(roomTypeResponse, {
    "room types status is 200": (r) => r.status === 200,
    "room types returns array": (r) => r.status === 200 && Array.isArray(r.json()),
  });
}

function runOffersFlow(data) {
  const propertyId = pickRandom(data.propertyIds);
  const stay = buildStayWindow(CHECK_IN_OFFSETS, NIGHTS_POOL);
  const guests = pickRandom(GUESTS_POOL);

  const response = http.get(
    `${BASE_URL}/api/v1/customer/properties/${propertyId}/offers?checkIn=${stay.checkIn}&checkOut=${stay.checkOut}&guests=${guests}`,
    {
      tags: { name: "customer-property-offers" },
    },
  );

  check(response, {
    "offers status is 200": (r) => r.status === 200,
    "offers returns array": (r) => r.status === 200 && Array.isArray(r.json()),
  });
}

function runReservationCreateFlow(data) {
  const propertyId = pickRandom(data.propertyIds);
  const roomTypeId = pickRandom(data.roomTypesByPropertyId[propertyId]);
  const stay = buildReservationStayWindow();
  const guests = pickRandom(GUESTS_POOL);
  const sequence = `${Date.now()}-${__VU}-${__ITER}`;

  const response = http.post(
    `${BASE_URL}/api/v1/customer/reservations`,
    JSON.stringify({
      propertyId,
      roomTypeId,
      checkIn: stay.checkIn,
      checkOut: stay.checkOut,
      numberOfGuests: guests,
      guestName: `loadtest guest ${sequence}`,
      guestPhone: `010${String(__VU).padStart(4, "0")}${String(__ITER % 10000).padStart(4, "0")}`,
      guestEmail: `loadtest-guest-${sequence}@example.com`,
    }),
    {
      headers: { "Content-Type": "application/json" },
      tags: { name: "customer-reservation-create" },
    },
  );

  check(response, {
    "reservation create status is 201": (r) => r.status === 201,
  });
}

function buildReservationStayWindow() {
  const sequence = __VU * 1000000 + __ITER;
  const checkIn = addDaysFromNow(3 + (sequence % 3650));
  const checkOut = addDaysFromNow(3 + (sequence % 3650) + pickRandom(NIGHTS_POOL));
  return {
    checkIn: formatDate(checkIn),
    checkOut: formatDate(checkOut),
  };
}

function addDaysFromNow(days) {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() + days);
  return date;
}

function formatDate(date) {
  return date.toISOString().slice(0, 10);
}

function runMyReservationsFlow() {
  const response = http.get(`${BASE_URL}/api/v1/customer/reservations`, {
    tags: { name: "customer-my-reservations" },
  });

  check(response, {
    "my reservations status is 200": (r) => r.status === 200,
    "my reservations returns array": (r) => r.status === 200 && Array.isArray(r.json()),
  });
}
