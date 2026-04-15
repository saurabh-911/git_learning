# Train Booking System Backend (Spring Boot)

Production-grade Tatkal-scale backend with layered traffic control, secure virtual waiting room, seat hold expiry, and adaptive protection.

## Core design

1. **Layer-0 (Edge):** Nginx/Cloudflare throttling (`EDGE_THROTTLING.md`)
2. **Layer-1 (Gateway/App):** Redis token-bucket per user/API key
3. **Virtual Waiting Room:** Redis ZSET + priority scoring + queue token
4. **Layer-2 Adaptive Concurrency:** AIMD gate on `/api/book-ticket`
5. **Seat hold + booking transaction:** `AVAILABLE -> HELD -> BOOKED`
6. **Circuit breaker:** fast-fail when DB path degrades
7. **Kafka async side effects only:** booking-confirmed notifications/analytics

## Seat lifecycle (anti-starvation)

- `AVAILABLE`: seat can be held.
- `HELD`: user-exclusive temporary lock for 5 minutes.
- `BOOKED`: terminal state.

Expired holds are automatically released by scheduler.

## Waiting room protections

- FIFO queue via Redis ZSET
- Premium priority (`score = timestamp - priorityWeight`)
- Admitted users receive short-lived queue token (30s)
- Booking is allowed only for admitted users with valid queue token

## APIs

### 1) Hold seat (required before booking)

`POST /api/hold-seat`

```json
{
  "userId": "u-123",
  "seatId": 1001,
  "trainId": "12951"
}
```

Response:

```json
{
  "seatId": 1001,
  "status": "HELD",
  "holdExpiresAt": "2026-04-15T12:00:30Z",
  "message": "Seat held for 5 minutes"
}
```

### 2) Queue status + admission token

`GET /queue-status?userId=u-123`

```json
{
  "userId": "u-123",
  "position": 0,
  "estimatedWaitSeconds": 0,
  "admitted": true,
  "queueToken": "0f9d...",
  "tokenExpiresInSeconds": 30
}
```

### 3) Confirm booking

`POST /api/book-ticket`

```json
{
  "userId": "u-123",
  "seatId": 1001,
  "trainId": "12951",
  "idempotencyKey": "5db364e4-cd36-4c70-b13d-809ecf",
  "queueToken": "0f9d...",
  "userTier": "PREMIUM"
}
```

Notes:
- Same `idempotencyKey` returns same booking response.
- Rejected requests are **never** sent to Kafka.

## Metrics & dashboard

Micrometer/Prometheus metrics emitted:
- `booking.queue.size`
- `booking.adaptive.limit`
- `booking.inflight`
- `booking.success.count`
- `booking.failed.count`

Grafana dashboard template:
- `observability/grafana/train-booking-dashboard.json`

## Dedicated gateway reference

`gateway/application.yml` contains Spring Cloud Gateway + Redis `RequestRateLimiter` configuration for edge deployment.

## Local run

```bash
docker run -p 6379:6379 redis:7
# Start PostgreSQL + Kafka locally
mvn -U clean install
mvn spring-boot:run
```


## MySQL production schema bundle

- Full schema + indexes + constraints + sample dataset + stored procedure:
  - `database/mysql/train_booking_mysql.sql`
- Read/write split operational strategy:
  - `database/mysql/READ_WRITE_SPLIT.md`


## Frontend (Next.js + Three.js)

A modern App Router frontend is available in `frontend/` with:
- Virtual waiting room polling
- 3D seat selection
- Hold + book flow
- Zustand + React Query state/data management
