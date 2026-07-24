ALTER TABLE custom_action ADD COLUMN environment_id BIGINT;
ALTER TABLE custom_action ADD CONSTRAINT fk_custom_action_environment
    FOREIGN KEY (environment_id) REFERENCES environment(id)
    ON DELETE SET NULL;

ALTER TABLE pipeline_definition ADD COLUMN environment_id BIGINT;
ALTER TABLE pipeline_definition ADD CONSTRAINT fk_pipeline_definition_environment
    FOREIGN KEY (environment_id) REFERENCES environment(id)
    ON DELETE SET NULL;

ALTER TABLE environment ADD COLUMN pipeline_definition_id BIGINT;
ALTER TABLE environment ADD CONSTRAINT fk_environment_pipeline
    FOREIGN KEY (pipeline_definition_id) REFERENCES pipeline_definition(id)
    ON DELETE SET NULL;
