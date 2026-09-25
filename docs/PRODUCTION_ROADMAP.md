# Gio's Kebab Production Roadmap

## Purpose and current baseline

Build a genuinely useful, production-ready website and content-management system for Gio's Kebab. The same backend and database serve an account-free customer website and an authenticated owner interface. Academic documentation should explain the real design and implementation; it must not drive artificial complexity.

The repository started as a minimal Spring Boot application (`BackendApplication`) with one context-load test and a Maven build. The `pom.xml` baseline is Java 21 and Spring Boot 4.1.1, with Spring Web MVC, Security, Data JPA, Validation, Flyway/PostgreSQL support, PostgreSQL runtime driver, DevTools, and Spring Boot test starters. The backend now has local/test/production database configuration, restaurant profile and hours, owner session authentication/provisioning, menu categories/items, informational promotions, and a featured/image URL reference contract. See [BACKEND_CURRENT_STATE.md](BACKEND_CURRENT_STATE.md) for the authoritative backend checkpoint and frontend handoff, plus [BACKEND_FOUNDATION.md](BACKEND_FOUNDATION.md), [RESTAURANT_SLICE.md](RESTAURANT_SLICE.md), [ADMIN_AUTH.md](ADMIN_AUTH.md), [MENU_SLICE.md](MENU_SLICE.md), and [PROMOTIONS_SLICE.md](PROMOTIONS_SLICE.md) for slice details. The frontend is unblocked. Deployment and production operations remain future work; owner-managed media upload/storage is a later product decision.

### Backend checkpoint

Completed backend capabilities:

- Foundation and environment profiles; PostgreSQL/Flyway migrations V1–V5.
- PostgreSQL Testcontainers verification of migrations and selected schema constraints.
- Restaurant profile/public API; weekly and special-date opening hours and opening status.
- Single-owner authentication, session/CSRF boundary, first-admin provisioning, and restaurant admin mutations.
- Menu categories/items, promotions, and the menu `featured`/`imageUrl` contract.

The public and admin API slices are ready for frontend development. Production/deployment readiness is still outstanding. See [BACKEND_CURRENT_STATE.md](BACKEND_CURRENT_STATE.md) for exact contracts and remaining backend work.

## Product boundaries

### Public customer website

Provide restaurant information, address/contact and map link, opening status and hours, categories and menu items (names, descriptions, prices, optional image URL, availability, featured state), current promotions, social links, and external Wolt/Bolt Food ordering links. Optimize for mobile use, accessibility, performance, local discovery, and a distinctive warm food-first presentation. No customer accounts, cart, checkout, payments, delivery tracking, or internal ordering in V1.

### Owner interface

Provide authenticated, low-friction tools for managing restaurant details and links, normal and special-date opening hours, categories, menu items, optional image URL references, availability, featured state, display order, and promotions. Normal business changes must not require source-code edits or developer intervention. Keep a single-owner administration model unless a demonstrated need changes it. Uploads and allergen/dietary metadata are excluded from V1 unless requirements change.

## Architecture and data design principles

- Keep a clear flow: controller → request/response DTO → service/domain logic → repository → PostgreSQL. Do not expose JPA entities as API contracts.
- Deliberately separate unauthenticated read-only `/api/public/**` routes from authenticated `/api/admin/**` routes. Decide exact endpoints during API design.
- Validate requests explicitly, return deliberate HTTP statuses, handle errors centrally, and define transactional boundaries.
- The schema is established in migrations V1–V5 for admin account, restaurant profile, opening hours, menu, and promotions. Keep the ER/model documentation aligned with these migrations; add media metadata only if uploads are later selected.
- Use migrations from the first schema change. Apply database constraints as well as application validation. Decide archive/hide/delete semantics so accidental deletion does not erase useful administrative history; do not imply audit/history guarantees until designed.
- Represent prices in a currency-safe form with an explicit currency decision, and represent weekly and exceptional opening intervals in a way that can express closed days and overnight hours if needed. Define the timezone source for open/closed calculations; avoid embedding country-specific behavior in generic domain rules.
- The current menu image contract is a nullable external HTTP/HTTPS URL reference. If owner-managed uploads are later selected, store bytes outside PostgreSQL and add only the validation, metadata, lifecycle, and delivery support that workflow needs.
- Prefer a modular monolith and existing dependencies. Do not introduce microservices, Kafka, Kubernetes, Redis, or enterprise IAM without measured need.

## Phased implementation plan

Phases are ordered by dependency, but can overlap where their interfaces are stable. Each phase should produce reviewable code and documentation before later phases depend on it.

### A. Foundation and project safety

Confirm repository conventions, package naming, Java/Maven commands, formatting expectations, and branch workflow. Replace generated project metadata and placeholder descriptions with accurate project metadata when appropriate. Establish a readable package structure around product domains and shared API concerns. Keep `main` preserved and work on `development` unless instructed otherwise.

**Exit:** project conventions and directory/package plan are recorded; no production feature is coupled to generated starter assumptions. **Status: domain package structure is established; placeholder Maven project metadata still needs cleanup.**

### B. Configuration and environments

