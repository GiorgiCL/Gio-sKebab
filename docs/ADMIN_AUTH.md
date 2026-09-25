# Owner authentication

V1 has one owner account, no public registration, and no customer accounts. The backend uses Spring Security's password authentication and server-side HTTP sessions because the planned admin UI is first-party. Flyway V2 creates `admin_account` with a database-enforced ID of 1, unique normalized email, BCrypt password hash, enabled flag, and timestamps. No account or password is seeded. Hibernate still validates schema rather than creating it.

## First account

An operator provisions the account exactly once using the application in non-web mode. Set the normal database environment variables for the selected profile, plus `ADMIN_BOOTSTRAP_EMAIL` and `ADMIN_BOOTSTRAP_PASSWORD` in the process environment. Supply the password through the deployment secret facility or an interactive shell prompt; do not put it in a command argument, script, log, or committed file. Then run the built jar with `--spring.profiles.active=prod --spring.main.web-application-type=none --app.admin.bootstrap=true`. The same command can use `local` for a local database. The command exits after creating the account; an existing account causes failure and is never overwritten. Concurrent attempts are additionally stopped by the database primary key. Clear the bootstrap environment variables afterward. Record the owner-controlled recovery email and credential handover before launch. Password reset and recovery are deferred; until built, account recovery requires a controlled operator procedure.

## HTTP contract

| Request | Behavior |
| --- | --- |
| `GET /api/admin/auth/csrf` | Anonymous allowed. Returns `headerName` and `token`, initializing a session token. Send the returned header with state-changing requests and the same session cookie. |
| `POST /api/admin/auth/login` | JSON `email` and `password`, CSRF required. Returns 200 with `{ "email": "..." }` and an authenticated session; wrong passwords, unknown accounts, and disabled accounts return the same 401 message. |
| `GET /api/admin/auth/me` | Requires the authenticated session; returns the account email, otherwise 401. |
| `POST /api/admin/auth/logout` | CSRF required. Invalidates the session, clears its cookie, and returns 204. |

Only the three documented public restaurant GET routes, public menu GET, and anonymous CSRF/login are publicly permitted. Other `/api/admin/**` requests require authentication; other routes remain denied by default. Owner content routes are documented in [RESTAURANT_SLICE.md](RESTAURANT_SLICE.md) and [MENU_SLICE.md](MENU_SLICE.md). Malformed, blank, or oversized login fields return a generic 400 problem response without echoing credentials. Passwords are limited to 72 UTF-8 bytes so BCrypt never silently truncates them. Spring Security's standard headers remain enabled. No password, hash, or session ID is returned by the API.

## Browser and deployment model

The session cookie is HttpOnly, SameSite=Lax, and expires after 30 minutes of inactivity. It is Secure in the `prod` profile; local HTTP development uses a non-Secure cookie. Deploy production over HTTPS. Login changes the session ID to prevent session fixation. Protected admin requests recheck that the account still exists and is enabled; disabling it invalidates an existing session. Spring Security's session-backed CSRF repository protects login, logout, and future writes; JavaScript obtains a token from the CSRF endpoint and sends it in `X-CSRF-TOKEN`. Never place credentials or CSRF tokens in URLs. Logout invalidates the session and CSRF state.

Same-origin requests need no CORS configuration. For a separate frontend origin, set `ADMIN_CORS_ALLOWED_ORIGINS` to a comma-separated list of exact `http://` or `https://` origins; wildcard origins are rejected, and production accepts only HTTPS origins. Credentialed requests, GET/POST/PUT/DELETE, and only `Content-Type` and `X-CSRF-TOKEN` request headers are allowed. Use a same-site frontend deployment where possible; SameSite=Lax cookies will not support a cross-site admin frontend. Review the final frontend origin and proxy setup before deployment.

The application has no in-process login throttle. A bounded login rate limit at the deployment proxy is mandatory before public launch; it must cover the login route without relying on client-supplied IP headers. The proxy must also cap request body size so oversized JSON is rejected before parsing. This avoids a single-process limiter that would reset on restart or diverge across instances. Password reset, MFA, remember-me, OAuth, and richer audit logging remain deferred. Test quickly with `.\mvnw.cmd test`; run PostgreSQL migration/constraint validation with `.\mvnw.cmd verify` and a Docker-compatible runtime.
