CREATE TABLE hpdi_alert_event (
    id uuid PRIMARY KEY,
    workspace varchar(100) NOT NULL,
    type varchar(40) NOT NULL CHECK (type IN ('START_OVERDUE','SLA_WARNING','SLA_BREACHED','PENDING_TOO_LONG','CRITICAL_NEW','NO_ONE_ON_DUTY','ASSIGNEE_OFF_DUTY')),
    severity varchar(20) NOT NULL CHECK (severity IN ('INFO','WARNING','CRITICAL')),
    process_instance_id varchar(100) NOT NULL,
    process_key varchar(100) NOT NULL,
    business_key varchar(200) NOT NULL,
    task_id varchar(100),
    generation varchar(200) NOT NULL,
    label varchar(300) NOT NULL,
    coordinators boolean NOT NULL,
    agent varchar(100),
    created_at timestamptz NOT NULL,
    retry_at timestamptz NOT NULL,
    delivered_at timestamptz,
    UNIQUE (workspace,process_instance_id,generation,type),
    UNIQUE (workspace,id)
);
CREATE INDEX hpdi_alert_delivery ON hpdi_alert_event(retry_at,id) WHERE delivered_at IS NULL;
CREATE TABLE hpdi_alert_recipient (
    workspace varchar(100) NOT NULL,
    event_id uuid NOT NULL,
    subject varchar(100) NOT NULL,
    read_at timestamptz,
    PRIMARY KEY (workspace,event_id,subject),
    FOREIGN KEY (workspace,event_id) REFERENCES hpdi_alert_event(workspace,id)
);
CREATE INDEX hpdi_alert_inbox ON hpdi_alert_recipient(workspace,subject,event_id);
