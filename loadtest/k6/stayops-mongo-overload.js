import { envInt, parseIntCsv } from "./common.js";
import { buildArrivalRateOptions, estimateReservationCreatesForArrivalPlan, runCujIteration, setupCujData } from "./cuj-flow.js";

const OVERLOAD_RATES = parseIntCsv(__ENV.OVERLOAD_RATES || "160,240,320,480");
const OVERLOAD_STAGE_DURATION = `${envInt("OVERLOAD_STAGE_MINUTES", 3)}m`;
const OVERLOAD_PRE_ALLOCATED_VUS = envInt("OVERLOAD_PRE_ALLOCATED_VUS", 150);
const OVERLOAD_MAX_VUS = envInt("OVERLOAD_MAX_VUS", 800);

const stages = OVERLOAD_RATES.map((rate) => ({ target: rate, duration: OVERLOAD_STAGE_DURATION })).concat([
  { target: 0, duration: "1m" },
]);

export const options = buildArrivalRateOptions({
  startRate: OVERLOAD_RATES[0],
  preAllocatedVUs: OVERLOAD_PRE_ALLOCATED_VUS,
  maxVUs: OVERLOAD_MAX_VUS,
  stages,
});

const plannedReservationCreates = estimateReservationCreatesForArrivalPlan({ startRate: OVERLOAD_RATES[0], stages });

export function setup() {
  return setupCujData({ plannedReservationCreates });
}

export default function (data) {
  runCujIteration(data);
}
