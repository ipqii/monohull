-- Per-container extras (env vars + bind mounts) on both image_config and environment_config.
-- Stored as JSON arrays in TEXT columns; deserialised via JPA AttributeConverter.

ALTER TABLE image_config
    ADD COLUMN db_extra_env_json    TEXT DEFAULT NULL,
    ADD COLUMN db_extra_binds_json  TEXT DEFAULT NULL,
    ADD COLUMN app_extra_env_json   TEXT DEFAULT NULL,
    ADD COLUMN app_extra_binds_json TEXT DEFAULT NULL,
    ADD COLUMN adm_extra_env_json   TEXT DEFAULT NULL,
    ADD COLUMN adm_extra_binds_json TEXT DEFAULT NULL;

ALTER TABLE environment_config
    ADD COLUMN db_extra_env_json    TEXT DEFAULT NULL,
    ADD COLUMN db_extra_binds_json  TEXT DEFAULT NULL,
    ADD COLUMN app_extra_env_json   TEXT DEFAULT NULL,
    ADD COLUMN app_extra_binds_json TEXT DEFAULT NULL,
    ADD COLUMN adm_extra_env_json   TEXT DEFAULT NULL,
    ADD COLUMN adm_extra_binds_json TEXT DEFAULT NULL;
