import { envInt, parseIntCsv } from "./common.js";
import { buildArrivalRateOptions, runCujIteration, setupCujData } from "./cuj-flow.js";

const BREAKPOINT_RATES = parseIntCsv(__ENV.BREAKPOINT_RATES || "20,40,80,120,160,220");
const BREAKPOINT_STAGE_DURATION = `${envInt("BREAKPOINT_STAGE_MINUTES", 5)}m`;
const BREAKPOINT_PRE_ALLOCATED_VUS = envInt("BREAKPOINT_PRE_ALLOCATED_VUS", 80);
const BREAKPOINT_MAX_VUS = envInt("BREAKPOINT_MAX_VUS", 400);

export const options = buildArrivalRateOptions({
  startRate: BREAKPOINT_RATES[0],
  preAllocatedVUs: BREAKPOINT_PRE_ALLOCATED_VUS,
  maxVUs: BREAKPOINT_MAX_VUS,
  stages: BREAKPOINT_RATES.map((rate) => ({ target: rate, duration: BREAKPOINT_STAGE_DURATION })).concat([
    { target: 0, duration: "1m" },
  ]),
});

export function setup() {
  return setupCujData();
}

export default function (data) {
  runCujIteration(data);
}
