-- Track which user created an environment so external dashboards can show
-- a user their own environments. Nullable: pre-existing rows have no creator
-- and are intentionally not backfilled. Value is the MADE username, which is
-- the user's O365 email.
ALTER TABLE environment ADD COLUMN created_by VARCHAR(255) NULL;
