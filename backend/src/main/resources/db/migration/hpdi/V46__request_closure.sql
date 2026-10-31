ALTER TABLE hpdi_request_change DROP CONSTRAINT hpdi_request_change_action_check;
ALTER TABLE hpdi_request_change ADD CONSTRAINT hpdi_request_change_action_check
    CHECK (action IN ('UPDATED','CANCELLED','CLOSED'));
