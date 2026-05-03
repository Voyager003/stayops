# StayOps infrastructure layout

## production

`infra/production` keeps the high-availability layout used for production-like
operation and failover testing.

- `app`: app, Redis, Nginx, Mock OTA, and optional observability.
- `mongodb-rss`: three-node MongoDB replica set, exporter, node exporter, and promtail.

Use the app observability stack only when it is needed:

```bash
cd infra/production/app
cp env.example .env
docker compose --env-file .env -f docker-compose.yml -f docker-compose.observability.yml up -d
```

For normal app runtime without Grafana, Prometheus, Loki, Promtail, or
node-exporter:

```bash
cd infra/production/app
cp env.example .env
docker compose --env-file .env -f docker-compose.yml up -d
```

## minimal

`infra/minimal` keeps the low-cost runtime layout used when the service only
needs to stay functional after load testing.

- `app`: app, Redis, Nginx, Mock OTA, and Mock OTA MongoDB.
- `mongodb-single-rs`: one MongoDB node configured as a single-node replica set.

The minimal MongoDB is intentionally not a standalone server. StayOps uses
MongoDB transactions through `MongoTransactionManager`, so the low-cost mode
still keeps `replicaSet=rs0` while removing the secondary nodes.

Minimal MongoDB setup:

```bash
cd infra/minimal/mongodb-single-rs
cp env.example .env
docker compose --env-file .env up -d
./bootstrap-single-replica-set.sh .env
./provision-mongo-users.sh .env
```

Minimal app setup:

```bash
cd infra/minimal/app
cp env.example .env
docker compose --env-file .env up -d
```
