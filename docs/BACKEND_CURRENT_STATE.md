# Backend current state and frontend handoff

## Localization update (Flyway V6)

Public content supports exactly `lt`, `en`, `ru`, and `ka`. Lithuanian is canonical. `GET /api/public/restaurant`, `/api/public/menu`, and `/api/public/promotions` accept optional `?lang=lt|en|ru|ka`; omitted means `lt`, and unsupported or empty values return 400. Each response keeps its existing single resolved text fields. Resolution is field by field: requested nonblank value, then Lithuanian canonical value. Optional menu item and promotion descriptions can remain null. No translations are generated. Opening hours and status do not use a language parameter.

V6 adds one translation table per parent: `restaurant_profile_translation`, `menu_category_translation`, `menu_item_translation`, and `promotion_translation`. These contain only en/ru/ka text, with a parent/locale primary key, locale and content checks, and a cascading foreign key. Existing Lithuanian content remains in its original columns, so V6 does not rewrite or discard it. Hibernate continues to validate the existing entities against the migrated schema.

Localized fields are profile `displayName` and `description`, category `name`, item `name` and `description`, and promotion `title` and `description`. Prices, availability, flags, order, image URL, address, phone, email, external URLs, hours, dates, and timezone stay shared across languages.

Admin GET responses retain top-level Lithuanian fields and add `translations`, keyed by locale. Values are typed objects: profile `{displayName, description}`, category `{name}`, item `{name, description}`, and promotion `{title, description}`. The returned `lt` entry reflects the canonical fields. POST/PUT requests retain their existing required Lithuanian fields and may include `translations`. A present map fully replaces en/ru/ka entries; omitting it preserves them for older clients. To clear a locale, omit it from a present map. A supplied `lt` entry must match canonical fields. Blank optional translated fields clear the field and trigger Lithuanian fallback. Fully blank translation entries are removed. Unsupported locale keys and malformed entries return 400. Names/titles retain a 160-character limit; profile and item descriptions use 1000, promotion descriptions 500. Required names/titles and business-field validation remain in force. Translation writes share the entity transaction.

Flyway V7 makes the canonical menu item description nullable. Admin menu item POST/PUT accepts omitted or null `description`; whitespace-only input becomes null. Admin and public responses include `description: null` when no resolved description exists. If the requested translation lacks a description, public reads use the Lithuanian description when present, otherwise null. Existing nonblank descriptions remain unchanged, and the 1000-character limit still applies to present text. Item names remain required.

Frontend integration: send the selected language as `lang` on the three localized public routes, and render the returned fields directly. The CMS should load each entity's `translations` map and send the complete desired map alongside the canonical Lithuanian and other business fields on save. Existing requests without `lang` and older admin writes without `translations` continue to work.

This document is the concise checkpoint for frontend and backend work. It describes the implemented backend as it exists in this repository. Slice-level behavior and setup instructions remain in [RESTAURANT_SLICE.md](RESTAURANT_SLICE.md), [ADMIN_AUTH.md](ADMIN_AUTH.md), [MENU_SLICE.md](MENU_SLICE.md), [PROMOTIONS_SLICE.md](PROMOTIONS_SLICE.md), and [BACKEND_FOUNDATION.md](BACKEND_FOUNDATION.md).

## Architecture and stack

The backend is a Java 21, Spring Boot 4.1.1 modular monolith. It uses Spring MVC, Spring Security, Spring Data JPA, Bean Validation, Flyway, and PostgreSQL. Hibernate validates the migrated schema; Flyway owns schema evolution. Controllers exchange DTOs with services and repositories; persistence entities are not API contracts.

Domain packages under `com.kebabshop.backend` are `auth`, `restaurant`, `menu`, and `promotion`, with shared application and security configuration at the package root. The application serves one restaurant and one owner account; there is no tenant model or customer account system.

## Database migrations

