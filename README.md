# Recruitment Respondex

An MVP backend and UI prototype for a respondent recruitment platform. The Java API uses Spring Boot, PostgreSQL, tenant isolation with row-level security (RLS), a budget ledger, and modular application boundaries.

## Project Contents

- `src/main/resources/db/migration/V1__foundation.sql`: PostgreSQL schema for organizations, studies, budgets, sessions, outbox events, audit records, and tenant RLS policies.
- `db/roles.sql`: grants for the restricted `app_rt` application role.
- `db/tests/invariants.sql`: database checks for tenant isolation, budget limits, scheduling conflicts, and append-only audit records.
- `src/main/java/com/platform/`: Spring Boot API modules for studies, budgets, audit, and tenant context.
- `src/main/resources/static/index.html`: bundled UI prototype served by the application.

## Run Without PostgreSQL

The default `demo` profile starts without a database. Open `http://localhost:8080/` for the UI. The study API uses in-memory storage, so its data is cleared when the application restarts.

```bash
mvn spring-boot:run
```

## Run With PostgreSQL

Provision PostgreSQL and the database roles, then apply the schema and grants:

```bash
createdb recrut
psql -d recrut -f src/main/resources/db/migration/V1__foundation.sql
psql -d recrut -f db/roles.sql
psql -d recrut -v ON_ERROR_STOP=1 -f db/tests/invariants.sql
```

Set database credentials through environment variables and activate the `postgres` profile:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/recrut
export DB_APP_PASSWORD=...
export DB_OWNER_USER=recrut_owner
export DB_OWNER_PASSWORD=...
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

The schema owner role and role passwords must be provisioned for your environment. `db/roles.sql` creates grants for `app_rt`; it does not set passwords.

Example request:

```bash
curl -X POST localhost:8080/api/v1/studies \
  -H 'Content-Type: application/json' \
  -H 'X-Org-Id: 00000000-0000-0000-0000-00000000000a' \
  -H 'X-User-Id: 00000000-0000-0000-0000-0000000000a1' \
  -d '{"title":"Online banking interviews","sessionDurationMin":60,"targetN":15}'
```

## Tests and Limitations

Run Java tests with `mvn test`. Database invariant checks in `db/tests/invariants.sql` require a configured PostgreSQL database.

Authentication is not implemented. The API currently accepts organization and user IDs from request headers for development only; production use requires verified identities and membership checks. Permissions, outbox workers, external integrations, and a production frontend are also not implemented.
