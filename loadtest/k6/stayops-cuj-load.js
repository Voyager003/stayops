import { envFloat, envInt, parseIntCsv } from "./common.js";
import {
  buildArrivalRateOptions,
  buildDuplicateReservationOptions,
  buildMyReservationsOptions,
  buildPmsConfirmOptions,
  buildSmokeOptions,
  configureDuplicateReservationResponses,
  estimateReservationCreatesForArrivalPlan,
  estimateReservationCreatesForIterationCount,
  runCujIteration,
  runDuplicateReservationIteration,
  runMyReservationsIteration,
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
const SMOKE_EXPECTED_ITERATIONS = envInt("SMOKE_EXPECTED_ITERATIONS", 100);
const MY_RESERVATIONS_RATE_ONLY = envInt("MY_RESERVATIONS_RATE_ONLY", CUJ_RATE);
const MY_RESERVATIONS_PRE_ALLOCATED_VUS = envInt("MY_RESERVATIONS_PRE_ALLOCATED_VUS", CUJ_PRE_ALLOCATED_VUS);
const MY_RESERVATIONS_MAX_VUS = envInt("MY_RESERVATIONS_MAX_VUS", CUJ_MAX_VUS);

if (MODE === "duplicate-reservation") {
  configureDuplicateReservationResponses();
}

const RUN_PLAN = buildRunPlan();

export const options = RUN_PLAN.options;

function buildRunPlan() {
  if (MODE === "smoke") {
    return {
      options: buildSmokeOptions(),
      plannedReservationCreates: estimateReservationCreatesForIterationCount(SMOKE_EXPECTED_ITERATIONS),
    };
  }

  if (MODE === "pms-confirm") {
    return {
      options: buildPmsConfirmOptions({
        iterations: PMS_CONFIRM_ITERATIONS,
        vus: PMS_CONFIRM_VUS,
        maxDuration: "10m",
      }),
      plannedReservationCreates: 0,
    };
  }

  if (MODE === "duplicate-reservation") {
    return {
      options: buildDuplicateReservationOptions({
        iterations: DUPLICATE_RESERVATION_ITERATIONS,
        vus: DUPLICATE_RESERVATION_VUS,
        maxDuration: "10m",
      }),
      plannedReservationCreates: DUPLICATE_RESERVATION_ITERATIONS,
    };
  }

  if (MODE === "my-reservations") {
    return {
      options: buildMyReservationsOptions({
        rate: MY_RESERVATIONS_RATE_ONLY,
        preAllocatedVUs: MY_RESERVATIONS_PRE_ALLOCATED_VUS,
        maxVUs: MY_RESERVATIONS_MAX_VUS,
      }),
      plannedReservationCreates: 0,
    };
  }

  if (MODE === "stress") {
    const stressRate = Math.ceil(CUJ_RATE * STRESS_RATE_MULTIPLIER);
    const stages = [
      { target: stressRate, duration: "5m" },
      { target: stressRate, duration: "35m" },
      { target: 0, duration: "5m" },
    ];
    return {
      options: buildArrivalRateOptions({
        startRate: 1,
        preAllocatedVUs: STRESS_PRE_ALLOCATED_VUS,
        maxVUs: STRESS_MAX_VUS,
        stages,
      }),
      plannedReservationCreates: estimateReservationCreatesForArrivalPlan({ startRate: 1, stages }),
    };
  }

  if (MODE === "step-load") {
    const stages = CUJ_STEP_RATES.map((rate) => ({ target: rate, duration: "5m" })).concat([{ target: 0, duration: "1m" }]);
    return {
      options: buildArrivalRateOptions({
        startRate: CUJ_STEP_RATES[0],
        preAllocatedVUs: CUJ_PRE_ALLOCATED_VUS,
        maxVUs: CUJ_MAX_VUS,
        stages,
      }),
      plannedReservationCreates: estimateReservationCreatesForArrivalPlan({ startRate: CUJ_STEP_RATES[0], stages }),
    };
  }

  const stages = [
    { target: CUJ_RATE, duration: "2m" },
    { target: CUJ_RATE, duration: "10m" },
    { target: 0, duration: "1m" },
  ];
  return {
    options: buildArrivalRateOptions({
      startRate: 1,
      preAllocatedVUs: CUJ_PRE_ALLOCATED_VUS,
      maxVUs: CUJ_MAX_VUS,
      stages,
    }),
    plannedReservationCreates: estimateReservationCreatesForArrivalPlan({ startRate: 1, stages }),
  };
}

export function setup() {
  return setupCujData({ plannedReservationCreates: RUN_PLAN.plannedReservationCreates });
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

  if (MODE === "my-reservations") {
    runMyReservationsIteration(data);
    return;
  }

  runCujIteration(data);
}
