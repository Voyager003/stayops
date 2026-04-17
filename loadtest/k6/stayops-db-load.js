import http from "k6/http";
import { check, group, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "https://api.example.com";
const PROPERTY_ID = __ENV.PROPERTY_ID || "property-dummy-001";
const ROOM_TYPE_ID = __ENV.ROOM_TYPE_ID || "roomtype-dummy-001";
const CUSTOMER_EMAIL = __ENV.CUSTOMER_EMAIL || "guest@dummy.com";
const CUSTOMER_PASSWORD = __ENV.CUSTOMER_PASSWORD || "password123";
const CHECK_IN = __ENV.CHECK_IN || offsetDate(14);
const CHECK_OUT = __ENV.CHECK_OUT || offsetDate(15);
const TEST_MODE = __ENV.TEST_MODE || "ramp";
const READ_RATE = Number(__ENV.READ_RATE || "20");
const WRITE_RATE = Number(__ENV.WRITE_RATE || "2");

export const options = {
  scenarios: {
    read_heavy: {
      executor: "ramping-arrival-rate",
      exec: "readHeavy",
      startRate: 1,
      timeUnit: "1s",
      preAllocatedVUs: 30,
      maxVUs: 100,
      stages: stagesFor(READ_RATE)
    },
    write_mixed: {
      executor: "ramping-arrival-rate",
      exec: "writeMixed",
      startRate: 1,
      timeUnit: "1s",
      preAllocatedVUs: 10,
      maxVUs: 50,
      stages: stagesFor(WRITE_RATE)
    }
  },
  thresholds: {
    http_req_failed: ["rate<0.05"],
    "http_req_duration{scenario:read_heavy}": ["p(95)<800", "p(99)<1500"],
    "http_req_duration{scenario:write_mixed}": ["p(95)<1500", "p(99)<3000"]
  }
};

export function setup() {
  const loginRes = http.post(
    `${BASE_URL}/api/v1/customer/auth/login`,
    JSON.stringify({ email: CUSTOMER_EMAIL, password: CUSTOMER_PASSWORD }),
    jsonParams()
  );

  check(loginRes, {
    "customer login succeeded": (res) => res.status === 200
  });

  return {
    customerCookie: extractSessionCookie(loginRes)
  };
}

export function readHeavy() {
  group("customer read path", () => {
    const params = { tags: { flow: "read" } };

    check(http.get(`${BASE_URL}/api/v1/customer/properties`, params), {
      "properties list 200": (res) => res.status === 200
    });

    check(http.get(`${BASE_URL}/api/v1/customer/properties/${PROPERTY_ID}`, params), {
      "property detail 200": (res) => res.status === 200
    });

    check(http.get(`${BASE_URL}/api/v1/customer/properties/${PROPERTY_ID}/room-types`, params), {
      "room types 200": (res) => res.status === 200
    });

    const query = `checkIn=${CHECK_IN}&checkOut=${CHECK_OUT}`;
    check(http.get(`${BASE_URL}/api/v1/customer/properties/${PROPERTY_ID}/room-types/${ROOM_TYPE_ID}/availability?${query}`, params), {
      "availability 200": (res) => res.status === 200
    });

    check(http.get(`${BASE_URL}/api/v1/customer/properties/${PROPERTY_ID}/room-types/${ROOM_TYPE_ID}/rates?${query}`, params), {
      "rates 200": (res) => res.status === 200
    });
  });

  sleep(1);
}

export function writeMixed(data) {
  group("customer reservation create", () => {
    const payload = {
      propertyId: PROPERTY_ID,
      roomTypeId: ROOM_TYPE_ID,
      checkIn: CHECK_IN,
      checkOut: CHECK_OUT,
      numberOfGuests: 2,
      guestName: `loadtest-${__VU}-${__ITER}`,
      guestPhone: "010-0000-0000",
      guestEmail: `loadtest-${__VU}-${__ITER}@example.com`
    };

    const params = jsonParams(data.customerCookie);
    const res = http.post(`${BASE_URL}/api/v1/customer/reservations`, JSON.stringify(payload), params);

    check(res, {
      "reservation create accepted": (response) => response.status === 201,
      "reservation id returned": (response) => {
        try {
          return Boolean(response.json("reservationId"));
        } catch (_) {
          return false;
        }
      }
    });
  });

  sleep(1);
}

export function handleSummary(data) {
  return {
    stdout: JSON.stringify({
      metrics: {
        http_req_failed: data.metrics.http_req_failed,
        http_req_duration: data.metrics.http_req_duration,
        checks: data.metrics.checks
      },
      root_group: data.root_group
    }, null, 2),
    "summary.json": JSON.stringify(data, null, 2)
  };
}

function jsonParams(cookie) {
  const headers = { "Content-Type": "application/json" };
  if (cookie) {
    headers.Cookie = cookie;
  }
  return { headers };
}

function extractSessionCookie(response) {
  const candidates = ["SESSION", "JSESSIONID"];
  for (const name of candidates) {
    if (response.cookies[name] && response.cookies[name][0]) {
      return `${name}=${response.cookies[name][0].value}`;
    }
  }
  return "";
}

function offsetDate(days) {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

function stagesFor(rate) {
  const profiles = {
    smoke: [
      { target: rate, duration: "30s" },
      { target: 0, duration: "10s" }
    ],
    baseline: [
      { target: rate, duration: "2m" },
      { target: rate, duration: "10m" },
      { target: 0, duration: "1m" }
    ],
    ramp: [
      { target: rate, duration: "2m" },
      { target: rate * 2, duration: "3m" },
      { target: rate * 3, duration: "3m" },
      { target: 0, duration: "1m" }
    ],
    spike: [
      { target: rate, duration: "1m" },
      { target: rate * 5, duration: "1m" },
      { target: rate, duration: "2m" },
      { target: 0, duration: "1m" }
    ],
    failover: [
      { target: rate, duration: "3m" },
      { target: rate, duration: "10m" },
      { target: 0, duration: "2m" }
    ]
  };

  return profiles[TEST_MODE] || profiles.ramp;
}