| Migration | Owns |
| --- | --- |
| V1 `restaurant_and_opening_hours` | Singleton restaurant profile, recurring weekly hours, special-date overrides. |
| V2 `admin_account` | One owner account row, normalized unique email, BCrypt hash, enabled state, timestamps. No account is seeded. |
| V3 `menu` | Ordered categories and menu items, EUR price, active/available flags, category relationship. |
| V4 `promotions` | Informational promotions, active window, display order. |
| V5 `menu_featured_and_image_url` | Non-null `featured` defaulting false and nullable `image_url` up to 2048 characters. |
| V6 `content_translations` | en/ru/ka text for owner-managed public content; Lithuanian remains canonical in existing columns. |
| V7 `optional_menu_item_description` | Allows null canonical menu item descriptions while preserving existing text and the nonblank check for present values. |

Migrations V1–V7 are present and covered by the PostgreSQL integration test. Do not edit applied migrations; add a new migration for future schema changes.

## Public API

All public routes are read-only and under `/api/public/**`:

| Endpoint | Contract summary |
| --- | --- |
| `GET /api/public/restaurant` | Restaurant `displayName`, `description`, `address`, `phone`, nullable `email`, `googleMapsUrl`, nullable Wolt/Bolt Food and Instagram/Facebook URLs, and timestamps. No persistence ID. |
| `GET /api/public/opening-hours` | `timeZone`, seven `weekly` rules, and upcoming `specialDates`; rules include date/day, open state, and nullable opening/closing times. |
| `GET /api/public/opening-status` | `openNow`, `closedToday`, local date/time, `timeZone`, selected rule `source` (`WEEKLY` or `SPECIAL`), and nullable opening/closing times. |
| `GET /api/public/menu` | Ordered active categories with active items. Each public item includes ID, name, nullable description, `priceEur`, `available`, `featured`, and nullable `imageUrl`. |
| `GET /api/public/promotions` | `timeZone` and active promotions inside their configured time windows, ordered by owner display order. Each has ID, title, nullable description, and nullable local `startsAt`/`endsAt`. |

Menu semantics: categories and their items are sorted by display order, then stable ID. Inactive categories and items are hidden from the public response. Active items with `available=false` remain visible so the site can mark them sold out. Categories without visible items are omitted. Featured items are selected by filtering the regular menu response. `imageUrl` is only an HTTP/HTTPS URL reference; the backend does not fetch, upload, store, inspect, or transform image content.

Opening calculations use `RESTAURANT_TIME_ZONE`, default `Europe/Vilnius`. A special-date rule overrides that weekday's recurring rule. Opening is inclusive and closing is exclusive. The public opening-hours response includes the complete weekly schedule and special dates from the current local date onward. Overnight schedules and split shifts are unsupported. Promotion request times are local ISO date-times interpreted in this same zone; daylight-saving gaps and overlaps are rejected, promotion starts are inclusive, and ends are exclusive.

## Owner API and authentication

Owner content APIs live under `/api/admin/**` and require the authenticated owner session:

- Restaurant profile: `GET/PUT /api/admin/restaurant`. PUT creates the profile with 201 if it has not yet been configured and otherwise replaces it with 200.
- Weekly hours: `GET/PUT /api/admin/opening-hours/weekly`.
- Special-date hours: `GET/POST /api/admin/opening-hours/special-dates`, `PUT/DELETE /api/admin/opening-hours/special-dates/{date}`.
- Menu categories and items: CRUD at `/api/admin/menu/categories[/{id}]` and `/api/admin/menu/items[/{id}]`.
- Promotions: CRUD at `/api/admin/promotions[/{id}]`.

Create operations return 201 with `Location`; successful replacements return 200; successful deletes return 204. PUT operations replace editable fields. For menu items, `featured` is required and `imageUrl` is nullable; null or blank clears the image reference. Validation errors return 400, unknown resources return 404, and data conflicts such as deleting a category that still has items return 409. An unconfigured public restaurant returns 404; opening-hours/status return 503 if a profile exists but its weekly schedule is incomplete.

