ALTER TABLE hpdi_estimate_request DROP CONSTRAINT hpdi_estimate_request_lifecycle_check;
ALTER TABLE hpdi_estimate_request ADD CONSTRAINT hpdi_estimate_request_lifecycle_check CHECK (
    (status='PENDING' AND decided_by IS NULL AND decided_at IS NULL AND decision_note IS NULL
        AND superseded_by IS NULL AND superseded_at IS NULL AND superseded_reason IS NULL)
    OR (status IN ('APPROVED','REJECTED') AND decided_by IS NOT NULL AND decided_at IS NOT NULL
        AND superseded_by IS NULL AND superseded_at IS NULL AND superseded_reason IS NULL)
    OR (status='SUPERSEDED' AND decided_by IS NULL AND decided_at IS NULL AND decision_note IS NULL
        AND superseded_by IS NOT NULL AND superseded_at IS NOT NULL
        AND superseded_reason IN ('ASSIGNMENT_CHANGED','CLASSIFICATION_CHANGED'))
);
ALTER TABLE hpdi_alert_event DROP CONSTRAINT hpdi_alert_event_type_check;
ALTER TABLE hpdi_alert_event ADD CONSTRAINT hpdi_alert_event_type_check CHECK (
    type IN ('START_OVERDUE','SLA_WARNING','SLA_BREACHED','PENDING_TOO_LONG','CRITICAL_NEW',
        'NO_ONE_ON_DUTY','ASSIGNEE_OFF_DUTY','CLASSIFICATION_CHANGED')
);
