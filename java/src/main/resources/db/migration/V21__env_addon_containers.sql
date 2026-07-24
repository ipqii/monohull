-- Opt-in helper containers attached to an environment's bridge network:
--   * mock-receiver (monohull/mock-receiver:latest) for capturing outbound HTTP integrations
--   * Mailpit (axllent/mailpit:latest) for capturing outbound email
-- Both are off by default; flags + host-port columns live alongside the existing app/db ports.

ALTER TABLE environment_config
    ADD COLUMN mock_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN mock_host_port INT DEFAULT NULL,
    ADD COLUMN smtp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN smtp_host_port INT DEFAULT NULL,
    ADD COLUMN smtp_ui_host_port INT DEFAULT NULL;
