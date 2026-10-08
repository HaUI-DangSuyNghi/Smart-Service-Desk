-- Read-only acceptance check. psql variables come from the API probe evidence.
-- This inspection is for N1 acceptance, not an Intelligence runtime DB consumer.
\set ON_ERROR_STOP on
BEGIN TRANSACTION READ ONLY;
SELECT count(*) = 1 AS metadata_exists
FROM metadata
WHERE id = :'metadata_id'::uuid AND owner = :'owner' AND workspace = :'workspace'
\gset
\if :metadata_exists
\else
  \echo FAIL expected metadata row absent or authority mismatch
  -- psql 16 ignores an exit code supplied to \quit; ON_ERROR_STOP needs an SQL error.
  SELECT 1 / 0;
\endif

SELECT count(*) = 1 AND count(*) FILTER (
    WHERE actor = :'owner' AND workspace = :'workspace'
      AND request_id = :'request_id' AND occurred_at IS NOT NULL
) = 1 AS audit_matches
FROM audit_event
WHERE action = 'metadata.create' AND resource = :'metadata_id'
\gset
\if :audit_matches
\else
  \echo FAIL metadata.create must appear exactly once with matching API request context
  SELECT 1 / 0;
\endif
COMMIT;
\echo PASS META-12 exactly one metadata.create with actor/resource/workspace/request_id/time
