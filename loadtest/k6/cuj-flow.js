import http from "k6/http";
import { check, fail, sleep } from "k6";
import exec from "k6/execution";
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
import {
  assertStayWindowWithinInventory,
  pickUniquePendingReservation,
  selectStayWindowOffsets,
} from "./cuj-policy.js";

const RUN_ID = envString("LOADTEST_RUN_ID", "run-001");
const PREFIX = envString("LOADTEST_PREFIX", `loadtest-${RUN_ID}`);
const EXPERIMENT_ID = envString("EXPERIMENT_ID", PREFIX);
const CUSTOMER_COUNT = envInt("CUSTOMER_COUNT", 30);
const OWNER_COUNT = envInt("OWNER_COUNT", envInt("LOADTEST_PROPERTY_COUNT", 10));
const CUSTOMER_EMAILS = resolveCustomerEmails();
const OWNER_EMAILS = resolveOwnerEmails();
const CUSTOMER_PASSWORD = envString("CUSTOMER_PASSWORD", "password123");
const OWNER_PASSWORD = envString("OWNER_PASSWORD", CUSTOMER_PASSWORD);
const HOT_PROPERTY_COUNT = envInt("HOT_PROPERTY_COUNT", 10);
const THINK_TIME = envFloat("CUJ_THINK_TIME", 0.5);
const LOADTEST_INVENTORY_DAYS = envInt("LOADTEST_INVENTORY_DAYS", 60);
const LOG_UNEXPECTED_RESPONSES = envString("LOG_UNEXPECTED_RESPONSES", "false") === "true";

const SEARCH_RATE = envFloat("SEARCH_RATE", 0.20);
const DETAIL_RATE = envFloat("DETAIL_RATE", 0.15);
const OFFERS_RATE = envFloat("OFFERS_RATE", 0.20);
const RESERVATION_CREATE_RATE = envFloat("RESERVATION_CREATE_RATE", 0.20);
const MY_RESERVATIONS_RATE = envFloat("MY_RESERVATIONS_RATE", 0.10);
const PMS_LIST_RATE = envFloat("PMS_LIST_RATE", 0.12);
const PMS_CONFIRM_RATE = envFloat("PMS_CONFIRM_RATE", 0);

const CHECK_IN_OFFSETS = parseIntCsv(__ENV.CHECK_IN_OFFSETS || "3,5,7,14,21,30,45");
const NIGHTS_POOL = parseIntCsv(__ENV.NIGHTS_POOL || "1,2,3");
const GUESTS_POOL = parseIntCsv(__ENV.GUESTS_POOL || "1,2,3,4");
assertStayWindowWithinInventory({
  checkInOffsets: CHECK_IN_OFFSETS,
  nightsPool: NIGHTS_POOL,
  inventoryDays: LOADTEST_INVENTORY_DAYS,
});
const NORMALIZED = normalizeCujWeights();

export function buildThresholds() {
  return {
    http_req_failed: ["rate<0.01"],
    checks: ["rate>0.99"],
    "http_req_duration{name:customer-login}": ["p(95)<1500"],
    "http_req_duration{name:owner-login}": ["p(95)<1500"],
    "http_req_duration{name:customer-properties-list}": ["p(95)<1500"],
    "http_req_duration{name:customer-property-detail}": ["p(95)<1000"],
    "http_req_duration{name:customer-room-types}": ["p(95)<1000"],
    "http_req_duration{name:customer-property-offers}": ["p(95)<2000"],
    "http_req_duration{name:customer-reservation-create}": ["p(95)<2500"],
    "http_req_duration{name:customer-confirm-payment}": ["p(95)<2500"],
    "http_req_duration{name:customer-my-reservations}": ["p(95)<1500"],
    "http_req_duration{name:pms-reservations-list}": ["p(95)<1500"],
    "http_req_duration{name:pms-reservation-confirm}": ["p(95)<2000"],
  };
}

