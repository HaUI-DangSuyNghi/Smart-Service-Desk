create table hpdi_work_settings (
    id integer primary key check (id = 1),
    version bigint not null check (version >= 1),
    payload jsonb not null check (jsonb_typeof(payload) = 'object'
        and jsonb_typeof(payload->'assignment') = 'object' and payload ? 'assignment'
        and jsonb_typeof(payload->'shifts') = 'array' and payload ? 'shifts'
        and jsonb_typeof(payload->'sla') = 'object' and payload ? 'sla'
        and jsonb_typeof(payload->'critical') = 'object' and payload ? 'critical'
        and jsonb_typeof(payload->'alerts') = 'object' and payload ? 'alerts'
        and jsonb_typeof(payload->'closing') = 'object' and payload ? 'closing'
        and jsonb_typeof(payload->'estimate') = 'object' and payload ? 'estimate'),
    updated_at timestamptz not null,
    updated_by varchar(100) not null check (length(trim(updated_by)) > 0)
);
insert into hpdi_work_settings values (1, 1, '{
  "assignment":{"pendingLoadFactor":0.5,"tieThresholdMinutes":15,"maxInProgress":1,"minRemainingMinutes":10},
  "shifts":[{"name":"Morning","start":"07:00","end":"11:30"},{"name":"Afternoon","start":"13:00","end":"17:00"}],
  "sla":{"targets":{"LOW":{"startWithinMinutes":510,"resolveWithinMinutes":2550},"MEDIUM":{"startWithinMinutes":240,"resolveWithinMinutes":1020},"HIGH":{"startWithinMinutes":60,"resolveWithinMinutes":480},"CRITICAL":{"startWithinMinutes":15,"resolveWithinMinutes":120}},"escalateAfterRatio":0.5,"maxEscalatedPriority":"HIGH","waitingExternalCounts":true},
  "critical":{"autoQueueAfterMinutes":5},
  "alerts":{"warnRemainingRatio":0.2,"pendingRemindDays":3,"reopenUnclaimedMinutes":15},
  "closing":{"autoCloseAfterDays":3},
  "estimate":{"minSamples":20,"windowMonths":6,"outlierMinMinutes":2,"outlierMaxMinutes":480}
}', current_timestamp, 'system:bootstrap');