Define local development, isolated test, and production configuration boundaries. Bind current database URL/user/password and allowed origins through environment variables or deployment secret management. Provide safe local setup examples with placeholders only; document required variables and defaults. Never commit real credentials or rely on a default admin password. Configure media or mail/recovery secrets only if those optional capabilities are later selected; neither upload nor email recovery is implemented.

**Exit:** a developer can configure a local instance from documented steps, and production secrets are externalized. **Status: local/test/production profiles and DB secret configuration are implemented; deployment verification remains.**

### C. Database migration and testing foundation

Set up PostgreSQL-backed local/test workflows and Flyway migration conventions. Create the first schema migration only after the domain model is reviewed. Define test database lifecycle and repeatable migration execution. Add migration tests against PostgreSQL; use H2 only for cases where its behavior is demonstrably sufficient. Do not use production data in tests.

**Exit:** a clean database can be created from migrations in development and an isolated test environment, with migration failures caught automatically. **Status: Flyway V1–V5 and H2/PostgreSQL Testcontainers validation implemented.**

### D. Restaurant and business information domain

Design the restaurant profile fields needed for public display and owner editing: name, description, address, phone/email, map URL, external ordering URLs, and social links. Decide whether V1 supports one restaurant (expected) and model accordingly. Validate links and contact values; avoid coupling stored content to a frontend presentation format.

**Exit:** documented data model and validation rules support owner-managed public information. **Status: implemented for the singleton profile and its public/admin APIs.**

### E. Opening hours and special-date overrides

Model recurring weekly hours and date-specific exceptions, including explicit closed days and exceptional opening intervals. Define precedence when multiple special records could apply and how timezone-aware “open now” is calculated. Test boundaries such as opening/closing instants, midnight, holidays, and daylight-saving transitions as applicable to the configured timezone.

**Exit:** public open/closed results are deterministic and the owner can maintain normal schedules and exceptions. **Status: implemented; split shifts and overnight hours are deferred from V1.**

### F. Menu categories and items

Design categories and menu items, including name, description, price/currency, category, optional HTTP/HTTPS image URL, available/sold-out state, featured state, display order, and timestamps. Decide soft-hide/archive and deletion semantics, referential integrity, and how category ordering behaves. Categories and items must be data-managed, never hardcoded into application logic. Dietary metadata is excluded from V1.

**Exit:** schema and service rules support safe management and stable public ordering. **Status: implemented in backend slices; image URLs are references only, with no upload/storage workflow.**

### G. Admin authentication and security boundary

The core owner authentication, password hashing, protected admin routes, session cookie, CSRF strategy, narrow configurable CORS, standard security headers, and safe first-admin provisioning are implemented. Before production, configure proxy login rate limiting, dry-run provisioning, and validate the controlled operator recovery and owner handover procedure. Self-service password recovery and richer audit logging are not implemented.

**Exit:** unauthenticated access is limited to intended public reads; admin access is usable and tested. **Status: core backend authentication implemented; production controls and recovery handover remain.**

### H. Optional image upload and media storage

Only implement this phase if owner-managed uploads are a confirmed product need. The current V1 menu contract stores an optional HTTP/HTTPS image URL and does not upload or store files. If uploads are later required, define a storage adapter independent of a final cloud vendor, validate size and actual content, use safe object keys, implement replacement/removal and metadata persistence, provide optimized public retrieval and orphan cleanup, and document storage configuration and retention. Prevent uploaded content from being served as executable content.

**Exit:** if selected, the owner can upload, replace, and remove menu imagery safely without storing large blobs in PostgreSQL. **Status: deferred; not required for the current URL-reference contract.**

### I. Promotions

Owner-managed informational promotions are implemented with optional local start/end times, active state, display order, public visibility rules, and timezone-aware boundaries. Image support and promotional redemption remain out of scope. Coupons, checkout discounts, and promo codes are excluded.

**Exit:** current promotions can be published and expire predictably. **Status: backend implemented; frontend consumption can begin, and deployment remains future work.**

### J. Public API

The read-only endpoints and response DTOs for restaurant details, open status/hours, categories/items, featured state, image URL references, and promotions are implemented and documented. The menu response carries `featured` and `imageUrl`; there is no separate featured or media endpoint. Consider caching or pagination only when frontend or collection needs justify it.

**Exit:** frontend requirements can be met from stable public contracts without exposing persistence details. **Status: implemented; frontend development is unblocked.**

### K. Backend hardening and testing

H2 API/security tests and a PostgreSQL Testcontainers migration/constraint test exist. CI for repeatable build/test execution and dependency/security scanning, plus production logging/error monitoring, remain outstanding. Media safety tests apply only if uploads are selected.

**Exit:** core business, authorization, migration, and API behavior is automatically checked in CI. **Status: local automated tests exist; CI is not configured.**

### L. Frontend foundation and design system

The backend contracts are ready. Select/confirm a frontend approach compatible with SEO and the eventual deployment, then establish the accessible responsive design system and authenticated admin state using [BACKEND_CURRENT_STATE.md](BACKEND_CURRENT_STATE.md) as the API handoff.

