-- Per-build override for the workspace bind source. When set, BuildService mounts this host
-- path at /workspace/<imageConfigBasename> instead of ImageConfig.workspacePath — used by PR
-- builds to mount the per-PR checkout while keeping the folder name the build pipeline expects.
ALTER TABLE environment_config ADD COLUMN workspace_path_override VARCHAR(1000) NULL;
