# Repository guidance

## Project

Gio's Kebab is a production full-stack website and owner-managed content system. Prefer production-grade simplicity over academic or enterprise complexity. Owner self-service for routine business updates is a core requirement.

## Change rules

- Work on `development` unless instructed otherwise. Preserve `main`.
- Never commit or push unless explicitly requested.
- Keep secrets, credentials, and production data out of source control. Never add a default master credential.
- Do not perform destructive actions against production data.
- Use versioned database migrations for schema evolution; do not rely on automatic schema creation or edits to already-applied migrations.
- Add or update tests for behavioral changes. Keep business logic testable and validate PostgreSQL-specific behavior against PostgreSQL where H2 is insufficient.
- Keep public read-only APIs (`/api/public/**`) separate from authenticated owner operations (`/api/admin/**`). Do not expose persistence entities as API contracts.
- V1 has no customer accounts. Wolt and Bolt Food are external links; do not add in-app ordering, payments, or checkout.
- Avoid unnecessary dependencies, services, roles, and infrastructure. Add complexity only for a demonstrated product or operational need.

## Product and security

- Public content and normal business updates must be manageable by the owner without code changes or developer intervention.
- Protect owner access, uploaded media, secrets, and production data. Choose authentication, CSRF, CORS, and session/token controls deliberately for the deployed architecture.
- Keep customer-facing behavior mobile-first, accessible, fast, and food-focused; keep the admin interface simple and safe for a nontechnical owner.

See [docs/PRODUCTION_ROADMAP.md](docs/PRODUCTION_ROADMAP.md) for the implementation plan and current repository baseline.
