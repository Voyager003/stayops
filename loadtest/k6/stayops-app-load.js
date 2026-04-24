import http from "k6/http";
import { check, sleep } from "k6";
import { BASE_URL, envFloat, envInt, pickWeighted } from "./common.js";

const MODE = __ENV.MODE || "app-baseline";
const APP_RATE = envInt("APP_RATE", 5);
const APP_PRE_ALLOCATED_VUS = envInt("APP_PRE_ALLOCATED_VUS", 10);
const APP_MAX_VUS = envInt("APP_MAX_VUS", 50);
const APP_THINK_TIME = envFloat("APP_THINK_TIME", 0.2);
const HEALTH_WEIGHT = envFloat("HEALTH_WEIGHT", 0.7);
const INFO_WEIGHT = envFloat("INFO_WEIGHT", 0.3);
const HEALTH_PATH = __ENV.HEALTH_PATH || "/health";
const INFO_PATH = __ENV.INFO_PATH || "/api/v1/customer/properties";

export const options = buildOptions();

function buildOptions() {
  const thresholds = {
    http_req_failed: ["rate<0.01"],
    checks: ["rate>0.99"],
    "http_req_duration{name:health}": ["p(95)<300"],
    "http_req_duration{name:public-read}": ["p(95)<1500"],
  };

  if (MODE === "smoke") {
    return {
      thresholds,
      scenarios: {
        smoke: {
          executor: "constant-vus",
          vus: 1,
          duration: "30s",
        },
      },
    };
  }

  return {
    thresholds,
    scenarios: {
      app_baseline: {
        executor: "ramping-arrival-rate",
        startRate: 1,
        timeUnit: "1s",
        preAllocatedVUs: APP_PRE_ALLOCATED_VUS,
        maxVUs: APP_MAX_VUS,
        stages: [
          { target: APP_RATE, duration: "2m" },
          { target: APP_RATE, duration: "10m" },
          { target: 0, duration: "1m" },
        ],
      },
    },
  };
}

export default function () {
  const route = pickWeighted([
    { weight: HEALTH_WEIGHT, value: "health" },
    { weight: INFO_WEIGHT, value: "info" },
  ]);

  if (route === "health") {
    const response = http.get(`${BASE_URL}${HEALTH_PATH}`, {
      tags: { name: "health" },
    });
    check(response, {
      "health status is 200": (r) => r.status === 200,
    });
  } else {
    const response = http.get(`${BASE_URL}${INFO_PATH}`, {
      tags: { name: "public-read" },
    });
    check(response, {
      "public read status is 200": (r) => r.status === 200,
    });
  }

  sleep(APP_THINK_TIME);
}
