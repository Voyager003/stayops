import http from "k6/http";
import { Counter } from "k6/metrics";
import { check } from "k6";
import { envInt } from "./common.js";
import {
  buildThresholds,
  estimateReservationCreatesForArrivalPlan,
  runCujIteration,
  setupCujData,
} from "./cuj-flow.js";

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 399 }, 400, 401, 429));

const CUJ_RATE = envInt("CUJ_RATE", 10);
const CUJ_PRE_ALLOCATED_VUS = envInt("CUJ_PRE_ALLOCATED_VUS", 30);
const CUJ_MAX_VUS = envInt("CUJ_MAX_VUS", 150);
const RATE_LIMIT_VUS = envInt("RATE_LIMIT_VUS", 5);
const RATE_LIMIT_DURATION_SECONDS = envInt("RATE_LIMIT_DURATION_SECONDS", 120);

const CUJ_STAGES = [
  { target: CUJ_RATE, duration: "2m" },
  { target: CUJ_RATE, duration: "8m" },
  { target: 0, duration: "1m" },
];

const rateLimitLogin429 = new Counter("rate_limit_login_429");

export const options = {
  thresholds: {
    ...buildThresholds(),
    rate_limit_login_429: ["count>0"],
    "http_req_duration{name:rate-limit-login}": ["p(95)<1500"],
  },
  scenarios: {
    graceful_cuj: {
      executor: "ramping-arrival-rate",
      startRate: 1,
      timeUnit: "1s",
      preAllocatedVUs: CUJ_PRE_ALLOCATED_VUS,
      maxVUs: CUJ_MAX_VUS,
      stages: CUJ_STAGES,
      exec: "gracefulCuj",
    },
    rate_limit_login_burst: {
      executor: "constant-vus",
      vus: RATE_LIMIT_VUS,
      duration: `${RATE_LIMIT_DURATION_SECONDS}s`,
      exec: "rateLimitLogin",
    },
  },
};

export function setup() {
  const plannedReservationCreates = estimateReservationCreatesForArrivalPlan({
    startRate: 1,
    stages: CUJ_STAGES,
  });

  return setupCujData({ plannedReservationCreates });
}

export function gracefulCuj(data) {
  runCujIteration(data);
}

export function rateLimitLogin() {
  const response = http.post(
    `${__ENV.BASE_URL}/api/v1/customer/auth/login`,
    JSON.stringify({
      email: "not-exist@example.com",
      password: "wrong-password",
    }),
    {
      headers: { "Content-Type": "application/json" },
      tags: { name: "rate-limit-login" },
    }
  );

  if (response.status === 429) {
    rateLimitLogin429.add(1);
  }

  check(response, {
    "login burst returns auth failure or rate limit": (r) =>
      r.status === 400 || r.status === 401 || r.status === 429,
    "429 includes retry-after": (r) =>
      r.status !== 429 || r.headers["Retry-After"] !== undefined,
  });
}
