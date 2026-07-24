CREATE TABLE pipeline_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE pipeline_step (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pipeline_id BIGINT NOT NULL,
    action_key VARCHAR(255) NOT NULL,
    sequence_order INT NOT NULL,
    CONSTRAINT fk_pipeline_step_pipeline FOREIGN KEY (pipeline_id) REFERENCES pipeline_definition(id) ON DELETE CASCADE,
    INDEX idx_pipeline_step_pipeline (pipeline_id)
);
