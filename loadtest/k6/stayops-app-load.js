import http from "k6/http";
import { check, group } from "k6";

const BASE_URL = __ENV.BASE_URL || "https://api.example.com";
const TEST_MODE = __ENV.TEST_MODE || "ramp";
const EXPERIMENT_ID = __ENV.EXPERIMENT_ID || `local-${Date.now()}`;
const LOADTEST_PHASE = __ENV.LOADTEST_PHASE || TEST_MODE;
const LIGHTWEIGHT_RATE = Number(__ENV.LIGHTWEIGHT_RATE || "50");
const BUSINESS_RATE = Number(__ENV.BUSINESS_RATE || "10");

export const options = {
  scenarios: {
    lightweight_http: {
      executor: "ramping-arrival-rate",
      exec: "lightweightHttp",
      startRate: 1,
      timeUnit: "1s",
      preAllocatedVUs: 50,
      maxVUs: 200,
      stages: stagesFor(LIGHTWEIGHT_RATE),
      tags: { test_type: "app_thread_pool" }
    },
    business_read_control: {
      executor: "ramping-arrival-rate",
      exec: "businessReadControl",
      startRate: 1,
      timeUnit: "1s",
      preAllocatedVUs: 20,
      maxVUs: 100,
      stages: stagesFor(BUSINESS_RATE),
      tags: { test_type: "app_with_db_read" }
    }
  },
  thresholds: {
    http_req_failed: ["rate<0.03"],
    "http_req_duration{scenario:lightweight_http}": ["p(95)<300", "p(99)<800"],
    "http_req_duration{scenario:business_read_control}": ["p(95)<800", "p(99)<1500"]
  }
};

export function lightweightHttp() {
  group("spring mvc lightweight endpoint", () => {
    const res = http.get(`${BASE_URL}/actuator/info`, {
      headers: experimentHeaders("lightweight-http"),
      tags: { flow: "lightweight_http" }
    });

    check(res, {
      "actuator info 200": (response) => response.status === 200
    });
  });
}

export function businessReadControl() {
  group("public business read control", () => {
    const res = http.get(`${BASE_URL}/api/v1/customer/properties`, {
      headers: experimentHeaders("business-read-control"),
      tags: { flow: "business_read_control" }
    });

    check(res, {
      "properties list 200": (response) => response.status === 200
    });
  });
}

export function handleSummary(data) {
  return {
    stdout: JSON.stringify({
      metrics: {
        http_req_failed: data.metrics.http_req_failed,
        http_req_duration: data.metrics.http_req_duration,
        dropped_iterations: data.metrics.dropped_iterations,
        checks: data.metrics.checks
      },
      root_group: data.root_group
    }, null, 2),
    [`app-summary-${safeFileName(EXPERIMENT_ID)}.json`]: JSON.stringify(data, null, 2)
  };
}

function experimentHeaders(scenario) {
  return {
    "X-Experiment-Id": EXPERIMENT_ID,
    "X-Loadtest-Phase": LOADTEST_PHASE,
    "X-Loadtest-Scenario": scenario
  };
}

function safeFileName(value) {
  return value.replace(/[^A-Za-z0-9._-]/g, "_").slice(0, 80);
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
    "app-baseline": [
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
    ],
    "failover-steady": [
      { target: rate, duration: "3m" },
      { target: rate, duration: "10m" },
      { target: 0, duration: "2m" }
    ]
  };

  return profiles[TEST_MODE] || profiles.ramp;
}
