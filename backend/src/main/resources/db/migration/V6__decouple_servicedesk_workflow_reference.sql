-- The process identifier is an opaque capability reference, not a shared-table FK.
ALTER TABLE servicedesk_ticket DROP CONSTRAINT servicedesk_ticket_process_instance_id_fkey;
