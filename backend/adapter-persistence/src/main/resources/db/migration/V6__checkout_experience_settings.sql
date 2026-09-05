-- Local developer scenario selection. Production integrations supply their
-- own application identifier and do not rely on this singleton.
INSERT INTO application_record
    (id,application_number,customer_id,policy_name,distribution_channel,payment_plan,
     amount_jpy,selected_method,state,configuration_version)
SELECT 5,'APP-20260904-025',4,'Annuity','PA','MONTHLY',25000,'card','READY',
       (SELECT version FROM configuration_release WHERE status='PUBLISHED')
WHERE NOT EXISTS (SELECT 1 FROM application_record WHERE customer_id=4);

CREATE TABLE checkout_experience_settings (
    id INTEGER PRIMARY KEY CHECK (id=1),
    selected_application_number TEXT NOT NULL REFERENCES application_record(application_number),
    configuration_token_required INTEGER NOT NULL DEFAULT 1
        CHECK (configuration_token_required IN (0,1)),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
INSERT INTO checkout_experience_settings
    (id,selected_application_number,configuration_token_required)
VALUES (1,'APP-20260821-001',1);
