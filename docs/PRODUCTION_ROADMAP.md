# Gio's Kebab Production Roadmap

## Purpose and current baseline

Build a genuinely useful, production-ready website and content-management system for Gio's Kebab. The same backend and database serve an account-free customer website and an authenticated owner interface. Academic documentation should explain the real design and implementation; it must not drive artificial complexity.

The repository currently contains a minimal Spring Boot application (`BackendApplication`), one context-load test, `application.properties` with only `spring.application.name=backend`, and a Maven build. The `pom.xml` baseline is Java 21 and Spring Boot 4.1.1, with Spring Web MVC, Security, Data JPA, Validation, Flyway/PostgreSQL support, PostgreSQL runtime driver, DevTools, and Spring Boot test starters. There are no domain packages, migrations, database configuration, API routes, authentication implementation, frontend, deployment assets, or product documentation yet. Verify exact versions and supported configuration against the repository and official Spring documentation when implementation begins.

## Product boundaries

### Public customer website

Provide restaurant information, address/contact and map link, opening status and hours, categories and menu items (names, descriptions, prices, images, availability, featured state), current promotions, social links, and external Wolt/Bolt Food ordering links. Optimize for mobile use, accessibility, performance, local discovery, and a distinctive warm food-first presentation. No customer accounts, cart, checkout, payments, delivery tracking, or internal ordering in V1.

### Owner interface

Provide authenticated, low-friction tools for managing restaurant details and links, normal and special-date opening hours, categories, menu items, item images, availability, featured state, display order, optional allergen/dietary metadata, and promotions. Normal business changes must not require source-code edits or developer intervention. Keep a single-owner administration model unless a demonstrated need changes it.

## Architecture and data design principles

- Keep a clear flow: controller → request/response DTO → service/domain logic → repository → PostgreSQL. Do not expose JPA entities as API contracts.
- Deliberately separate unauthenticated read-only `/api/public/**` routes from authenticated `/api/admin/**` routes. Decide exact endpoints during API design.
- Validate requests explicitly, return deliberate HTTP statuses, handle errors centrally, and define transactional boundaries.
- Design and document an ER model and constraints before implementing schema. Candidate areas: admin user, restaurant profile, recurring opening hours, special-date overrides, category, menu item, promotion, and media metadata. Avoid prematurely splitting singleton restaurant data or adding speculative tables.
- Use migrations from the first schema change. Apply database constraints as well as application validation. Decide archive/hide/delete semantics so accidental deletion does not erase useful administrative history; do not imply audit/history guarantees until designed.
- Represent prices in a currency-safe form with an explicit currency decision, and represent weekly and exceptional opening intervals in a way that can express closed days and overnight hours if needed. Define the timezone source for open/closed calculations; avoid embedding country-specific behavior in generic domain rules.
- Store image bytes outside PostgreSQL. Keep media metadata and a storage abstraction in the database/application. Validate actual content, size, and allowed image types; use safe generated object keys, lifecycle-aware replace/delete operations, optimized public delivery, and orphan cleanup.
- Prefer a modular monolith and existing dependencies. Do not introduce microservices, Kafka, Kubernetes, Redis, or enterprise IAM without measured need.

## Phased implementation plan

Phases are ordered by dependency, but can overlap where their interfaces are stable. Each phase should produce reviewable code and documentation before later phases depend on it.

### A. Foundation and project safety

Confirm repository conventions, package naming, Java/Maven commands, formatting expectations, and branch workflow. Replace generated project metadata and placeholder descriptions with accurate project metadata when appropriate. Establish a readable package structure around product domains and shared API concerns. Keep `main` preserved and work on `development` unless instructed otherwise.

**Exit:** project conventions and directory/package plan are recorded; no production feature is coupled to generated starter assumptions.

### B. Configuration and environments

Define local development, isolated test, and production configuration boundaries. Bind database URL/user/password, session/signing secrets, allowed origins, media configuration, and mail/recovery settings through environment variables or deployment secret management. Provide safe local setup examples with placeholders only; document required variables and defaults. Never commit real credentials or rely on a default admin password. Ensure production configuration fails clearly when required secrets are absent.

**Exit:** a developer can configure a local instance from documented steps, and production secrets are externalized.

