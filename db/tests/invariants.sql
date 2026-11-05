-- Verify schema invariants against a real PostgreSQL database.
-- Run with: psql -v ON_ERROR_STOP=1 -f db/tests/invariants.sql (as superuser, on an empty database after migrations and roles.sql).
-- Each block either passes or raises an exception with a descriptive message.

\set ON_ERROR_STOP on

-- ---------- setup (the superuser bypasses RLS, which is expected here)
INSERT INTO organization (id, name) VALUES
  ('00000000-0000-0000-0000-00000000000a', 'Org A'),
  ('00000000-0000-0000-0000-00000000000b', 'Org B');

INSERT INTO app_user (id, email, name) VALUES
  ('00000000-0000-0000-0000-0000000000a1', 'mod1@example.com', 'Moderator 1'),
  ('00000000-0000-0000-0000-0000000000a2', 'mod2@example.com', 'Moderator 2');

INSERT INTO study (id, org_id, title, session_duration_min, target_n) VALUES
  ('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-00000000000a', 'Study A', 60, 15);

INSERT INTO budget_account (id, org_id, scope_type, scope_id, currency, limit_amount) VALUES
  ('00000000-0000-0000-0000-0000000000d1', '00000000-0000-0000-0000-00000000000a', 'ORG', '00000000-0000-0000-0000-00000000000a', 'EUR', 100.00);

-- ---------- switch to the application role: RLS applies from here on
SET ROLE app_rt;

-- T1: rows are hidden without an organization context.
DO $$
BEGIN
  PERFORM set_config('app.org_id', '', true);
  IF (SELECT count(*) FROM study) <> 0 THEN
    RAISE EXCEPTION 'T1 FAIL: study rows are visible without app.org_id';
  END IF;
  RAISE NOTICE 'T1 OK: an empty context cannot see data';
END
$$;

-- T2: organization B cannot see organization A studies.
DO $$
BEGIN
  PERFORM set_config('app.org_id', '00000000-0000-0000-0000-00000000000b', true);
  IF (SELECT count(*) FROM study) <> 0 THEN
    RAISE EXCEPTION 'T2 FAIL: org B can see org A studies';
  END IF;
  RAISE NOTICE 'T2 OK: read isolation between organizations';
END
$$;

-- T3: organization B cannot insert a row with org_id = A (WITH CHECK).
DO $$
BEGIN
  PERFORM set_config('app.org_id', '00000000-0000-0000-0000-00000000000b', true);
  BEGIN
    INSERT INTO study (org_id, title, session_duration_min, target_n)
    VALUES ('00000000-0000-0000-0000-00000000000a', 'Hack', 60, 1);
    RAISE EXCEPTION 'T3 FAIL: insert into another organization succeeded';
  EXCEPTION WHEN insufficient_privilege THEN
    RAISE NOTICE 'T3 OK: RLS rejected an insert into another organization';
  END;
END
$$;

-- T4: conditional reservation. With a limit of 100, 60 succeeds and another 60 is rejected (0 rows).
DO $$
DECLARE n int;
BEGIN
  PERFORM set_config('app.org_id', '00000000-0000-0000-0000-00000000000a', true);

  UPDATE budget_account
     SET reserved_amount = reserved_amount + 60, version = version + 1
   WHERE id = '00000000-0000-0000-0000-0000000000d1'
     AND limit_amount - reserved_amount - spent_amount >= 60;
  GET DIAGNOSTICS n = ROW_COUNT;
  IF n <> 1 THEN RAISE EXCEPTION 'T4 FAIL: the first reservation should succeed'; END IF;

  UPDATE budget_account
     SET reserved_amount = reserved_amount + 60, version = version + 1
   WHERE id = '00000000-0000-0000-0000-0000000000d1'
     AND limit_amount - reserved_amount - spent_amount >= 60;
  GET DIAGNOSTICS n = ROW_COUNT;
  IF n <> 0 THEN RAISE EXCEPTION 'T4 FAIL: the second reservation must not exceed the limit'; END IF;

  RAISE NOTICE 'T4 OK: conditional reservation prevents overspending';
END
$$;

