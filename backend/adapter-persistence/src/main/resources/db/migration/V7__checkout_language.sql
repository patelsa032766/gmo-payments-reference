-- Language belongs to the local checkout scenario so navigation and process
-- restarts do not silently reset the customer's selected experience.
ALTER TABLE checkout_experience_settings
ADD COLUMN checkout_language TEXT NOT NULL DEFAULT 'en'
CHECK (checkout_language IN ('en','ja'));
