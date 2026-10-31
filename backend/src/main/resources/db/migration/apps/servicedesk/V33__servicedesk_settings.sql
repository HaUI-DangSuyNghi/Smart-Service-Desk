create table servicedesk_settings (
    id integer primary key check (id = 1),
    version bigint not null check (version >= 1),
    payload jsonb not null check (jsonb_typeof(payload) = 'object'
        and payload ? 'categoryGroups' and jsonb_typeof(payload->'categoryGroups') = 'object'
        and payload ? 'estimateDefaults' and jsonb_typeof(payload->'estimateDefaults') = 'object'),
    updated_at timestamptz not null,
    updated_by varchar(100) not null check (length(trim(updated_by)) > 0)
);
insert into servicedesk_settings values (1, 1, '{
  "categoryGroups":{"HARDWARE":"hardware","NETWORK":"network","SOFTWARE":"software","ACCOUNT":"sysadmin","OTHER":null},
  "estimateDefaults":{
    "HARDWARE":{"LOW":45,"MEDIUM":60,"HIGH":90,"CRITICAL":120},
    "NETWORK":{"LOW":30,"MEDIUM":45,"HIGH":60,"CRITICAL":90},
    "SOFTWARE":{"LOW":30,"MEDIUM":30,"HIGH":45,"CRITICAL":60},
    "ACCOUNT":{"LOW":15,"MEDIUM":15,"HIGH":20,"CRITICAL":30},
    "OTHER":{"LOW":30,"MEDIUM":45,"HIGH":60,"CRITICAL":90}
  }
}', current_timestamp, 'system:bootstrap');
