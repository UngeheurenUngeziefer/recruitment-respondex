-- Database roles. Run once per environment by an administrator (not as a migration).
-- app_rt: application role; not a table owner, superuser, or BYPASSRLS role, so RLS applies.
-- app_worker: cross-organization background jobs (outbox, reconciliation); usage is logged.

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_rt') THEN
    CREATE ROLE app_rt LOGIN NOSUPERUSER NOBYPASSRLS;
  END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO app_rt;

GRANT SELECT, INSERT, UPDATE, DELETE ON
  organization, app_user, membership, study,
  budget_account, budget_entry, outbox_event,
  session, session_moderator
TO app_rt;

-- audit_event: INSERT and SELECT only. UPDATE/DELETE are revoked, with a trigger as a second guard.
GRANT SELECT, INSERT ON audit_event TO app_rt;
GRANT USAGE ON SEQUENCE audit_event_id_seq TO app_rt;
