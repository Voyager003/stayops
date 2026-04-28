import { envFloat, envInt, parseIntCsv } from "./common.js";
import {
  buildArrivalRateOptions,
  buildDuplicateReservationOptions,
  buildPmsConfirmOptions,
  buildSmokeOptions,
  configureDuplicateReservationResponses,
  runCujIteration,
  runDuplicateReservationIteration,
  runPmsConfirmOnceIteration,
  setupCujData,
} from "./cuj-flow.js";

const MODE = __ENV.MODE || "baseline";
const CUJ_RATE = envInt("CUJ_RATE", 10);
const CUJ_PRE_ALLOCATED_VUS = envInt("CUJ_PRE_ALLOCATED_VUS", 30);
const CUJ_MAX_VUS = envInt("CUJ_MAX_VUS", 150);
const CUJ_STEP_RATES = parseIntCsv(__ENV.CUJ_STEP_RATES || "5,10,20,40,80");
const STRESS_RATE_MULTIPLIER = envFloat("STRESS_RATE_MULTIPLIER", 1.5);
const STRESS_PRE_ALLOCATED_VUS = envInt("STRESS_PRE_ALLOCATED_VUS", CUJ_PRE_ALLOCATED_VUS);
const STRESS_MAX_VUS = envInt("STRESS_MAX_VUS", CUJ_MAX_VUS);
const PMS_CONFIRM_ITERATIONS = envInt("PMS_CONFIRM_ITERATIONS", 100);
const PMS_CONFIRM_VUS = envInt("PMS_CONFIRM_VUS", 10);
const DUPLICATE_RESERVATION_ITERATIONS = envInt("DUPLICATE_RESERVATION_ITERATIONS", 30);
const DUPLICATE_RESERVATION_VUS = envInt("DUPLICATE_RESERVATION_VUS", 5);

if (MODE === "duplicate-reservation") {
  configureDuplicateReservationResponses();
}

export const options = buildOptions();

function buildOptions() {
  if (MODE === "smoke") {
    return buildSmokeOptions();
  }

  if (MODE === "pms-confirm") {
    return buildPmsConfirmOptions({
      iterations: PMS_CONFIRM_ITERATIONS,
      vus: PMS_CONFIRM_VUS,
      maxDuration: "10m",
    });
  }

  if (MODE === "duplicate-reservation") {
    return buildDuplicateReservationOptions({
      iterations: DUPLICATE_RESERVATION_ITERATIONS,
      vus: DUPLICATE_RESERVATION_VUS,
      maxDuration: "10m",
    });
  }

  if (MODE === "stress") {
    const stressRate = Math.ceil(CUJ_RATE * STRESS_RATE_MULTIPLIER);
    return buildArrivalRateOptions({
      startRate: 1,
      preAllocatedVUs: STRESS_PRE_ALLOCATED_VUS,
      maxVUs: STRESS_MAX_VUS,
      stages: [
        { target: stressRate, duration: "5m" },
        { target: stressRate, duration: "35m" },
        { target: 0, duration: "5m" },
      ],
    });
  }

  if (MODE === "step-load") {
    return buildArrivalRateOptions({
      startRate: CUJ_STEP_RATES[0],
      preAllocatedVUs: CUJ_PRE_ALLOCATED_VUS,
      maxVUs: CUJ_MAX_VUS,
      stages: CUJ_STEP_RATES.map((rate) => ({ target: rate, duration: "5m" })).concat([{ target: 0, duration: "1m" }]),
    });
  }

  return buildArrivalRateOptions({
    startRate: 1,
    preAllocatedVUs: CUJ_PRE_ALLOCATED_VUS,
    maxVUs: CUJ_MAX_VUS,
    stages: [
      { target: CUJ_RATE, duration: "2m" },
      { target: CUJ_RATE, duration: "10m" },
      { target: 0, duration: "1m" },
    ],
  });
}

export function setup() {
  return setupCujData();
}

export default function (data) {
  if (MODE === "pms-confirm") {
    runPmsConfirmOnceIteration(data);
    return;
  }

  if (MODE === "duplicate-reservation") {
    runDuplicateReservationIteration(data);
    return;
  }

  runCujIteration(data);
}
