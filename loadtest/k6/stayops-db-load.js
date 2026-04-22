import http from "k6/http";
import { check, fail, sleep } from "k6";
import {
  BASE_URL,
  buildStayWindow,
  envFloat,
  envInt,
  normalizeWeights,
  parseCsv,
  pickRandom,
  pickWeighted,
} from "./common.js";

const MODE = __ENV.MODE || "db-baseline";

const SEARCH_RATE = envFloat("SEARCH_RATE", 0.5);
const DETAIL_RATE = envFloat("DETAIL_RATE", 0.2);
const OFFERS_RATE = envFloat("OFFERS_RATE", 0.3);

const HOT_PROPERTY_COUNT = envInt("HOT_PROPERTY_COUNT", 5);
const DB_RATE = envInt("DB_RATE", 12);
const DB_PRE_ALLOCATED_VUS = envInt("DB_PRE_ALLOCATED_VUS", 20);
const DB_MAX_VUS = envInt("DB_MAX_VUS", 100);
const DB_THINK_TIME = envFloat("DB_THINK_TIME", 0.5);

const DB_RAMP_START = envInt("DB_RAMP_START", 8);
const DB_RAMP_STEP_1 = envInt("DB_RAMP_STEP_1", 16);
const DB_RAMP_STEP_2 = envInt("DB_RAMP_STEP_2", 24);
const DB_RAMP_STEP_3 = envInt("DB_RAMP_STEP_3", 32);

const CHECK_IN_OFFSETS = parseCsv(__ENV.CHECK_IN_OFFSETS || "3,5,7,14,21,30").map((v) => Number.parseInt(v, 10));
const NIGHTS_POOL = parseCsv(__ENV.NIGHTS_POOL || "1,2,3").map((v) => Number.parseInt(v, 10));
const GUESTS_POOL = parseCsv(__ENV.GUESTS_POOL || "1,2,3,4").map((v) => Number.parseInt(v, 10));

const NORMALIZED = normalizeWeights(SEARCH_RATE, DETAIL_RATE, OFFERS_RATE);

export const options = buildOptions();

function buildOptions() {
  const thresholds = {
    http_req_failed: ["rate<0.01"],
    checks: ["rate>0.99"],
    "http_req_duration{name:customer-properties-list}": ["p(95)<1500"],
    "http_req_duration{name:customer-property-detail}": ["p(95)<1000"],
    "http_req_duration{name:customer-room-types}": ["p(95)<1000"],
    "http_req_duration{name:customer-property-offers}": ["p(95)<2000"],
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

  if (MODE === "db-ramp") {
    return {
      thresholds,
      scenarios: {
        db_read_mix: {
          executor: "ramping-arrival-rate",
          startRate: DB_RAMP_START,
          timeUnit: "1s",
          preAllocatedVUs: DB_PRE_ALLOCATED_VUS,
          maxVUs: DB_MAX_VUS,
          stages: [
            { target: DB_RAMP_START, duration: "2m" },
            { target: DB_RAMP_STEP_1, duration: "5m" },
            { target: DB_RAMP_STEP_2, duration: "5m" },
            { target: DB_RAMP_STEP_3, duration: "5m" },
            { target: 0, duration: "1m" },
          ],
        },
      },
    };
  }

  return {
    thresholds,
    scenarios: {
      db_read_mix: {
        executor: "ramping-arrival-rate",
        startRate: 1,
        timeUnit: "1s",
        preAllocatedVUs: DB_PRE_ALLOCATED_VUS,
        maxVUs: DB_MAX_VUS,
        stages: [
          { target: DB_RATE, duration: "2m" },
          { target: DB_RATE, duration: "10m" },
          { target: 0, duration: "1m" },
        ],
      },
    },
  };
}

export function setup() {
  const response = http.get(`${BASE_URL}/api/v1/customer/properties`, {
    tags: { name: "customer-properties-list-setup" },
  });

  const checks = check(response, {
    "setup property list status is 200": (r) => r.status === 200,
  });

  if (!checks) {
    fail(`Setup failed. property list status=${response.status}`);
  }

  const properties = response.json();
  if (!Array.isArray(properties) || properties.length === 0) {
    fail("Setup failed. property list is empty.");
  }

  const configuredHotIds = parseCsv(__ENV.HOT_PROPERTY_IDS);
  const hotPropertyIds =
    configuredHotIds.length > 0
      ? configuredHotIds
      : properties
          .slice(0, HOT_PROPERTY_COUNT)
          .map((property) => property.id)
          .filter(Boolean);

  if (hotPropertyIds.length === 0) {
    fail("Setup failed. no hot property ids available.");
  }

  return {
    hotPropertyIds,
    totalProperties: properties.length,
  };
}

export default function (data) {
  const flow = pickWeighted([
    { weight: NORMALIZED.search, value: "search" },
    { weight: NORMALIZED.detail, value: "detail" },
    { weight: NORMALIZED.offers, value: "offers" },
  ]);

  if (flow === "search") {
    runSearchFlow();
  } else if (flow === "detail") {
    runDetailFlow(data.hotPropertyIds);
  } else {
    runOffersFlow(data.hotPropertyIds);
  }

  sleep(DB_THINK_TIME);
}

function runSearchFlow() {
  const response = http.get(`${BASE_URL}/api/v1/customer/properties`, {
    tags: { name: "customer-properties-list" },
  });

  check(response, {
    "property list status is 200": (r) => r.status === 200,
    "property list returns array": (r) => Array.isArray(r.json()),
  });
}

function runDetailFlow(hotPropertyIds) {
  const propertyId = pickRandom(hotPropertyIds);

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
    "room types returns array": (r) => Array.isArray(r.json()),
  });
}

function runOffersFlow(hotPropertyIds) {
  const propertyId = pickRandom(hotPropertyIds);
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
    "offers returns array": (r) => Array.isArray(r.json()),
  });
}
