ALTER TABLE image_config ADD COLUMN pipeline_definition_id BIGINT;
ALTER TABLE image_config ADD CONSTRAINT fk_image_config_pipeline
    FOREIGN KEY (pipeline_definition_id) REFERENCES pipeline_definition(id)
    ON DELETE SET NULL;
