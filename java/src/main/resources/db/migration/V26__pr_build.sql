-- One build triggered by a PR webhook. build_id keys the SSE log stream (LogSink),
-- mirroring environment builds. environment_id is set only in BUILD_AND_ENV mode.
CREATE TABLE pr_build (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    repository_id BIGINT NOT NULL,
    pr_number INT NOT NULL,
    pr_title VARCHAR(1000),
    source_branch VARCHAR(500) NOT NULL,
    target_branch VARCHAR(500),
    commit_sha VARCHAR(100),
    event_type VARCHAR(30) NOT NULL,               -- OPENED | SYNCHRONIZE | CLOSED
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',  -- QUEUED|CLONING|BUILDING|SUCCESS|FAILED|CANCELLED|SUPERSEDED|REMOVED
    build_id VARCHAR(64) NOT NULL,
    environment_id BIGINT,
    workspace_path VARCHAR(1000),
    error TEXT,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (repository_id) REFERENCES connected_repository(id) ON DELETE CASCADE,
    FOREIGN KEY (environment_id) REFERENCES environment(id) ON DELETE SET NULL
);

CREATE INDEX idx_pr_build_repo ON pr_build (repository_id);
CREATE INDEX idx_pr_build_build_id ON pr_build (build_id);