export function buildArrivalRateOptions({ startRate, stages, preAllocatedVUs, maxVUs }) {
  return {
    thresholds: buildThresholds(),
    scenarios: {
      production_critical_journey: {
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

export function buildPmsConfirmOptions({ iterations, vus, maxDuration }) {
  return {
    thresholds: buildThresholds(),
    scenarios: {
      pms_confirm_once: {
        executor: "shared-iterations",
        vus,
        iterations,
        maxDuration,
      },
    },
  };
}

export function setupCujData() {
  const propertyResponse = http.get(`${BASE_URL}/api/v1/customer/properties`, {
    tags: { name: "customer-properties-list-setup" },
    headers: loadtestHeaders("setup", "customer-properties-list"),
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
          .filter((property) => !PREFIX || String(property.id || "").startsWith(PREFIX))
          .slice(0, HOT_PROPERTY_COUNT)
          .map((property) => property.id)
          .filter(Boolean);

  const propertyIds = hotPropertyIds.length > 0 ? hotPropertyIds : properties.slice(0, HOT_PROPERTY_COUNT).map((p) => p.id).filter(Boolean);
  const roomTypesByPropertyId = resolveRoomTypes(propertyIds);
  const bookablePropertyIds = Object.keys(roomTypesByPropertyId);
  if (bookablePropertyIds.length === 0) {
    fail("Setup failed. no property with room types available.");
  }

  const customerSessions = loginSessions(CUSTOMER_EMAILS, CUSTOMER_PASSWORD, "/api/v1/customer/auth/login", "customer-login");
  const ownerSessions = loginOwnerSessions(OWNER_EMAILS, OWNER_PASSWORD, bookablePropertyIds);
  const pendingReservations = collectPendingReservations(ownerSessions);

  return {
    propertyIds: bookablePropertyIds,
    roomTypesByPropertyId,
    customerSessions,
    ownerSessions,
    pendingReservations,
  };
}

export function runCujIteration(data) {
  const flow = pickWeighted([
    { weight: NORMALIZED.search, value: "search" },
    { weight: NORMALIZED.detail, value: "detail" },
    { weight: NORMALIZED.offers, value: "offers" },
    { weight: NORMALIZED.createReservation, value: "createReservation" },
    { weight: NORMALIZED.myReservations, value: "myReservations" },
    { weight: NORMALIZED.pmsList, value: "pmsList" },
    { weight: NORMALIZED.pmsConfirm, value: "pmsConfirm" },
  ]);

  if (flow === "search") {
    runSearchFlow();
  } else if (flow === "detail") {
    runDetailFlow(data);
  } else if (flow === "offers") {
    runOffersFlow(data);
  } else if (flow === "createReservation") {
    runReservationCreateAndPaymentFlow(data);
  } else if (flow === "myReservations") {
    runMyReservationsFlow(data);
  } else if (flow === "pmsList") {
    runPmsReservationsListFlow(data);
  } else {
    runPmsConfirmFlow(data);
  }

  sleep(THINK_TIME);
}

export function runPmsConfirmOnceIteration(data) {
  runPmsConfirmFlow(data);
  sleep(THINK_TIME);
}

function resolveCustomerEmails() {
  const configured = parseCsv(__ENV.CUSTOMER_EMAILS);
  if (configured.length > 0) {
    return configured;
  }
  return buildSequentialList(`${PREFIX}-customer-`, CUSTOMER_COUNT, "@example.com");
}

function resolveOwnerEmails() {
  const configured = parseCsv(__ENV.OWNER_EMAILS);
  if (configured.length > 0) {
    return configured;
  }
  return buildSequentialList(`${PREFIX}-owner-`, OWNER_COUNT, "@example.com");
}

function normalizeCujWeights() {
  const browse = normalizeWeights(SEARCH_RATE, DETAIL_RATE, OFFERS_RATE);
  const browseTotal = SEARCH_RATE + DETAIL_RATE + OFFERS_RATE;
  const total = browseTotal + RESERVATION_CREATE_RATE + MY_RESERVATIONS_RATE + PMS_LIST_RATE + PMS_CONFIRM_RATE;
  if (total <= 0) {
    throw new Error("At least one CUJ scenario weight must be greater than zero.");
  }
  return {
    search: browse.search * (browseTotal / total),
    detail: browse.detail * (browseTotal / total),
    offers: browse.offers * (browseTotal / total),
    createReservation: RESERVATION_CREATE_RATE / total,
    myReservations: MY_RESERVATIONS_RATE / total,
    pmsList: PMS_LIST_RATE / total,
    pmsConfirm: PMS_CONFIRM_RATE / total,
  };
}

function resolveRoomTypes(propertyIds) {
  const roomTypesByPropertyId = {};
  for (const propertyId of propertyIds) {
    const roomTypesResponse = http.get(`${BASE_URL}/api/v1/customer/properties/${propertyId}/room-types`, {
      tags: { name: "customer-room-types-setup" },
      headers: loadtestHeaders("setup", "customer-room-types"),
    });
    if (roomTypesResponse.status !== 200) {
      continue;
    }
    const roomTypes = roomTypesResponse.json();
    if (Array.isArray(roomTypes) && roomTypes.length > 0) {
      roomTypesByPropertyId[propertyId] = roomTypes.map((roomType) => roomType.id).filter(Boolean);
    }
  }
  return roomTypesByPropertyId;
}

function loginSessions(emails, password, path, tagName) {
  return emails.map((email) => {
    const jar = new http.CookieJar();
    const response = http.post(`${BASE_URL}${path}`, JSON.stringify({ email, password }), {
      jar,
      headers: jsonHeaders("setup", tagName),
      tags: { name: tagName },
    });
    const ok = check(response, { [`${tagName} status is 200`]: (r) => r.status === 200 });
    if (!ok) {
      fail(`Setup failed. ${tagName} email=${email} status=${response.status}`);
    }
    return {
      email,
      cookie: extractSessionCookie(response, jar),
    };
  });
}

function loginOwnerSessions(emails, password, propertyIds) {
  const sessions = loginSessions(emails, password, "/api/v1/auth/login", "owner-login");
  return sessions.map((session, index) => ({
    ...session,
    propertyId: propertyIds.includes(`${PREFIX}-property-${String(index + 1).padStart(4, "0")}`)
      ? `${PREFIX}-property-${String(index + 1).padStart(4, "0")}`
      : propertyIds[index % propertyIds.length],
  }));
}

function collectPendingReservations(ownerSessions) {
  const pending = [];
  for (const session of ownerSessions) {
    const response = http.get(`${BASE_URL}/api/v1/properties/${session.propertyId}/reservations?status=PENDING&page=0&size=100`, {
      headers: authHeaders(session, "setup", "pms-reservations-list"),
      tags: { name: "pms-reservations-list-setup" },
    });
    if (response.status !== 200) {
      continue;
    }
    const body = response.json();
    const content = Array.isArray(body.content) ? body.content : [];
    for (const reservation of content) {
      if (reservation.reservationId || reservation.id) {
        pending.push({
          propertyId: session.propertyId,
          reservationId: reservation.reservationId || reservation.id,
          cookie: session.cookie,
        });
      }
    }
  }
  return pending;
}

function checkExpectedStatus(response, expectedStatus, checkName, context) {
  const ok = check(response, {
    [checkName]: (r) => r.status === expectedStatus,
  });
  if (!ok) {
    logUnexpectedResponse(context, response, expectedStatus);
  }
  return ok;
}

function logUnexpectedResponse(context, response, expectedStatus) {
  if (!LOG_UNEXPECTED_RESPONSES) {
    return;
  }

  const body = typeof response.body === "string" ? response.body.slice(0, 500) : "";
  console.error(
    JSON.stringify({
      experimentId: EXPERIMENT_ID,
      context,
      expectedStatus,
      actualStatus: response.status,
      body,
    }),
  );
}

function extractSessionCookie(response, jar) {
  const session = response.cookies && response.cookies.SESSION && response.cookies.SESSION[0];
  if (session && session.value) {
    return `SESSION=${session.value}`;
  }

  const jarSession = jar && jar.cookiesForURL(BASE_URL).SESSION;
  if (Array.isArray(jarSession) && jarSession[0]) {
    return `SESSION=${jarSession[0]}`;
  }
  if (typeof jarSession === "string" && jarSession) {
    return `SESSION=${jarSession}`;
  }

  const setCookie = response.headers && (response.headers["Set-Cookie"] || response.headers["set-cookie"]);
  const match = typeof setCookie === "string" ? setCookie.match(/(?:^|,\s*)SESSION=([^;]+)/) : null;
  if (!match || !match[1]) {
    fail("Setup failed. SESSION cookie was not returned.");
  }
  return `SESSION=${match[1]}`;
}

function runSearchFlow() {
  const response = http.get(`${BASE_URL}/api/v1/customer/properties`, {
    tags: { name: "customer-properties-list" },
    headers: loadtestHeaders("customer", "customer-properties-list"),
  });

  checkExpectedStatus(response, 200, "property list status is 200", "customer-properties-list");
  check(response, {
    "property list returns array": (r) => r.status === 200 && Array.isArray(r.json()),
  });
}

function runDetailFlow(data) {
  const propertyId = pickRandom(data.propertyIds);
  const propertyResponse = http.get(`${BASE_URL}/api/v1/customer/properties/${propertyId}`, {
    tags: { name: "customer-property-detail" },
    headers: loadtestHeaders("customer", "customer-property-detail"),
  });

  checkExpectedStatus(propertyResponse, 200, "property detail status is 200", "customer-property-detail");

  const roomTypeResponse = http.get(`${BASE_URL}/api/v1/customer/properties/${propertyId}/room-types`, {
    tags: { name: "customer-room-types" },
    headers: loadtestHeaders("customer", "customer-room-types"),
  });

  checkExpectedStatus(roomTypeResponse, 200, "room types status is 200", "customer-room-types");
  check(roomTypeResponse, {
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
      headers: loadtestHeaders("customer", "customer-property-offers"),
    },
  );

  checkExpectedStatus(response, 200, "offers status is 200", "customer-property-offers");
  check(response, {
    "offers returns array": (r) => r.status === 200 && Array.isArray(r.json()),
  });
}

function runReservationCreateAndPaymentFlow(data) {
  const session = pickSession(data.customerSessions);
  const propertyId = pickRandom(data.propertyIds);
  const roomTypeId = pickRandom(data.roomTypesByPropertyId[propertyId]);
  const stay = buildReservationStayWindow();
  const guests = pickRandom(GUESTS_POOL);
  const sequence = `${Date.now()}-${__VU}-${__ITER}`;

  const createResponse = http.post(
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
      headers: authJsonHeaders(session, "customer", "customer-reservation-create"),
      tags: { name: "customer-reservation-create" },
    },
  );

  const created = checkExpectedStatus(createResponse, 201, "reservation create status is 201", "customer-reservation-create");
  if (!created) {
    return;
  }

  const body = createResponse.json();
  const confirmResponse = http.post(
    `${BASE_URL}/api/v1/customer/reservations/${body.reservationId}/confirm-payment`,
    JSON.stringify({
      paymentKey: `loadtest-payment-key-${body.reservationId}-${Date.now()}`,
      orderId: body.orderId,
      amount: body.amount,
    }),
    {
      headers: authJsonHeaders(session, "customer", "customer-confirm-payment"),
      tags: { name: "customer-confirm-payment" },
    },
  );

  checkExpectedStatus(confirmResponse, 202, "confirm payment status is 202", "customer-confirm-payment");
}

function runMyReservationsFlow(data) {
  const session = pickSession(data.customerSessions);
  const response = http.get(`${BASE_URL}/api/v1/customer/reservations`, {
    tags: { name: "customer-my-reservations" },
    headers: authHeaders(session, "customer", "customer-my-reservations"),
  });

  checkExpectedStatus(response, 200, "my reservations status is 200", "customer-my-reservations");
  check(response, {
    "my reservations returns array": (r) => r.status === 200 && Array.isArray(r.json()),
  });
}

function runPmsReservationsListFlow(data) {
  const session = pickSession(data.ownerSessions);
  const response = http.get(`${BASE_URL}/api/v1/properties/${session.propertyId}/reservations?page=0&size=20`, {
    tags: { name: "pms-reservations-list" },
    headers: authHeaders(session, "pms", "pms-reservations-list"),
  });

  checkExpectedStatus(response, 200, "pms reservations list status is 200", "pms-reservations-list");
}

function runPmsConfirmFlow(data) {
  if (!data.pendingReservations || data.pendingReservations.length === 0) {
    runPmsReservationsListFlow(data);
    return;
  }
  const target = pickUniquePendingReservation(data.pendingReservations, currentIterationInTest());
  if (!target) {
    runPmsReservationsListFlow(data);
    return;
  }
  const response = http.post(
    `${BASE_URL}/api/v1/properties/${target.propertyId}/reservations/${target.reservationId}/confirm`,
    null,
    {
      tags: { name: "pms-reservation-confirm" },
      headers: {
        ...loadtestHeaders("pms", "pms-reservation-confirm"),
        Cookie: target.cookie,
      },
    },
  );

  checkExpectedStatus(response, 200, "pms reservation confirm status is 200", "pms-reservation-confirm");
}

function pickSession(sessions) {
  if (!sessions || sessions.length === 0) {
    fail("No authenticated session available.");
  }
  return sessions[(__VU + __ITER) % sessions.length];
}

function buildReservationStayWindow() {
  const sequence = __VU * 1000000 + __ITER;
  const stay = selectStayWindowOffsets({
    sequence,
    checkInOffsets: CHECK_IN_OFFSETS,
    nightsPool: NIGHTS_POOL,
  });
  const checkIn = addDaysFromNow(stay.checkInOffset);
  const checkOut = addDaysFromNow(stay.checkInOffset + stay.nights);
  return {
    checkIn: formatDate(checkIn),
    checkOut: formatDate(checkOut),
  };
}

function currentIterationInTest() {
  const iterationInTest = exec && exec.scenario && exec.scenario.iterationInTest;
  return Number.isInteger(iterationInTest) ? iterationInTest : __ITER;
}

function addDaysFromNow(days) {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() + days);
  return date;
}

function formatDate(date) {
  return date.toISOString().slice(0, 10);
}

function authHeaders(session, phase, scenario) {
  return {
    ...loadtestHeaders(phase, scenario),
    Cookie: session.cookie,
  };
}

function jsonHeaders(phase, scenario) {
  return {
    ...loadtestHeaders(phase, scenario),
    "Content-Type": "application/json",
  };
}

function authJsonHeaders(session, phase, scenario) {
  return {
    ...jsonHeaders(phase, scenario),
    Cookie: session.cookie,
  };
}

function loadtestHeaders(phase, scenario) {
  return {
    "X-Experiment-Id": EXPERIMENT_ID,
    "X-Loadtest-Phase": phase,
    "X-Loadtest-Scenario": scenario,
  };
}
