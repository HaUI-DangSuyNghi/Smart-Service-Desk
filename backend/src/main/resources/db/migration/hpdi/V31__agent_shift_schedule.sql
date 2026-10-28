CREATE TABLE hpdi_agent_schedule (
    workspace varchar(100) NOT NULL,
    subject varchar(100) NOT NULL,
    version bigint NOT NULL CHECK (version > 0),
    enabled boolean NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    PRIMARY KEY (workspace, subject)
);
CREATE TABLE hpdi_agent_shift (
    workspace varchar(100) NOT NULL,
    subject varchar(100) NOT NULL,
    version bigint NOT NULL,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    timezone varchar(100) NOT NULL,
    CHECK (ends_at > starts_at),
    PRIMARY KEY (workspace, subject, version, starts_at),
    FOREIGN KEY (workspace, subject) REFERENCES hpdi_agent_schedule(workspace, subject)
);
