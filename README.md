# To-Do

A full-stack to-do app: Spring Boot + PostgreSQL backend, React + TypeScript frontend,
Docker for local infrastructure.

Sign in with **email + password** or **Google**, and your to-dos come with you either way.

---

## Quick start

### Prerequisites

- **Docker Desktop** (running) — provides PostgreSQL
- **JDK 25** — the backend needs it
- **Node 20+** — the frontend needs it

> Check `JAVA_HOME` matches your `java -version`. Maven reads `JAVA_HOME`, not `PATH`,
> so a mismatch fails with `release version 25 not supported`.

```powershell
java -version
node --version
docker ps
```

### 1. Configure secrets

```powershell
copy .env.example .env
```

Fill in `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` and `JWT_SECRET`. The backend reads
this file automatically (see `spring.config.import` in `application.yml`).

**Password login works without any of this.** Google sign-in stays switched off until
real credentials are present, and the UI hides the button rather than showing a dead one.

### 2. Start the database

```powershell
docker compose up -d db
```

### 3. Backend

```powershell
cd backend
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.2"   # if not already set
.\mvnw.cmd spring-boot:run
```

Runs on **http://localhost:8080**.

### 4. Frontend

```powershell
cd frontend
npm install
npm run dev
```

Runs on **http://localhost:5173** — open this one.

### Everything in Docker instead

```powershell
docker compose up --build
```

Same URLs. Postgres, backend and an nginx-served frontend build.

> ⚠️ In this mode the OAuth callback arrives at
> `http://localhost:5173/login/oauth2/code/google`, which needs its **own** entry in the
> Google Cloud console alongside the `localhost:8080` one. Google allows several.

---

## Google Cloud setup

1. [Google Cloud Console](https://console.cloud.google.com) → create a project
2. **APIs & Services** → **OAuth consent screen** → configure, add yourself as a test user
3. **APIs & Services** → **Credentials** → **Create Credentials** → **OAuth client ID**
4. Application type: **Web application**
5. Authorised redirect URI: `http://localhost:8080/login/oauth2/code/google`
6. Copy the Client ID and Secret into `.env`

---

## API

All `/api/todos` routes require the session cookie.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/auth/register` | Create an account |
| `POST` | `/api/auth/login` | Email + password |
| `POST` | `/api/auth/logout` | Clear the cookie |
| `GET` | `/api/auth/me` | Current user |
| `GET` | `/api/auth/providers` | Whether Google is configured |
| `GET` | `/api/todos` | `?search=&status=&priority=&sort=` |
| `POST` | `/api/todos` | Create |
| `GET` | `/api/todos/{id}` | Read |
| `PUT` | `/api/todos/{id}` | Update |
| `PATCH` | `/api/todos/{id}/status?status=` | Change status only |
| `DELETE` | `/api/todos/{id}` | Delete |
| `GET` | `/api/todos/stats` | Counts per bucket |

`sort` accepts `priority` (default), `dueDate`, `createdAt`, `title`.

### Errors

Every failure returns the same shape:

```json
{
  "timestamp": "2026-01-01T00:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/todos",
  "fieldErrors": { "title": "Title is required" }
}
```

---

## Testing

```powershell
cd backend
.\mvnw.cmd test
```

18 integration tests run against a **real PostgreSQL in a Testcontainers container** —
not H2. An in-memory database speaks a different SQL dialect and skips schema creation
entirely, so it would pass tests that fail in production and never exercise the Flyway
migrations.

```powershell
cd frontend
npm run build     # type-checks, then builds
```

---

## Architecture notes

Decisions that are not obvious from reading the code:

**The session is a JWT in an httpOnly cookie**, not in `localStorage`. A cookie marked
httpOnly is invisible to JavaScript, so an XSS bug cannot steal it. `localStorage` is
readable by any script on the page. The cost is that every request needs
`credentials: 'include'`, and CSRF must be handled — see below.

**CSRF is disabled because the cookie is `SameSite=Lax`**, which already stops the
browser attaching it to cross-site state-changing requests. This is only true while the
API and frontend are same-site. If you ever put them on different domains, you need a
real CSRF token scheme (`CookieCsrfTokenRepository`) and you must turn this back on.

**The JWT filter re-reads the user from the database on every request** rather than
trusting the token body. A token stays valid until it expires, so this is how a deleted
or banned account loses access immediately instead of in seven days.

**Every query is scoped by `(id, userId)`**, never `findById`. Someone else's to-do
returns **404, not 403** — a 403 would confirm the id exists.

**Priority sorting uses a `CASE` expression.** The enum is stored as a string, so a plain
`ORDER BY priority` yields `HIGH, LOW, MEDIUM`. That is alphabetically correct and
completely wrong to a human.

**Account linking is gated.** If a Google login's email matches an existing account, the
two are merged so one person sees the same to-dos either way. That is only safe while the
address is verified — Google asserts that, this app never sends verification emails. Set
`ACCOUNT_LINKING_ENABLED=false` before exposing this publicly.

**Vite proxies `/api`, `/oauth2` and `/login`** in development, and nginx does the same
in Docker. The browser therefore stays on a single origin and CORS never enters the
picture.

---

## Layout

```
.
├── docker-compose.yml
├── .env.example
├── backend/
│   ├── Dockerfile              multi-stage: Maven build -> JRE runtime
│   └── src/main/java/com/todoapp/
│       ├── auth/               register, login, logout
│       ├── common/             ApiError + global exception handler
│       ├── config/             security, CORS, cookies, OAuth2
│       ├── domain/             User, Todo, enums
│       ├── repository/
│       ├── security/           JWT, filters, OAuth2 user service
│       └── todo/               CRUD service, Specifications, controller
└── frontend/
    ├── Dockerfile              build -> nginx
    ├── nginx.conf              SPA fallback + API proxy
    └── src/
        ├── components/         TodoDialog, ui/ (shadcn)
        ├── lib/                api client, auth context, types
        └── pages/              LoginPage, TodoPage, OAuthCallbackPage
```

---

## Keyboard

| Key | Action |
|---|---|
| `/` | Focus search |
| `n` | New task |

---

## Troubleshooting

**`release version 25 not supported`** — `JAVA_HOME` points at a different JDK than
`java -version`. Set `JAVA_HOME` to your JDK 25.

**`Google sign-in is DISABLED` in the log** — `.env` was not found or still contains
`PASTE_` placeholders.

**`authorizationUri cannot be empty`** — Google endpoints are missing from the client
registration. See `GoogleOAuth2Config`.

**Port 8080 already in use** — another process holds it. `Get-Process java | Stop-Process`.

**Google returns `redirect_uri_mismatch`** — add the exact URI from the error message to
the OAuth client's redirect URIs.
