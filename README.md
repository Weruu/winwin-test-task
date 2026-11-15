# WinWin.travel – Backend Test Task

Two Spring Boot services and PostgreSQL, started together with Docker Compose.

| Service  | Host port | Description |
|----------|-----------|-------------|
| `auth-api` | 8080 | Registration, login (JWT) and the protected `/api/process` endpoint. Stores a processing log in Postgres. |
| `data-api` | 8081 | `/api/transform` – reverses the text and uppercases it. Accepts only requests with a valid `X-Internal-Token`. |
| `postgres` | – | Stores `users` and `processing_log`. The schema is created by Flyway. |

**Stack:** Java 17, Spring Boot 4.1.2 (Web, Security, Data JPA, Validation), Flyway, PostgreSQL 16, JJWT, Maven, Docker.

## Architecture

```
client ──► auth-api :8080 ──► data-api :8081
               │
               ▼
           postgres
```

All containers are in one Docker network (`backend`). `auth-api` reaches `data-api` at `http://data-api:8081` (Docker DNS), and Postgres at `postgres:5432`.

Request flow for `POST /api/process`:
1. `auth-api` validates the JWT from the `Authorization: Bearer <token>` header.
2. `auth-api` calls `data-api` `POST /api/transform` with the `X-Internal-Token` header.
3. `data-api` checks the token, transforms the text and returns `{ "result": "..." }`.
4. `auth-api` saves `(user_id, input_text, output_text, created_at)` into `processing_log` and returns the result to the client.

## Requirements

- Docker with Docker Compose v2

Java and Maven are **not** required on the host: both services are built inside Docker (multi-stage Dockerfiles).

## Quick start

```bash
git clone <https://github.com/weruu/winwin-test-task.git>
cd winwin-test-task
docker compose up -d --build
docker compose ps
```

The first build takes several minutes (base images and Maven dependencies are downloaded). Later builds are much faster thanks to Docker layer caching.

The services are ready when `docker compose ps` shows all three containers as running and `docker compose logs auth-api` contains `Started AuthApiApplication`.

Stop: `docker compose down`. Stop and delete the database data: `docker compose down -v`.

## Configuration

Everything is configured with environment variables. `docker-compose.yml` has development defaults, so the project starts without any extra files. To override them, copy `.env.example` to `.env` and edit it.

| Variable | Used by | Description |
|----------|---------|-------------|
| `POSTGRES_URL` | auth-api | JDBC URL (set in compose: `jdbc:postgresql://postgres:5432/appdb`) |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | auth-api, postgres | Database credentials |
| `POSTGRES_DB` | postgres, compose | Database name (default `appdb`) |
| `JWT_SECRET` | auth-api | HMAC key for signing JWTs, **at least 32 characters** |
| `INTERNAL_TOKEN` | auth-api, data-api | Shared secret sent in the `X-Internal-Token` header |

> The defaults in `docker-compose.yml` are for local development only. Real secrets must never be committed; `.env` is in `.gitignore`.

## API

### Register
```bash
curl -i -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"a@a.com","password":"pass"}'
```
- `201 Created` → `{ "id": "<uuid>", "email": "a@a.com" }`
- `400` – invalid email or password (length 4–72)
- `409` – email is already registered

### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"a@a.com","password":"pass"}'
```
- `200 OK` → `{ "token": "<jwt>" }`
- `401` – wrong email or password (same message for both cases)

### Process (JWT required)
```bash
curl -X POST http://localhost:8080/api/process \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"text":"hello"}'
```
- `200 OK` → `{ "result": "OLLEH" }`
- `400` – empty text or text longer than 10 000 characters
- `401` – missing, invalid or expired token
- `502` – `data-api` is unreachable or returned an invalid response

### data-api directly (internal endpoint)
```bash
# No token -> 403
curl -i -X POST http://localhost:8081/api/transform \
  -H "Content-Type: application/json" -d '{"text":"hello"}'

# Valid token -> 200 {"result":"OLLEH"}
curl -X POST http://localhost:8081/api/transform \
  -H "X-Internal-Token: dev-internal-token-change-me" \
  -H "Content-Type: application/json" -d '{"text":"hello"}'
```
Without a valid `X-Internal-Token` the service always answers `403`.

### Check the database
```bash
docker compose exec postgres psql -U appuser -d appdb \
  -c "SELECT user_id, input_text, output_text, created_at FROM processing_log;"

