ALTER TABLE servicedesk_confirmation ADD COLUMN rollout_hold boolean NOT NULL DEFAULT false;
ALTER TABLE servicedesk_confirmation ADD COLUMN rollout_reason varchar(50);
ALTER TABLE servicedesk_confirmation ADD COLUMN reopen_alerted_at timestamptz;

DROP INDEX servicedesk_confirmation_pending;
CREATE INDEX servicedesk_confirmation_pending ON servicedesk_confirmation(checked_at,workspace,generation)
    WHERE (decision IS NULL AND NOT rollout_hold) OR (decision='REWORK' AND reopen_received_at IS NULL AND reopen_alerted_at IS NULL);
ALTER TABLE servicedesk_confirmation ADD CONSTRAINT servicedesk_confirmation_rollout_hold
    CHECK (rollout_hold = (rollout_reason IS NOT NULL) AND (rollout_reason IS NULL OR rollout_reason='OVERDUE_AT_ROLLOUT'));
ALTER TABLE servicedesk_confirmation ADD CONSTRAINT servicedesk_confirmation_reopen_alert
    CHECK (reopen_alerted_at IS NULL OR (decision IS NOT DISTINCT FROM 'REWORK' AND reopen_alerted_at>=reopen_deadline_at));
