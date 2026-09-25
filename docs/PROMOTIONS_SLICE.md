# Informational promotions

Flyway V4 creates a single `promotion` table. A promotion has a title, optional short description, active flag, optional start/end instants, display order, and created/updated instants. Title length is 1–160 characters; description is null or 1–500 non-whitespace characters. Display order is a nonnegative integer. There are no images, coupons, promo codes, discounts, or redemption behavior.

## Time semantics

Admin requests send `startsAt` and `endsAt` as local ISO date-times without an offset, for example `2026-09-25T13:00:00`. They are interpreted in the configured `restaurant.time-zone`. Each valid local time is converted to an instant and stored in PostgreSQL `TIMESTAMP WITH TIME ZONE`. A local time in a daylight-saving gap or overlap is rejected with 400 because it does not identify exactly one instant. Responses include `timeZone` and return the values as local date-times in that zone.

Public visibility uses the injected `Clock` instant. Start is inclusive (`startsAt <= now`) and end is exclusive (`now < endsAt`). A null start has no lower bound; a null end has no upper bound. An end before a start is invalid; equal boundaries are allowed but yield an empty visibility interval. Promotions are sorted by display order and then stable ID.

## HTTP contract

`GET /api/public/promotions` is anonymous and returns active promotions in their valid time windows. Public DTOs contain title, description, dates, and IDs; they omit active state, display order, and timestamps.

Owner routes under `/api/admin/promotions` require an authenticated session. POST, PUT, and DELETE require CSRF:

| Route | Semantics |
| --- | --- |
| `GET /api/admin/promotions` | Lists every promotion, including inactive and out-of-window entries. |
| `POST /api/admin/promotions` | Creates with 201 and a `Location` header. |
| `GET /api/admin/promotions/{id}` | Reads one promotion. |
| `PUT /api/admin/promotions/{id}` | Fully replaces all editable fields. |
| `DELETE /api/admin/promotions/{id}` | Deletes with 204. |

Missing IDs return 404. Invalid or malformed requests, including invalid local timestamps, return a generic 400 problem response. PostgreSQL constraint conflicts return a generic 409. Authentication and CSRF behavior follows [ADMIN_AUTH.md](ADMIN_AUTH.md). Fast H2 tests cover endpoint behavior and deterministic time; `mvnw verify` validates V4 constraints on disposable PostgreSQL 17.9.
