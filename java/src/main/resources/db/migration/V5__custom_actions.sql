CREATE TABLE custom_action (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action_key VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    target_role VARCHAR(10) NOT NULL,
    command TEXT NOT NULL,
    working_dir VARCHAR(500),
    timeout_seconds INT NOT NULL DEFAULT 300,
    image_config_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (image_config_id) REFERENCES image_config(id) ON DELETE SET NULL
);

CREATE TABLE action_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id VARCHAR(255) NOT NULL UNIQUE,
    action_key VARCHAR(100) NOT NULL,
    environment_id BIGINT NOT NULL,
    container_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    exit_code INT,
    FOREIGN KEY (environment_id) REFERENCES environment(id) ON DELETE CASCADE,
    FOREIGN KEY (container_id) REFERENCES container(id) ON DELETE CASCADE
);

CREATE TABLE action_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id BIGINT NOT NULL,
    line TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (execution_id) REFERENCES action_execution(id) ON DELETE CASCADE
);
