create table servicedesk_assignment_cursor (
    workspace varchar(100) not null,
    support_group varchar(100) not null,
    last_subject varchar(100) not null,
    primary key (workspace, support_group)
);

create table servicedesk_assignment_event (
    id bigint generated always as identity primary key,
    workspace varchar(100) not null,
    process_id varchar(100) not null,
    subject varchar(100) not null,
    assigned_at timestamptz not null default clock_timestamp()
);
create index servicedesk_assignment_event_day on servicedesk_assignment_event(workspace, assigned_at, subject);

insert into servicedesk_assignment_event(workspace,process_id,subject,assigned_at)
select audit.workspace,ticket.process_instance_id,
    audit.details->>'assignee',
    audit.occurred_at
from audit_event audit
join servicedesk_ticket ticket on ticket.workspace=audit.workspace
    and ticket.process_instance_id=audit.details->>'processInstanceId'
where ticket.process_key='service-desk-reviewed'
    and audit.action='task.assign' and audit.details->>'taskKey'='handle'
        and audit.details->>'assignee' is not null
        and audit.details->>'assignee' is distinct from audit.details->>'previousAssignee';
