-- Static host ports defined on the image-config template, so they're set once per
-- client/project and the Create Environment dialog only needs the staticPorts toggle.
-- All nullable: when null, the env falls back to dynamic allocation (or the request
-- can still pass values explicitly for one-off API callers).

ALTER TABLE image_config
    ADD COLUMN app_http_port INT DEFAULT NULL,
    ADD COLUMN app_https_port INT DEFAULT NULL,
    ADD COLUMN db_port INT DEFAULT NULL,
    ADD COLUMN mock_host_port INT DEFAULT NULL,
    ADD COLUMN smtp_host_port INT DEFAULT NULL,
    ADD COLUMN smtp_ui_host_port INT DEFAULT NULL;