Authentication is a single-owner Spring Security session with BCrypt password hashing. Obtain a session CSRF token from `GET /api/admin/auth/csrf`, then send it in `X-CSRF-TOKEN` for login and all state-changing requests. `POST /api/admin/auth/login` creates the session; `GET /api/admin/auth/me` checks it; CSRF-protected `POST /api/admin/auth/logout` ends it. Anonymous protected requests return 401; missing/invalid CSRF returns 403. The session cookie is HttpOnly, SameSite=Lax, 30-minute idle timeout, and Secure under `prod`.

Same-origin use needs no CORS configuration. For a separate same-site frontend origin, configure exact origins with `ADMIN_CORS_ALLOWED_ORIGINS`; wildcards are rejected and `prod` permits only HTTPS origins. Credentialed CORS is enabled for GET/POST/PUT/DELETE and only `Content-Type` and `X-CSRF-TOKEN` headers. Because the session cookie is SameSite=Lax, use a same-site deployment for the admin frontend.

## First owner provisioning

There is no public registration, default password, or seeded owner. Provision the first owner by running the application in non-web mode with `--app.admin.bootstrap=true` and `--spring.main.web-application-type=none`, with database settings plus `ADMIN_BOOTSTRAP_EMAIL` and `ADMIN_BOOTSTRAP_PASSWORD` supplied through the process environment/secret facility. Do not pass the password as a command argument or store it in a script. The account is created once; an existing account is never overwritten. Clear bootstrap secrets after use. Production provisioning has not yet had a deployment dry run. Until password recovery is implemented, recovery requires a controlled operator procedure and owner handover.

## Testing and setup

`mvnw test` runs fast integration/API/security tests against isolated H2 2.3.232 databases in PostgreSQL mode. `mvnw verify` additionally runs `PostgresqlSchemaIT` against disposable PostgreSQL 17.9 through Testcontainers and requires Docker. H2 is useful for API and application behavior; PostgreSQL Testcontainers verifies migration, schema validation, and PostgreSQL constraints. See [BACKEND_FOUNDATION.md](BACKEND_FOUNDATION.md) for environment setup and exact commands.

## Frontend handoff

The public endpoints above are ready for customer-site integration. The owner endpoints above support the corresponding CMS flows. Use the public restaurant and opening routes for contact details, hours, and open status; use the menu response for categories, featured selection, sold-out labels, and optional image URLs; use public promotions for current offers. Wolt and Bolt Food remain outbound links from the restaurant profile.

For the admin UI, maintain the session cookie, obtain and send CSRF tokens as described above, and treat 401 as an unauthenticated session, 403 as forbidden/CSRF failure, 400 as invalid input, 404 as missing data, and 409 as a state conflict. Show useful loading, empty, and error states. The backend exposes no image upload route, no restaurant logo/hero fields, no customer ordering APIs, and no dedicated featured-items route.

## Intentional limitations and remaining backend work

Current intentional limitations include one owner, EUR-only prices, no customer accounts or order processing, no split shifts/overnight schedules, and no media upload/storage. Public and slice-specific error handling exists, but there is no deployment-wide operational error monitoring.

Before production, the project still needs a chosen deployment/routing architecture; HTTPS and proxy configuration; proxy login rate limiting and request-size limits; health/readiness checks; production logging and error monitoring; a first-admin deployment dry run and verified recovery/handover procedure; backup and restore procedures with a restore drill; CI for the documented build/tests; and a final production/security review. These are not blockers to frontend development.

Owner-managed image upload/storage is a later conditional decision. Restaurant logo/hero management is also conditional on frontend/product need. Allergen/dietary metadata, customer accounts, cart/checkout/payment, promo redemption, MFA, generic RBAC, analytics platform, split shifts/overnight schedules, and multi-admin concurrency/versioning are explicitly deferred from V1.
