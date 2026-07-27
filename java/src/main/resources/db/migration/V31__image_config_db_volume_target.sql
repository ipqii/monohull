-- Mount point inside the DB container for the environment's database volume.
--
-- The target was hardcoded per vendor (/database for DB2, /opt/oracle for Oracle), which
-- silently persists nothing when an image keeps its data elsewhere — the volume mounts over
-- an empty directory, the database lives in the container's writable layer, and recreating
-- the container throws the whole database away. Like db_container_port, this is a property
-- of the image rather than of the environment, so it lives on image_config only.
--
-- NULL keeps the previous per-vendor defaults.

ALTER TABLE image_config
    ADD COLUMN db_volume_target VARCHAR(255) DEFAULT NULL;
