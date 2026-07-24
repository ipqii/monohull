-- Per-environment database password, passed to the DB container as MAXIMO_DB_PASSWORD.
-- Used by the restore script (and any other in-image tooling) that needs to authenticate
-- to the Maximo DB without hard-coding credentials in the image.

ALTER TABLE environment_config
    ADD COLUMN db_password VARCHAR(255) DEFAULT NULL;
