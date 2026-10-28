ALTER TABLE hpdi_estimate_request
    ADD COLUMN superseded_by varchar(100),
    ADD COLUMN superseded_at timestamptz,
    ADD COLUMN superseded_reason varchar(100);

ALTER TABLE hpdi_estimate_request DROP CONSTRAINT hpdi_estimate_request_status_check;
ALTER TABLE hpdi_estimate_request DROP CONSTRAINT hpdi_estimate_request_check;
ALTER TABLE hpdi_estimate_request
    ADD CONSTRAINT hpdi_estimate_request_status_check
        CHECK (status IN ('PENDING','APPROVED','REJECTED','SUPERSEDED')),
    ADD CONSTRAINT hpdi_estimate_request_lifecycle_check CHECK (
        (status='PENDING' AND decided_by IS NULL AND decided_at IS NULL AND decision_note IS NULL
            AND superseded_by IS NULL AND superseded_at IS NULL AND superseded_reason IS NULL)
        OR (status IN ('APPROVED','REJECTED') AND decided_by IS NOT NULL AND decided_at IS NOT NULL
            AND superseded_by IS NULL AND superseded_at IS NULL AND superseded_reason IS NULL)
        OR (status='SUPERSEDED' AND decided_by IS NULL AND decided_at IS NULL AND decision_note IS NULL
            AND superseded_by IS NOT NULL AND superseded_at IS NOT NULL
            AND superseded_reason IS NOT NULL AND superseded_reason='ASSIGNMENT_CHANGED')
    );
