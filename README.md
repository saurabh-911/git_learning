# Train Booking System Backend (Spring Boot)

Production-style backend reference for Tatkal-scale spikes with **3-layer traffic control**, a **Redis virtual waiting room**, and **adaptive concurrency**.

## Architecture

1. **Layer-0 (Edge, conceptual):** Nginx/Cloudflare IP throttling (`EDGE_THROTTLING.md`)
2. **Layer-1 (Gateway/App):** Token Bucket rate limiting using Redis
3. **Virtual Waiting Room:** Redis ZSET queue + scheduler releases users/sec
4. **Layer-2 Adaptive Concurrency:** AIMD concurrency limiter on `/api/book-ticket`
5. **Core Booking:** transactional, lock-based seat allocation + idempotency
6. **Kafka Async:** emits only booking-confirmed events

## Project structure

- `controller/` REST APIs (`/api/book-ticket`, `/queue-status`)
- `service/` business and control logic
- `queue/` waiting room + scheduler
- `filter/` rate limit and adaptive concurrency filters
- `entity/repository/` persistence model
- `kafka/` event producer/consumer
- `config/` app, Redis, Kafka, tunables
- `gateway/` dedicated Spring Cloud Gateway deployment config (reference)

## Request flow

1. `POST /api/book-ticket`
2. Token bucket checks user/API key
3. User is queued in Redis ZSET (`booking_queue`)
4. Scheduler releases top N users/sec into `booking_admitted`
5. Adaptive concurrency gate checks current in-flight limit
6. Transaction books seat with pessimistic lock and idempotency
7. Publish Kafka `booking-confirmed` event

## API examples

### Book ticket

`POST /api/book-ticket`

```json
{
  "userId": "u-123",
  "seatId": 1001,
  "trainId": "12951",
  "idempotencyKey": "a4f72bd9-8d43-4f2b-9f79-f325"
}
```

Queued response (HTTP 202 from exception handler):

```json
{ "error": "User is in waiting room. Poll /queue-status." }
```

Success:

```json
{ "bookingId": 4412, "status": "CONFIRMED", "message": "Seat booked successfully" }
```

### Queue status

`GET /queue-status?userId=u-123`

```json
{ "userId": "u-123", "position": 312, "estimatedWaitSeconds": 2, "admitted": false }
```

## Run locally

```bash
docker run -p 6379:6379 redis:7
# Start PostgreSQL and Kafka locally (or via docker-compose)
mvn spring-boot:run
```

## Notes

- Rejected booking requests are **not sent to Kafka**.
- Queue is admission control only; booking stays synchronous + consistent.
- Tune `traffic-control.*` in `application.yml` for spike profiles.