-- T5: CHECK guardrail. A direct UPDATE that bypasses the condition must violate the database constraint.
DO $$
BEGIN
  PERFORM set_config('app.org_id', '00000000-0000-0000-0000-00000000000a', true);
  BEGIN
    UPDATE budget_account SET reserved_amount = 500
     WHERE id = '00000000-0000-0000-0000-0000000000d1';
    RAISE EXCEPTION 'T5 FAIL: CHECK did not fire';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'T5 OK: CHECK reserved+spent<=limit preserves the invariant';
  END;
END
$$;

-- T6: prevent double-booking a moderator in overlapping intervals (including buffers).
DO $$
DECLARE s1 uuid := '00000000-0000-0000-0000-0000000000e1';
        s2 uuid := '00000000-0000-0000-0000-0000000000e2';
BEGIN
  PERFORM set_config('app.org_id', '00000000-0000-0000-0000-00000000000a', true);

  INSERT INTO session (id, org_id, study_id, starts_at, ends_at)
  VALUES (s1, '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-0000000000c1',
          '2026-12-01 10:00+01', '2026-12-01 11:00+01'),
         (s2, '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-0000000000c1',
          '2026-12-01 11:10+01', '2026-12-01 12:10+01');

  -- Session 1 occupies 09:45–11:15 (15-minute buffers on both sides).
  INSERT INTO session_moderator (session_id, org_id, moderator_id, busy)
  VALUES (s1, '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-0000000000a1',
          tstzrange('2026-12-01 09:45+01', '2026-12-01 11:15+01', '[)'));

  -- Session 2 starts at 11:10, overlapping session 1's busy range; the insert must fail.
  BEGIN
    INSERT INTO session_moderator (session_id, org_id, moderator_id, busy)
    VALUES (s2, '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-0000000000a1',
            tstzrange('2026-12-01 10:55+01', '2026-12-01 12:25+01', '[)'));
    RAISE EXCEPTION 'T6 FAIL: overlap with the buffer was not detected';
  EXCEPTION WHEN exclusion_violation THEN
    RAISE NOTICE 'T6 OK: the exclusion constraint catches overlaps including buffers';
  END;

  -- Another moderator is allowed at the same time.
  INSERT INTO session_moderator (session_id, org_id, moderator_id, busy)
  VALUES (s2, '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-0000000000a2',
          tstzrange('2026-12-01 10:55+01', '2026-12-01 12:25+01', '[)'));
  RAISE NOTICE 'T6 OK: another moderator is allowed at the same time';
END
$$;

-- T7: audit is append-only. UPDATE and DELETE are forbidden.
DO $$
DECLARE aid bigint;
BEGIN
  PERFORM set_config('app.org_id', '00000000-0000-0000-0000-00000000000a', true);
  INSERT INTO audit_event (org_id, entity, entity_id, action, after_state)
  VALUES ('00000000-0000-0000-0000-00000000000a', 'study', 'x', 'CREATE', '{"title":"A"}')
  RETURNING id INTO aid;

  -- For app_rt, grants revoke UPDATE/DELETE (first guard); the trigger is the second guard.
  BEGIN
    UPDATE audit_event SET action = 'TAMPER' WHERE id = aid;
    RAISE EXCEPTION 'T7 FAIL: audit UPDATE succeeded';
  EXCEPTION WHEN insufficient_privilege THEN
    RAISE NOTICE 'T7 OK: grants prevent audit UPDATE';
  END;

  BEGIN
    DELETE FROM audit_event WHERE id = aid;
    RAISE EXCEPTION 'T7 FAIL: audit DELETE succeeded';
  EXCEPTION WHEN insufficient_privilege THEN
    RAISE NOTICE 'T7 OK: grants prevent audit DELETE';
  END;
END
$$;

RESET ROLE;

-- T8: the append-only trigger also applies to the owner (without grants).
DO $$
BEGIN
  BEGIN
    UPDATE audit_event SET action = 'TAMPER';
    RAISE EXCEPTION 'T8 FAIL: the trigger did not fire';
  EXCEPTION WHEN raise_exception THEN
    RAISE NOTICE 'T8 OK: the append-only trigger provides a second guard';
  END;
END
$$;
\echo '=== all checks passed ==='
