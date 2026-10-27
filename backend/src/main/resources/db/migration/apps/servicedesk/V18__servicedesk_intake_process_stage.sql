ALTER TABLE servicedesk_intake_admission
    ADD COLUMN process_stage varchar(20) NOT NULL DEFAULT 'review'
    CHECK (process_stage IN ('intake','review','follower'));
