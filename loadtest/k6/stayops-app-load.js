import http from "k6/http";
import { check, group, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "https://api.example.com";
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
      stages: [
        { target: LIGHTWEIGHT_RATE, duration: "2m" },
        { target: LIGHTWEIGHT_RATE * 2, duration: "3m" },
        { target: LIGHTWEIGHT_RATE * 3, duration: "3m" },
        { target: 0, duration: "1m" }
      ],
      tags: { test_type: "app_thread_pool" }
    },
    business_read_control: {
      executor: "ramping-arrival-rate",
      exec: "businessReadControl",
      startRate: 1,
      timeUnit: "1s",
      preAllocatedVUs: 20,
      maxVUs: 100,
      stages: [
        { target: BUSINESS_RATE, duration: "2m" },
        { target: BUSINESS_RATE * 2, duration: "3m" },
        { target: BUSINESS_RATE * 3, duration: "3m" },
        { target: 0, duration: "1m" }
      ],
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
      tags: { flow: "lightweight_http" }
    });

    check(res, {
      "actuator info 200": (response) => response.status === 200
    });
  });

  sleep(1);
}

export function businessReadControl() {
  group("public business read control", () => {
    const res = http.get(`${BASE_URL}/api/v1/customer/properties`, {
      tags: { flow: "business_read_control" }
    });

    check(res, {
      "properties list 200": (response) => response.status === 200
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
    "app-summary.json": JSON.stringify(data, null, 2)
  };
}
