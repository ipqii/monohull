-- A git repository connected to MADE. A PR webhook from this repo triggers a build
-- of the PR branch using the linked image config's pipeline. webhook_secret verifies
-- inbound webhooks; clone_token authenticates HTTPS clones of private repos;
-- status_token is reserved for Phase 2 (posting build status back to the provider).
CREATE TABLE connected_repository (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    provider VARCHAR(20) NOT NULL,                 -- GITHUB | BITBUCKET | GITLAB
    repo_url VARCHAR(1000) NOT NULL,
    repo_full_name VARCHAR(500) NOT NULL,          -- owner/repo, matched against webhook payloads
    default_branch VARCHAR(255) NOT NULL DEFAULT 'main',
    build_mode VARCHAR(20) NOT NULL DEFAULT 'BUILD_ONLY',  -- BUILD_ONLY | BUILD_AND_ENV
    image_config_id BIGINT NOT NULL,
    webhook_secret VARCHAR(100) NOT NULL,
    clone_username VARCHAR(255),
    clone_token VARCHAR(1000),
    status_token VARCHAR(1000),
    max_concurrent INT NOT NULL DEFAULT 2,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (image_config_id) REFERENCES image_config(id),
    UNIQUE KEY uniq_provider_repo (provider, repo_full_name)
);
