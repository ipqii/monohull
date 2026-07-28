-- Command (argv) handed to the DB container's entrypoint.
--
-- Some Maximo DB images ship an entrypoint that branches on its first argument to
-- decide whether to restore a database backup or leave the freshly created empty
-- one in place. Monohull previously never set a command, so those images silently
-- took the "empty database" path and the failure only surfaced several pipeline
-- actions later as a bare SQL0204N / exit 4.
--
-- NULL/blank means "use the image's own CMD", which is the pre-existing behaviour.

ALTER TABLE image_config
    ADD COLUMN db_command VARCHAR(500) DEFAULT NULL;

ALTER TABLE environment_config
    ADD COLUMN db_command VARCHAR(500) DEFAULT NULL;
