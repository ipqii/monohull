CREATE TABLE environment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    build_id VARCHAR(255) NOT NULL UNIQUE,
    network_name VARCHAR(255) NOT NULL,
    maximo_version VARCHAR(100) NOT NULL,
    db_vendor VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE container (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    environment_id BIGINT NOT NULL,
    container_name VARCHAR(255) NOT NULL,
    docker_container_id VARCHAR(255),
    role VARCHAR(10) NOT NULL,
    image VARCHAR(500) NOT NULL,
    ports VARCHAR(255),
    status VARCHAR(30) NOT NULL,
    CONSTRAINT fk_container_environment FOREIGN KEY (environment_id) REFERENCES environment(id) ON DELETE CASCADE
);

CREATE TABLE environment_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    environment_id BIGINT NOT NULL UNIQUE,
    app_config_host_path VARCHAR(500),
    workspace_host_path VARCHAR(500),
    logs_host_path VARCHAR(500),
    db_volume_name VARCHAR(255),
    static_ports BOOLEAN NOT NULL DEFAULT FALSE,
    app_http_port INT,
    app_https_port INT,
    db_port INT,
    CONSTRAINT fk_config_environment FOREIGN KEY (environment_id) REFERENCES environment(id) ON DELETE CASCADE
);

CREATE TABLE build_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    environment_id BIGINT NOT NULL,
    line TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_log_environment FOREIGN KEY (environment_id) REFERENCES environment(id) ON DELETE CASCADE
);

CREATE INDEX idx_container_environment ON container(environment_id);
CREATE INDEX idx_build_log_environment ON build_log(environment_id);
