# Backend foundation

## Local startup

Use Java 21 and a local PostgreSQL instance. Create an empty local database and a local database user with access to it. Set `DB_PASSWORD` in your shell. The default `local` profile uses `jdbc:postgresql://localhost:5432/gios_kebab` and username `gios_kebab`; set `DB_URL` and `DB_USERNAME` if yours differ. The sample values are in [`.env.example`](../.env.example), which is documentation only and is not loaded by Spring Boot.

PowerShell example (replace the value with your own local secret):

```powershell
$env:DB_PASSWORD = '<local database password>'
.\mvnw.cmd spring-boot:run
```

No business endpoints exist yet. A successful startup confirms the database connection and Flyway initialization; HTTP requests are denied by the temporary security policy.

## Production configuration

Set `SPRING_PROFILES_ACTIVE=prod` and supply `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` explicitly through the deployment environment or secret manager. No production database defaults or credentials are stored in this repository. `DB_URL` must point to the intended PostgreSQL database. Provisioning and migration permissions must be reviewed before deployment. Do not run the `test` profile as a production service.

The common configuration sets `spring.jpa.hibernate.ddl-auto=validate`, disables open-in-view, and enables Flyway. Hibernate must not create or update schema. Put each future schema change in a new, versioned SQL file under `src/main/resources/db/migration`; do not edit migrations already applied to any shared environment. The migration directory is deliberately empty because no domain schema has been designed. Flyway can start with zero pending migrations; the first feature schema should introduce `V1__<description>.sql` after its model is reviewed.

## Tests

Run `./mvnw.cmd test` in PowerShell (or `./mvnw test` on Unix). The context smoke test activates `test`, pins both the datasource and Flyway connection to an in-memory H2 database at highest test property precedence, and checks that Flyway starts with no migrations. It uses no external database or service. `application-test.properties` also selects H2 for tests that activate that profile. Keep future tests isolated with the same explicit datasource and Flyway settings; do not override them with external URLs. H2 PostgreSQL mode is only a convenience for generic startup tests; it does not validate PostgreSQL-specific SQL, constraints, or transaction behavior. Introduce disposable PostgreSQL integration tests when the first PostgreSQL-specific migration or repository behavior warrants them.

## Temporary security and observability

The foundation security filter denies all HTTP requests, including `/api/public/**` and `/api/admin/**`. Form login and HTTP Basic are disabled, and Spring Boot's generated in-memory user is excluded. There is no admin authentication or public API yet. Replace this policy deliberately when implementing the public read API and owner authentication; do not treat it as the final security design. CSRF remains at the framework default until an authentication approach is selected.

Actuator is not included. Health/readiness, structured operational logging, monitoring, backups, and deployment checks belong to the later deployment and hardening phases in the [production roadmap](PRODUCTION_ROADMAP.md).
