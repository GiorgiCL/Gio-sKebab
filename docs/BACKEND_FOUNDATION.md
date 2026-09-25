# Backend foundation

## Local startup

Use Java 21 and a local PostgreSQL instance. Create an empty local database and a local database user with access to it. Set `DB_PASSWORD` in your shell. The default `local` profile uses `jdbc:postgresql://localhost:5432/gios_kebab` and username `gios_kebab`; set `DB_URL` and `DB_USERNAME` if yours differ. The sample values are in [`.env.example`](../.env.example), which is documentation only and is not loaded by Spring Boot.

PowerShell example (replace the value with your own local secret):

```powershell
$env:DB_PASSWORD = '<local database password>'
.\mvnw.cmd spring-boot:run
```

The first public read endpoints now exist; see [RESTAURANT_SLICE.md](RESTAURANT_SLICE.md). Startup confirms the database connection and Flyway migration.

## Production configuration

Set `SPRING_PROFILES_ACTIVE=prod` and supply `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` explicitly through the deployment environment or secret manager. No production database defaults or credentials are stored in this repository. `DB_URL` must point to the intended PostgreSQL database. Provisioning and migration permissions must be reviewed before deployment. Do not run the `test` profile as a production service.

The common configuration sets `spring.jpa.hibernate.ddl-auto=validate`, disables open-in-view, and enables Flyway. Hibernate must not create or update schema. Put each future schema change in a new, versioned SQL file under `src/main/resources/db/migration`; do not edit migrations already applied to any shared environment. `V1__restaurant_and_opening_hours.sql` is the first migration and contains no business seed data.

## Tests

Run `.\mvnw.cmd test` in PowerShell (or `./mvnw test` on Unix) for the fast tests. They activate `test` and pin both the datasource and Flyway connection to isolated in-memory H2 databases at highest test property precedence. They use no external database or service. `application-test.properties` also selects H2 for tests that activate that profile. Keep future tests isolated with explicit datasource and Flyway settings; do not override them with external URLs. The test-scoped H2 version is pinned to 2.3.232 because [H2 2.4.240 can fail on a `CHECK IN` constraint after the connection that created it closes](https://github.com/h2database/h2database/issues/4320). H2 PostgreSQL mode keeps API and domain tests quick, but cannot prove PostgreSQL-specific migration or constraint behavior.

Run `.\mvnw.cmd verify` (or `./mvnw verify` on Unix) with a working Docker-compatible container runtime for both the fast tests and `*IT` PostgreSQL integration tests. Maven Failsafe runs the latter after Surefire's fast tests. The PostgreSQL test uses Spring Boot's Testcontainers service connection and a disposable `postgres:17.9-alpine` container; JDBC and Flyway both connect to that container. It never uses `gios_kebab_dev`, `DB_PASSWORD`, a local PostgreSQL instance, or any production database. The test checks that V1 migrates, Hibernate validates the schema, JPA persists the profile, and PostgreSQL enforces selected V1 constraints. Docker is required for `verify`; `test` does not require it. The local PostgreSQL 17.9 startup and Flyway V1 migration were also manually validated before this automated layer was added.

## Temporary security and observability

The temporary security filter permits only the three documented public GET endpoints and denies all other HTTP requests, including `/api/admin/**`. Form login and HTTP Basic are disabled, and Spring Boot's generated in-memory user is excluded. There is no admin authentication yet. Extend this policy deliberately when implementing owner authentication; CSRF remains at the framework default until an authentication approach is selected.

Actuator is not included. Health/readiness, structured operational logging, monitoring, backups, and deployment checks belong to the later deployment and hardening phases in the [production roadmap](PRODUCTION_ROADMAP.md).
