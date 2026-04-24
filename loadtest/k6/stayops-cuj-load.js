import { envInt, parseIntCsv } from "./common.js";
import { buildArrivalRateOptions, buildSmokeOptions, runCujIteration, setupCujData } from "./cuj-flow.js";

const MODE = __ENV.MODE || "baseline";
const CUJ_RATE = envInt("CUJ_RATE", 10);
const CUJ_PRE_ALLOCATED_VUS = envInt("CUJ_PRE_ALLOCATED_VUS", 30);
const CUJ_MAX_VUS = envInt("CUJ_MAX_VUS", 150);
const CUJ_STEP_RATES = parseIntCsv(__ENV.CUJ_STEP_RATES || "5,10,20,40,80");

export const options = buildOptions();

function buildOptions() {
  if (MODE === "smoke") {
    return buildSmokeOptions();
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
  runCujIteration(data);
}
