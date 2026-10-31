-- Editing the original request starts a fresh context without deleting earlier answers.
ALTER TABLE servicedesk_ticket ADD COLUMN content_revision bigint NOT NULL DEFAULT 0
    CHECK (content_revision >= 0 AND content_revision <= context_revision);
