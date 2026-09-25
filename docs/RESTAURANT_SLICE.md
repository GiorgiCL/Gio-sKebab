# Restaurant profile and opening hours

## Schema and ownership

Flyway migration `V1__restaurant_and_opening_hours.sql` creates an explicit `restaurant_profile` table and two schedule tables. Profile ID `1` is the only permitted row; this deployment serves one restaurant and has no tenant model. No production business content is seeded. The profile stores display details, address/contact, HTTPS map and external ordering links, optional Instagram/Facebook links, and creation/update instants. The weekday and special-date columns are primary keys, so each weekday and calendar date has at most one rule. Schedule tables have no restaurant foreign key because there can only be one profile. Their primary keys support the current lookups without extra indexes.

Database checks require a valid weekday and either a closed day with null times or an open day with both times and `closing_time > opening_time`. Java constructors enforce the interval rule. Overnight hours and split shifts are deliberately unsupported in V1; a closing time at or before the opening time is rejected. A future schema/design change must define how an overnight interval interacts with a special override on the following date before enabling it. String lengths follow ordinary contact/URL storage bounds; URLs accepted through the entity constructor must be HTTPS links with a host, and optional email is checked by Bean Validation when persisted. Future admin request DTOs will need equivalent input validation.

## Time and schedule semantics

Set `RESTAURANT_TIME_ZONE` to a Java/IANA zone ID; the default is `Europe/Vilnius`. Startup fails with a message naming `restaurant.time-zone` for an invalid zone. The service uses an injected `Clock`, converts its instant into that zone, then checks the special override for the resulting local date. A special rule wins over the weekday rule. Opening is inclusive; closing is exclusive. `closedToday` means the selected rule marks the whole day closed; `openNow` may be false before opening or after closing even when `closedToday` is false.

A complete weekly schedule has seven rows, including explicit closed days. When the profile does not exist, all three public endpoints return 404. When a profile exists but the weekly schedule is incomplete, opening hours and status return 503 rather than claiming the restaurant is closed. These errors are written as problem responses so servlet error dispatch does not replace the intended status with a security denial. This is an initial content configuration state; no public or insecure admin mutation endpoint is provided here.

## Public contract

Only these unauthenticated read requests are permitted:

| Endpoint | Response |
| --- | --- |
| `GET /api/public/restaurant` | Profile details and external links, without persistence ID. |
| `GET /api/public/opening-hours` | Timezone, Monday-to-Sunday weekly rules, and special rules from the current local date onward. |
| `GET /api/public/opening-status` | `openNow`, `closedToday`, local date/time, timezone, `WEEKLY` or `SPECIAL` source, and the selected day's times. |

Other routes remain denied by default; `/api/admin/**` now requires owner authentication as described in [ADMIN_AUTH.md](ADMIN_AUTH.md). No restaurant editing API, menu, promotion, or image workflow is part of this slice. Initial business data must be loaded by an authorized operator through a controlled process until owner editing is built. No production credentials or business seed data are embedded in migrations.

The fast tests run Flyway and JPA against isolated H2 databases and cover this contract, schedule precedence, timezone conversion, missing data, and security. A separate PostgreSQL Testcontainers integration test checks V1 migration, JPA schema validation, and selected database constraints on the production database engine. See [BACKEND_FOUNDATION.md](BACKEND_FOUNDATION.md) for commands and the Docker prerequisite.
