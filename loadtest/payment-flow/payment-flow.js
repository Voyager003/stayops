// Drives the reservation-intent -> payment-confirm -> reservation flow under load.
//
// The point is not throughput (the load generator and the app share a machine) but
// whether every accepted payment settles into exactly one reservation. Each iteration
// therefore follows one booking all the way to its terminal state instead of firing
// and forgetting, and the iteration count is fixed so verify.sql can compare against
// an exact expected number rather than a ratio.
import http from "k6/http";
import exec from "k6/execution";
import { check, fail, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const BASE_URL = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/$/, "");
const PREFIX = __ENV.PAYFLOW_PREFIX || "payflow";
const PROPERTY_ID = __ENV.PROPERTY_ID || "payflow-prop-1";
const ROOM_TYPE_ID = __ENV.ROOM_TYPE_ID || "payflow-rt-1";
const PASSWORD = __ENV.CUSTOMER_PASSWORD || "password123";

const CUSTOMERS = envInt("CUSTOMERS", 40);
const ITERATIONS = envInt("ITERATIONS", 200);
const VUS = envInt("VUS", 20);
const STAY_START_OFFSET = envInt("STAY_START_OFFSET", 30);
const STAY_DAYS = envInt("STAY_DAYS", 20);
const SETTLE_TIMEOUT_S = envInt("SETTLE_TIMEOUT_S", 90);
const POLL_INTERVAL_S = envFloat("POLL_INTERVAL_S", 1.5);
// Every Nth booking sends its confirm twice, the way a double-clicked pay button would.
// 0 disables it.
const DUPLICATE_CONFIRM_EVERY = envInt("DUPLICATE_CONFIRM_EVERY", 5);
// Off by default so concurrent bookings land on different dates. Turning it on points
// every booking at one inventory row, which measures contention on that row instead of
// the payment path — most requests then lose the version CAS and never reach payment.
const HOT_DATE_ONLY = (__ENV.HOT_DATE_ONLY || "false") === "true";

const intentCreated = new Counter("payflow_intent_created");
const intentRejected = new Counter("payflow_intent_rejected");
const confirmAccepted = new Counter("payflow_confirm_accepted");
const duplicateConfirmSent = new Counter("payflow_duplicate_confirm_sent");
const reservationConfirmed = new Counter("payflow_reservation_confirmed");
const paymentFailed = new Counter("payflow_payment_failed");
const settleTimedOut = new Counter("payflow_settle_timed_out");
const confirmedRate = new Rate("payflow_reservation_confirmed_rate");
const settleSeconds = new Trend("payflow_settle_seconds");