### C. Database migration and testing foundation

Set up PostgreSQL-backed local/test workflows and Flyway migration conventions. Create the first schema migration only after the domain model is reviewed. Define test database lifecycle and repeatable migration execution. Add migration tests against PostgreSQL; use H2 only for cases where its behavior is demonstrably sufficient. Do not use production data in tests.

**Exit:** a clean database can be created from migrations in development and an isolated test environment, with migration failures caught automatically.

### D. Restaurant and business information domain

Design the restaurant profile fields needed for public display and owner editing: name, description, address, phone/email, map URL, external ordering URLs, and social links. Decide whether V1 supports one restaurant (expected) and model accordingly. Validate links and contact values; avoid coupling stored content to a frontend presentation format.

**Exit:** documented data model and validation rules support owner-managed public information.

### E. Opening hours and special-date overrides

Model recurring weekly hours and date-specific exceptions, including explicit closed days and exceptional opening intervals. Define precedence when multiple special records could apply and how timezone-aware “open now” is calculated. Test boundaries such as opening/closing instants, midnight, holidays, and daylight-saving transitions as applicable to the configured timezone.

**Exit:** public open/closed results are deterministic and the owner can maintain normal schedules and exceptions.

### F. Menu categories and items

Design categories and menu items, including name, description, price/currency, category, image reference, available/sold-out state, featured state, display order, timestamps, and optional allergen/dietary metadata. Decide soft-hide/archive and deletion semantics, referential integrity, and how category ordering behaves. Categories and items must be data-managed, never hardcoded into application logic.

**Exit:** schema and service rules support safe management and stable public ordering.

### G. Admin authentication and security boundary

Implement owner-only authentication with secure password hashing, protected admin routes, deliberate session or token architecture, secure production cookie/token settings, and a matching CSRF strategy. Configure narrow CORS rules for actual frontend origins. Add login rate limiting, security headers, secrets management, and a practical password recovery strategy. Provide a safe initial owner provisioning process with no committed or hidden master credential. Record useful security/audit events without logging passwords, tokens, or sensitive payloads.

**Exit:** unauthenticated access is limited to intended public reads; admin access and recovery are usable and tested.

### H. Image and media storage

Define a storage adapter independent of a final cloud vendor. Implement upload validation, size limits, MIME/content inspection, safe object keys, replacement and removal lifecycle, metadata persistence, optimized public retrieval, and orphan cleanup/reconciliation. Prevent uploads from being served as executable content. Document operational storage configuration and retention.

**Exit:** the owner can upload, replace, and remove menu imagery safely without storing large blobs in PostgreSQL.

### I. Promotions

Design owner-managed promotions with clear start/end timing, active state, display order, optional image, and public visibility rules. Define timezone and expiration behavior. Avoid implementing coupons, checkout discounts, or promotion redemption absent a real business requirement.

**Exit:** current promotions can be published and expire predictably.

### J. Public API

Design read-only endpoints and response DTOs for restaurant details, open status/hours, categories/items, featured content, promotions, and media. Use consistent pagination only where collections justify it; include cache policy/ETags where useful. Return only published, available-to-display content according to explicit rules. Document contracts and error behavior.

**Exit:** frontend requirements can be met from stable public contracts without exposing persistence details.

### K. Backend hardening and testing

Add service/domain unit tests, controller/API integration tests, validation and centralized-error tests, security/authentication and authorization tests, media safety tests, and PostgreSQL-specific repository/migration tests where database semantics matter. Exercise public/admin separation and failure paths. Establish CI for reproducible Maven builds, test execution, and dependency/security scanning. Add structured logs and correlation/request identifiers without sensitive data.

**Exit:** core business, authorization, migration, and API behavior is automatically checked in CI.

### L. Frontend foundation and design system

Select/confirm a frontend approach compatible with the deployment and SEO needs. Establish accessible typography, color, spacing, responsive layout, image handling, and reusable components with a distinctive food-first visual identity. Avoid generic Bootstrap/SaaS presentation and excessive animation. Document how the frontend consumes the backend and manages authenticated admin state.

**Exit:** design direction and build/development workflow support both customer and owner interfaces.

### M. Public customer website

