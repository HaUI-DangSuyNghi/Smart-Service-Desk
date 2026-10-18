-- Owned by Tier-3 Room Request. No foreign keys to engine/Core storage internals.
create table roomrequest_request (
    id uuid primary key,
    workspace varchar(50) not null,
    creator varchar(200) not null,
    room varchar(50) not null,
    knowledge_id uuid,
    process_id varchar(100) not null unique,
    created_at timestamptz not null default now()
);
create index roomrequest_workspace_creator on roomrequest_request(workspace,creator);
