-- The original prototype setting protected configuration writes only. The UI
-- presents one operator-token switch, so its persisted name and behavior now
-- cover every operator mutation (capture, MIT, preferences, batches, SFTP and
-- configuration). Existing installations retain their current boolean value.
ALTER TABLE checkout_experience_settings
    RENAME COLUMN configuration_token_required TO operator_token_required;
