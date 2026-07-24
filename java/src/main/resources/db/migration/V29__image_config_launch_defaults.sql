-- Launch defaults for one-click profile launches (MXF-20). An image config plus these
-- defaults is a "profile": the /api/profiles launch endpoints create an environment from
-- the template without a New Build dialog, using these values in place of user input.
ALTER TABLE image_config ADD COLUMN launch_description VARCHAR(500) NULL;
ALTER TABLE image_config ADD COLUMN launch_static_ports BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE image_config ADD COLUMN launch_include_mock BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE image_config ADD COLUMN launch_include_smtp BOOLEAN NOT NULL DEFAULT FALSE;
