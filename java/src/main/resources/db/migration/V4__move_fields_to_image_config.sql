ALTER TABLE image_config ADD COLUMN db_vendor VARCHAR(10) NOT NULL DEFAULT 'DB2';
ALTER TABLE image_config ADD COLUMN host_volume_path VARCHAR(500);
ALTER TABLE image_config ADD COLUMN db_volume_name VARCHAR(200);
