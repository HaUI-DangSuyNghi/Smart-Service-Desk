DO $$
BEGIN
  IF (SELECT count(DISTINCT process_key) FROM process_scope) < 2 THEN
    RAISE EXCEPTION 'Run smoke first: both process definitions must have evidence';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM metadata) THEN RAISE EXCEPTION 'Run smoke first: metadata evidence missing'; END IF;
  IF EXISTS (
    SELECT 1 FROM metadata record WHERE
      (SELECT count(*) FROM audit_event audit WHERE audit.action='metadata.create' AND audit.resource=record.id::text AND audit.actor=record.owner AND audit.workspace=record.workspace) <> 1
  ) THEN RAISE EXCEPTION 'Metadata audit mismatch'; END IF;
  IF EXISTS (SELECT 1 FROM act_hi_procinst history LEFT JOIN process_scope scope ON scope.id=history.id_ WHERE scope.id IS NULL) THEN
    RAISE EXCEPTION 'Orphan engine history: possible rollback failure';
  END IF;
  IF EXISTS (SELECT 1 FROM process_scope scope LEFT JOIN act_hi_procinst history ON scope.id=history.id_ WHERE history.id_ IS NULL) THEN
    RAISE EXCEPTION 'Scope without engine history';
  END IF;
  IF EXISTS (
    SELECT 1 FROM process_scope scope WHERE
      (SELECT count(*) FROM audit_event audit WHERE audit.action='process.start' AND audit.resource=scope.id AND audit.actor=scope.owner AND audit.workspace=scope.workspace) <> 1
  ) THEN RAISE EXCEPTION 'Process audit mismatch'; END IF;
  IF EXISTS (
    SELECT 1 FROM act_hi_taskinst task WHERE task.end_time_ IS NOT NULL AND
      (SELECT count(*) FROM audit_event audit WHERE audit.action='task.complete' AND audit.resource=task.id_ AND audit.actor=task.assignee_) <> 1
  ) THEN RAISE EXCEPTION 'Task completion audit mismatch'; END IF;
  IF EXISTS (SELECT 1 FROM audit_event WHERE request_id IS NULL OR occurred_at IS NULL) THEN
    RAISE EXCEPTION 'Missing audit request ID/time';
  END IF;
END $$;
SELECT 'PASS engine/scope rollback and audit consistency' AS result;