**Exit:** design direction and build/development workflow support both customer and owner interfaces. **Status: backend dependency is complete; frontend implementation can begin.**

### M. Public customer website

Build the responsive restaurant site with business details, menu browsing, categories, prices, availability/sold-out labels, featured items, current promotions, opening status/hours, map/contact actions, and external Wolt/Bolt links. Include meaningful loading, empty, and error states. Do not add customer login, cart, ordering, or payment flows.

**Exit:** customers can answer what is served, when the restaurant is open, where it is, and how to order externally on mobile and desktop.

### N. Admin CMS

Build authenticated owner workflows for restaurant profile, weekly and special-date hours, categories, menu items (including featured and optional image URL fields), promotions, and ordering/display state. Image upload/storage is not part of the current contract. Use plain language, safe destructive-action confirmation, helpful validation, clear save/error feedback, and sensible defaults. Make frequent tasks usable on a phone while prioritizing practical owner workflows.

**Exit:** routine business content can be updated without developer involvement.

### O. Responsive, accessibility, and performance polish

Review keyboard operation, focus visibility, labels, contrast, semantic structure, screen-reader announcements, reduced-motion preferences, touch targets, and responsive layouts. Optimize image dimensions/formats and loading, public API payloads, caching, and render paths. Measure real performance and address Core Web Vitals bottlenecks before launch.

**Exit:** public and admin workflows meet agreed accessibility and performance targets across representative devices.

### P. SEO and local discoverability

Add page titles/descriptions, canonical and social preview metadata, and appropriate restaurant structured data (including address, phone, menu and opening hours). Keep structured data consistent with visible and API-managed content. Provide sitemap and robots behavior where appropriate; prevent admin/private pages from indexing. Verify local business details and outbound map links.

**Exit:** crawlers receive accurate, maintainable local restaurant information and public pages have valid metadata.

### Q. Deployment, observability, and backups

Before production, document reproducible deployment and rollback, environment separation, database provisioning and migration execution, domain/TLS and frontend/backend routing. Add health/readiness checks, production logging/error monitoring, uptime checks and alerts. Define database backup cadence, retention, access and restore drills. Add media storage configuration and media backups only if owner-managed uploads are selected.

**Exit:** a clean production deployment and a tested recovery path are documented; backups are restorable, not merely configured.

### R. Final security and production audit

Review threat surfaces: authentication/recovery, authorization, CSRF/CORS, input validation, secret exposure, dependencies, headers/TLS, logging, rate limiting, privacy, and data retention. Review upload validation only if uploads are adopted. Verify production settings, least-privilege service accounts, error responses, and owner account bootstrap. Resolve findings and record accepted residual risks.

**Exit:** launch blockers are resolved and a concise security review is available.

### S. Academic and technical documentation

Maintain documentation from actual implementation: requirements/use cases, scope decisions, ER model, schema and migrations, package/class structure, API contracts, authentication/security choices, test strategy/results, deployment architecture, and installation/run instructions. Include diagrams only when they clarify implemented behavior. Keep academic explanation accurate and avoid fake subsystems.

**Exit:** a reviewer can explain the real system and reproduce its development/test setup from the repository.

### T. Owner handover

Transfer operational ownership of domain, hosting, database, admin account/recovery contact, and external service accounts to the business. Include media storage only if that capability is adopted. Provide owner instructions for supported content editing, the controlled account recovery procedure, and technical support, plus backup, restore, deployment, incident, and cost guidance.

**Exit:** the owner can manage routine content and the business controls its production assets and recovery paths.

## V1 exclusions

Do not implement allergens/dietary metadata, customer accounts, cart/checkout/payments, internal online ordering, delivery tracking, promo redemption, MFA, generic RBAC, analytics platform, driver management, kitchen/POS integration, loyalty points, reservations, real-time inventory quantities, split shifts/overnight hours, multi-admin concurrency/versioning, microservices, Kafka, Kubernetes, or Redis without demonstrated need. Wolt and Bolt Food remain external ordering providers. QR menu support is desirable after core V1 and is not a release requirement.

## Remaining backend work before production

- Choose and document deployment architecture, application/database provisioning, routing, migration/rollback steps, and HTTPS/TLS.
- Configure proxy login rate limiting and request-size limits.
- Provide health/readiness checks and production logging/error monitoring.
- Perform a first-admin provisioning dry run and validate owner recovery/handover procedures.
- Establish CI for reproducible builds/tests and dependency/security checks.
- Define database backups and complete a restore drill.
- Complete the final production/security audit.

These items do not block frontend development. Image upload/storage and restaurant logo/hero management remain later conditional decisions, not V1 backend capabilities or current release gates.

## Release gates

Before public launch, require: clean migration from an empty production-like PostgreSQL database; automated coverage for public/admin boundaries and core content flows; owner authentication and recovery validation; responsive/accessibility and SEO checks; documented deploy/rollback and environment variables; verified health/readiness and monitoring; successful database restore drill; and owner acceptance of the CMS and handover instructions. Require media upload safety and media restore only if uploads are added to the product scope.