export const options = {
  scenarios: {
    payment_flow: {
      executor: "shared-iterations",
      vus: VUS,
      iterations: ITERATIONS,
      maxDuration: "30m",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    checks: ["rate>0.99"],
    // The flow's whole promise: an accepted payment becomes a reservation
    payflow_reservation_confirmed_rate: ["rate>0.99"],
    "http_req_duration{name:reservation-intent-create}": ["p(95)<2500"],
    "http_req_duration{name:payment-confirm}": ["p(95)<2500"],
    payflow_settle_seconds: [`p(95)<${SETTLE_TIMEOUT_S}`],
  },
};

export function setup() {
  const capacity = HOT_DATE_ONLY ? CUSTOMERS : CUSTOMERS * STAY_DAYS;
  if (ITERATIONS > capacity) {
    fail(
      `Unique booking capacity exhausted: ITERATIONS=${ITERATIONS} > capacity=${capacity} ` +
        `(HOT_DATE_ONLY=${HOT_DATE_ONLY}). Duplicate (member, roomType, checkIn, checkOut) requests are ` +
        "rejected before payment, so raise CUSTOMERS or STAY_DAYS instead.",
    );
  }

  const health = http.get(`${BASE_URL}/actuator/health`, { tags: { name: "health" } });
  if (health.status !== 200) {
    fail(`App is not reachable at ${BASE_URL}. status=${health.status}`);
  }

  const sessions = [];
  for (let index = 0; index < CUSTOMERS; index += 1) {
    sessions.push(prepareCustomer(index));
  }
  return { sessions, startedAt: new Date().toISOString() };
}

export default function (data) {
  const sequence = exec.scenario.iterationInTest;
  // The date varies fastest so that iterations running at the same moment touch
  // different inventory rows; the customer only changes once a full date sweep is done
  const dayIndex = HOT_DATE_ONLY ? 0 : sequence % STAY_DAYS;
  const customerIndex = HOT_DATE_ONLY
    ? sequence % CUSTOMERS
    : Math.floor(sequence / STAY_DAYS) % CUSTOMERS;
  const session = data.sessions[customerIndex];
  const stay = buildStay(dayIndex);

  const created = createIntent(session, sequence, stay);
  if (!created) {
    confirmedRate.add(false);
    return;
  }

  const confirmStartedAt = Date.now();
  const confirmed = confirmPayment(session, created);
  if (!confirmed) {
    confirmedRate.add(false);
    return;
  }

  if (DUPLICATE_CONFIRM_EVERY > 0 && sequence % DUPLICATE_CONFIRM_EVERY === 0) {
    const duplicate = confirmPayment(session, created, "payment-confirm-duplicate");
    duplicateConfirmSent.add(1);
    check(duplicate, {
      "중복 결제 승인 요청도 거부되지 않는다": (r) => r.status === 202,
    });
  }

  settle(session, created, confirmStartedAt);
}

function createIntent(session, sequence, stay) {
  const response = http.post(
    `${BASE_URL}/api/v1/customer/reservation-intents`,
    JSON.stringify({
      propertyId: PROPERTY_ID,
      roomTypeId: ROOM_TYPE_ID,
      checkIn: stay.checkIn,
      checkOut: stay.checkOut,
      numberOfGuests: 2,
      guestName: `payflow guest ${sequence}`,
      guestPhone: buildPhone(sequence),
      guestEmail: `payflow-guest-${sequence}@example.com`,
    }),
    {
      headers: {
        ...authHeaders(session, "payment-flow", "reservation-intent-create"),
        "Idempotency-Key": `${PREFIX}-${session.runToken}-${sequence}`,
      },
      tags: { name: "reservation-intent-create" },
    },
  );

  const ok = check(response, {
    "예약 intent 생성이 201이다": (r) => r.status === 201,
  });
  if (!ok) {
    intentRejected.add(1);
    logUnexpected("reservation-intent-create", response);
    return null;
  }

  intentCreated.add(1);
  return response.json();
}

function confirmPayment(session, intent, tagName = "payment-confirm") {
  const response = http.post(
    `${BASE_URL}/api/v1/customer/reservation-intents/${intent.reservationIntentId}/confirm-payment`,
    JSON.stringify({
      paymentKey: `${PREFIX}-pk-${intent.reservationIntentId}`,
      orderId: intent.orderId,
      amount: intent.amount,
    }),
    {
      headers: authHeaders(session, "payment-flow", tagName),
      tags: { name: tagName },
    },
  );

  if (tagName !== "payment-confirm") {
    return response;
  }

  const ok = check(response, {
    "결제 승인 요청이 202다": (r) => r.status === 202,
  });
  if (!ok) {
    logUnexpected(tagName, response);
    return null;
  }

  confirmAccepted.add(1);
  return response;
}

// Confirmation is asynchronous: the API only queues an outbox message, so the
// verdict has to be read back from the intent's terminal state
function settle(session, intent, confirmStartedAt) {
  const deadline = confirmStartedAt + SETTLE_TIMEOUT_S * 1000;

  while (Date.now() < deadline) {
    sleep(POLL_INTERVAL_S);
    const response = http.get(
      `${BASE_URL}/api/v1/customer/reservation-intents/${intent.reservationIntentId}`,
      {
        headers: authHeaders(session, "payment-flow", "reservation-intent-status"),
        tags: { name: "reservation-intent-status" },
      },
    );
    if (response.status !== 200) {
      continue;
    }

    const status = response.json();
    if (status.intentStatus === "RESERVED") {
      settleSeconds.add((Date.now() - confirmStartedAt) / 1000);
      reservationConfirmed.add(1);
      confirmedRate.add(true);
      check(status, {
        "확정된 예약의 결제는 APPROVED다": (s) => s.paymentStatus === "APPROVED",
      });
      return;
    }
    if (["PAYMENT_FAILED", "EXPIRED", "CANCELLED"].includes(status.intentStatus)) {
      paymentFailed.add(1);
      confirmedRate.add(false);
      console.warn(
        `payment did not settle into a reservation. reservationIntentId=${intent.reservationIntentId} ` +
          `intentStatus=${status.intentStatus} paymentStatus=${status.paymentStatus}`,
      );
      return;
    }
  }

  settleTimedOut.add(1);
  confirmedRate.add(false);
  console.warn(
    `payment settle timed out after ${SETTLE_TIMEOUT_S}s. ` +
      `reservationIntentId=${intent.reservationIntentId}`,
  );
}

function prepareCustomer(index) {
  const email = `${PREFIX}-c${String(index).padStart(4, "0")}@example.com`;
  const jar = new http.CookieJar();

  // seed.sql drops these accounts, so signup normally succeeds; a non-201 is
  // tolerated so the script still runs against an already-seeded database
  http.post(
    `${BASE_URL}/api/v1/customer/auth/signup`,
    JSON.stringify({ email, password: PASSWORD, name: `payflow ${index}` }),
    {
      jar,
      headers: csrfHeaders(fetchCsrf(jar), "setup", "customer-signup"),
      tags: { name: "customer-signup" },
    },
  );

  const login = http.post(
    `${BASE_URL}/api/v1/customer/auth/login`,
    JSON.stringify({ email, password: PASSWORD }),
    {
      jar,
      headers: csrfHeaders(fetchCsrf(jar), "setup", "customer-login"),
      tags: { name: "customer-login" },
    },
  );
  if (login.status !== 200) {
    fail(`Setup failed. customer login email=${email} status=${login.status} body=${login.body}`);
  }

  // Login rotates the session id, so both the cookie and the session-bound
  // CSRF token have to be read after authenticating
  const cookie = sessionCookie(jar);
  return {
    email,
    cookie,
    csrf: fetchCsrf(jar),
    runToken: `${__ENV.PAYFLOW_RUN_ID || "run"}`,
  };
}

function fetchCsrf(jar) {
  const response = http.get(`${BASE_URL}/api/v1/csrf`, {
    jar,
    tags: { name: "csrf" },
  });
  if (response.status !== 200) {
    fail(`Setup failed. csrf status=${response.status}`);
  }
  return response.json().token;
}

function sessionCookie(jar) {
  const cookies = jar.cookiesForURL(BASE_URL);
  const session = cookies && cookies.SESSION;
  const value = Array.isArray(session) ? session[0] : session;
  if (!value) {
    fail("Setup failed. SESSION cookie was not returned.");
  }
  return `SESSION=${value}`;
}

function loadtestHeaders(phase, scenario) {
  return {
    "X-Experiment-Id": `${PREFIX}-${__ENV.PAYFLOW_RUN_ID || "run"}`,
    "X-Loadtest-Phase": phase,
    "X-Loadtest-Scenario": scenario,
  };
}

function csrfHeaders(token, phase, scenario) {
  return {
    ...loadtestHeaders(phase, scenario),
    "Content-Type": "application/json",
    "X-CSRF-TOKEN": token,
  };
}

function authHeaders(session, phase, scenario) {
  return {
    ...csrfHeaders(session.csrf, phase, scenario),
    Cookie: session.cookie,
  };
}

function buildStay(dayIndex) {
  const checkIn = addDays(new Date(), STAY_START_OFFSET + dayIndex);
  return {
    checkIn: formatDate(checkIn),
    checkOut: formatDate(addDays(checkIn, 1)),
  };
}

function buildPhone(sequence) {
  return `010${String(sequence % 100000000).padStart(8, "0")}`;
}

function addDays(date, days) {
  const next = new Date(date);
  next.setUTCDate(next.getUTCDate() + days);
  return next;
}

function formatDate(date) {
  return date.toISOString().slice(0, 10);
}

function logUnexpected(context, response) {
  console.warn(
    `unexpected response. context=${context} status=${response.status} body=${String(response.body).slice(0, 300)}`,
  );
}

function envInt(name, fallback) {
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

function envFloat(name, fallback) {
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
