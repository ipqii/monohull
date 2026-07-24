-- SSH clone support for connected repositories. auth_method selects how MADE
-- authenticates the clone: HTTPS (existing clone_username/clone_token) or SSH
-- (ssh_private_key + optional ssh_passphrase deploy key). Both secrets are
-- write-only, mirroring clone_token. Existing rows default to HTTPS.
ALTER TABLE connected_repository
    ADD COLUMN auth_method VARCHAR(20) NOT NULL DEFAULT 'HTTPS' AFTER provider,
    ADD COLUMN ssh_private_key TEXT AFTER clone_token,
    ADD COLUMN ssh_passphrase VARCHAR(500) AFTER ssh_private_key;
