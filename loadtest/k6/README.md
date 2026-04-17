# StayOps Load Tests

The load-test suite is split into application and database scenarios.

## Application Thread-Pool Test

`stayops-app-load.js` checks whether the Boot EC2, Nginx, Tomcat/Spring MVC, JVM, and request thread handling become the first bottleneck before MongoDB.

```text
k6 -> Nginx -> StayOps Spring Boot
```

It has two flows:

- `lightweight_http`: calls `/actuator/info` to exercise the HTTP/Spring path with minimal business and DB work.
- `business_read_control`: calls `/api/v1/customer/properties` to compare the same App path with a simple MongoDB-backed read.

Run this before the DB-heavy test:

```bash
BASE_URL=https://api.example.com \
TEST_MODE=baseline \
LIGHTWEIGHT_RATE=50 \
BUSINESS_RATE=10 \
k6 run loadtest/k6/stayops-app-load.js
```

Primary metrics:

- k6: p95/p99 latency, error rate, throughput
- Spring Boot: `http.server.requests`, JVM heap, GC pause, live threads
- Tomcat: busy/current thread metrics, request queue pressure
- Boot EC2: CPU, memory, network, load average

Interpretation:

```text
App CPU/thread metrics saturate while MongoDB stays low:
-> Boot App is the first bottleneck.

App metrics stay healthy while MongoDB CPU/IO/lag rises:
-> MongoDB is the first bottleneck.
```

## MongoDB DB Load Test

`stayops-db-load.js` targets the path that stresses StayOps MongoDB:

```text
k6 -> Nginx -> StayOps Spring Boot -> MongoDB replica set
```

It intentionally excludes Toss Payments and Mock OTA calls from the first DB load-test pass.

## Required Data

Prepare repeatable test data before running k6:

- `PROPERTY_ID`
- `ROOM_TYPE_ID`
- customer account email/password
- room inventory for `CHECK_IN` to `CHECK_OUT`
- active rate plan for the same date range

The existing `scripts/init-dummy-data.js` can be used as a starting point, but verify document class names and IDs before using it against a deployed environment.

## DB Smoke Run

```bash
BASE_URL=https://api.example.com \
TEST_MODE=smoke \
PROPERTY_ID=property-dummy-001 \
ROOM_TYPE_ID=roomtype-dummy-001 \
CUSTOMER_EMAIL=guest@dummy.com \
CUSTOMER_PASSWORD=password123 \
READ_RATE=2 \
WRITE_RATE=1 \
k6 run loadtest/k6/stayops-db-load.js
```

## Load Timing Control

Start time is controlled by when you run the `k6 run` command. Stop time can be controlled with `Ctrl+C`, or by letting the selected `TEST_MODE` finish.

Both scripts support the same `TEST_MODE` values:

| TEST_MODE | Purpose | Load shape |
|---|---|---|
| `smoke` | Deployment/config check before real measurement | 30s at the configured rate, then stop |
| `baseline` | Stable reference point before increasing load | 2m warm-up, 10m steady load, 1m cooldown |
| `ramp` | Gradually find the first bottleneck | 1x, 2x, 3x configured rate, then cooldown |
| `spike` | Short sudden traffic burst | 1x, 5x, 1x configured rate, then cooldown |
| `failover` | Manual MongoDB primary stop/recovery test | 3m warm-up, 10m steady window for failover, 2m cooldown |

For failover testing, start k6 first and wait until the steady window starts. Then stop the current MongoDB primary from another terminal or cloud console, and observe election time, write failures, write concern timeouts, latency, and recovery.

## Prometheus Remote Write

Prometheus in `infra/aws/boot/docker-compose.yml` enables the remote write receiver.

```bash
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
k6 run -o experimental-prometheus-rw loadtest/k6/stayops-db-load.js
```

If k6 runs outside the Boot EC2, use an SSH tunnel or restrict access to the Prometheus port by security group.

## Metrics To Compare

- k6: throughput, p95/p99 latency, error rate
- Spring Boot: endpoint latency, 4xx/5xx, JVM heap, GC pause, request threads, MongoDB connection pool
- MongoDB: primary state, election events, replication lag, opcounters, slow query, write concern timeout
- VM: CPU, memory, disk I/O, network, load average
