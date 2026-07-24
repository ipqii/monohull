ALTER TABLE environment_config ADD COLUMN host_volume_path VARCHAR(500);
ALTER TABLE environment_config DROP COLUMN app_config_host_path;
ALTER TABLE environment_config DROP COLUMN workspace_host_path;
ALTER TABLE environment_config DROP COLUMN logs_host_path;
