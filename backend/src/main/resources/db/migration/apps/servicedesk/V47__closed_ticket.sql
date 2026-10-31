ALTER TABLE servicedesk_ticket ADD COLUMN closed_at timestamptz;
ALTER TABLE servicedesk_intake_admission DROP CONSTRAINT servicedesk_intake_admission_status_check;
ALTER TABLE servicedesk_intake_admission ADD CONSTRAINT servicedesk_intake_admission_status_check CHECK (status IN (
    'NEW','NEEDS_ENRICHMENT','DUPLICATE_HOLD','FINAL_ASSESSMENT_QUEUED','ASSESSING',
    'REVIEW_RELEASED','MANUAL_REQUIRED','REVIEWED','CANONICAL_FOLLOWER','CANCELLED','CLOSED'));