Build the responsive restaurant site with business details, menu browsing, categories, prices, availability/sold-out labels, featured items, current promotions, opening status/hours, map/contact actions, and external Wolt/Bolt links. Include meaningful loading, empty, and error states. Do not add customer login, cart, ordering, or payment flows.

**Exit:** customers can answer what is served, when the restaurant is open, where it is, and how to order externally on mobile and desktop.

### N. Admin CMS

Build authenticated owner workflows for restaurant profile, weekly and special-date hours, categories, menu items, images, promotions, and ordering/display state. Use plain language, safe destructive-action confirmation, helpful validation, clear save/error feedback, and sensible defaults. Make frequent tasks usable on a phone while prioritizing practical owner workflows.

**Exit:** routine business content can be updated without developer involvement.

### O. Responsive, accessibility, and performance polish

Review keyboard operation, focus visibility, labels, contrast, semantic structure, screen-reader announcements, reduced-motion preferences, touch targets, and responsive layouts. Optimize image dimensions/formats and loading, public API payloads, caching, and render paths. Measure real performance and address Core Web Vitals bottlenecks before launch.

**Exit:** public and admin workflows meet agreed accessibility and performance targets across representative devices.

### P. SEO and local discoverability

Add page titles/descriptions, canonical and social preview metadata, and appropriate restaurant structured data (including address, phone, menu and opening hours). Keep structured data consistent with visible and API-managed content. Provide sitemap and robots behavior where appropriate; prevent admin/private pages from indexing. Verify local business details and outbound map links.

**Exit:** crawlers receive accurate, maintainable local restaurant information and public pages have valid metadata.

### Q. Deployment, observability, and backups

Document reproducible build and deployment steps, environment separation, database provisioning, migration execution, media storage configuration, domain/TLS setup, and frontend/backend routing. Add health and readiness checks with appropriate information exposure. Configure structured operational logs, error monitoring, uptime checks, and actionable alerts. Define database and media backup cadence, retention, access, restore drills, and recovery objectives. Document rollback, including safe application rollback and forward-fix guidance for already-applied migrations.

**Exit:** a clean production deployment and a tested recovery path are documented; backups are restorable, not merely configured.

### R. Final security and production audit

Review threat surfaces: authentication/recovery, authorization, CSRF/CORS, upload validation, injection/input validation, secret exposure, dependencies, headers/TLS, logging, rate limiting, privacy, and data retention. Verify production settings, least-privilege service accounts, error responses, and owner account bootstrap. Resolve findings and record accepted residual risks.

**Exit:** launch blockers are resolved and a concise security review is available.

### S. Academic and technical documentation

Maintain documentation from actual implementation: requirements/use cases, scope decisions, ER model, schema and migrations, package/class structure, API contracts, authentication/security choices, test strategy/results, deployment architecture, and installation/run instructions. Include diagrams only when they clarify implemented behavior. Keep academic explanation accurate and avoid fake subsystems.

**Exit:** a reviewer can explain the real system and reproduce its development/test setup from the repository.

### T. Owner handover

Transfer operational ownership of domain, hosting, database, media storage, admin account/recovery email, and external service accounts to the business. Provide owner instructions for editing all supported content, handling account recovery, and requesting technical support. Provide an operations guide for backups, restore, deployment, incident contacts, and recurring costs. Confirm no hidden developer account, hardcoded credential, or single-person dependency remains.

**Exit:** the owner can manage routine content and the business controls its production assets and recovery paths.

## V1 exclusions

Do not implement payments, checkout/cart, internal online ordering, delivery tracking, customer accounts, driver management, kitchen/POS integration, loyalty points, reservations, real-time inventory quantities, microservices, Kafka, Kubernetes, or Redis without demonstrated need. Wolt and Bolt Food remain external ordering providers. QR menu support is desirable after core V1 and is not a release requirement.

## Release gates

Before public launch, require: clean migration from an empty production-like PostgreSQL database; automated coverage for public/admin boundaries and core content flows; owner authentication and recovery validation; safe media upload lifecycle; responsive/accessibility and SEO checks; documented deploy/rollback and environment variables; verified health/readiness and monitoring; successful database and media restore drill; and owner acceptance of the CMS and handover instructions.
