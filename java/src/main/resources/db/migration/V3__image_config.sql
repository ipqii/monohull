CREATE TABLE image_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client VARCHAR(255) NOT NULL,
    project VARCHAR(255) NOT NULL,
    maximo_version VARCHAR(100) NOT NULL,
    app_image VARCHAR(500) NOT NULL,
    db_image VARCHAR(500) NOT NULL,
    adm_image VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_image_config (client, project, maximo_version)
);

ALTER TABLE environment ADD COLUMN image_config_id BIGINT;
ALTER TABLE environment ADD CONSTRAINT fk_env_image_config FOREIGN KEY (image_config_id) REFERENCES image_config(id);