docker compose exec postgres psql -U appuser -d appdb \
  -c "SELECT email, password_hash FROM users;"
```
`password_hash` must be a BCrypt hash (`$2a$10$...`), never the plain password.

## Data model

Created by the Flyway migration `auth-api/src/main/resources/db/migration/V1__init.sql`:

- `users` – `id` (UUID, PK), `email` (unique), `password_hash`
- `processing_log` – `id` (UUID, PK), `user_id` (FK → users), `input_text`, `output_text`, `created_at` (timestamptz), index on `user_id`

## Implementation notes and gotchas

**Security**
- Passwords are hashed with BCrypt. Passwords and tokens are never written to logs.
- The password length is limited to **72 characters**, because BCrypt only uses the first 72 bytes and would silently ignore the rest.
- Emails are trimmed and lower-cased, so `A@a.com` and `a@a.com` are the same user.
- Login returns the same `401` message for an unknown email and for a wrong password, so the API cannot be used to find out which emails are registered.
- JWT: HS256, lifetime 1 hour, user id in the `sub` claim. Authentication is stateless (no server sessions), so CSRF protection is disabled on purpose.
- `data-api` validates `X-Internal-Token` in a servlet **filter**, which runs before request-body validation. A request without a valid token gets `403` even if its body is garbage. The comparison is constant-time (`MessageDigest.isEqual`).
- Spring Boot normally generates a default user with a random password and prints it to the log. This is disabled with a dummy `UserDetailsService`, because a secret in the logs is exactly what the task forbids.

**Application behaviour**
- Hibernate runs with `ddl-auto: validate`: it never changes the schema, Flyway does.
- `/error` is explicitly allowed in the security config. Otherwise Spring Security intercepts Spring's internal error dispatch and replaces real errors (`409`, `400`) with an empty `401`.
- The email uniqueness is checked twice: by a query and by the unique DB constraint (a concurrent duplicate registration is still turned into `409`).
- The `auth-api` → `data-api` HTTP client has timeouts (3 s connect, 5 s read); failures are mapped to `502` instead of hanging or leaking a stack trace.
- A log record is saved only after `data-api` answered successfully.

**Docker**
- Multi-stage Dockerfiles: Maven image for the build, a slim JRE image for runtime. Dependencies are downloaded in a separate layer, so source changes rebuild quickly.
- The applications run as a non-root user.
- `auth-api` waits for a **healthy** Postgres (`healthcheck` + `depends_on: condition: service_healthy`), not just a started container. Without it the app can crash on startup while the database is still initialising.
- Postgres data lives in the named volume `pgdata` and **survives** `docker compose down`. Use `docker compose down -v` to start from an empty database.
- `data-api` is published on `localhost:8081` because the task requires it. In a real deployment it would only be reachable inside the Docker network.
- Config via environment variables is read at start: after changing `.env`, run `docker compose up -d` again so the containers are recreated.


## Troubleshooting

| Problem | What to do |
|---------|-----------|
| `port is already allocated` | Something already uses 8080, 8081 or 5432. Stop it or change the left side of the port mapping in `docker-compose.yml`. |
| `auth-api` keeps restarting | `docker compose logs auth-api`. Usual causes: missing/short `JWT_SECRET` (min. 32 chars), wrong DB credentials after changing `.env` (run `docker compose down -v` once). |
| Changed code but nothing changed | Rebuild: `docker compose up -d --build`. |
| `401` on `/api/process` | Token is missing, malformed or expired (1 hour). Log in again. |
| `502` on `/api/process` | `data-api` is down, or `INTERNAL_TOKEN` differs between the services. Check `docker compose logs data-api`. |
| Want a clean start | `docker compose down -v && docker compose up -d --build` |

## Known limitations (kept simple on purpose)

- No refresh tokens or token revocation: a JWT is valid until it expires.
- No rate limiting or account lockout on login.
- No endpoint to read the processing history; it is only stored in the database.
- No automated tests; the behaviour is verified with the curl scenarios above.
- HTTP only: TLS is expected to be terminated by a reverse proxy in a real deployment.
- The transform logic (reverse + uppercase) is intentionally trivial.

## Project structure

```
auth-api/              Service A (Spring Boot): auth, JWT, /api/process, JPA, Flyway
data-api/              Service B (Spring Boot): /api/transform, internal token filter
docker-compose.yml     postgres + auth-api + data-api in one network
.env.example           example environment configuration
```
