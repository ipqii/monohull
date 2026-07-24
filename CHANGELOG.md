# Changelog

All notable changes to Monohull are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/), and Monohull uses
[Semantic Versioning](https://semver.org/).

## [1.0.0] — 2026-07-24

First public release.

### Added
- **Environments on demand**: provision a full IBM Maximo® development environment
  (DB2/Oracle database, WebSphere/Liberty application server, admin/build container on
  an isolated Docker network) from a saved image configuration, with live build-log
  streaming, start/stop/rebuild/teardown from the dashboard.
- **Build pipelines**: the standard Maximo build sequence (EAR build, UpdateDB with
  pre-processor, vendor DB fixes, app start, credential reset) as ordered, re-runnable
  actions; custom actions (container exec, host-level, or ephemeral builder container)
  and drag-and-drop pipeline authoring.
- **One-click profiles**: image config + pipeline + launch defaults as a shareable
  YAML bundle; a fresh install can import a shared profile and go straight to a
  building environment.
- **Per-pull-request builds**: connect GitHub/Bitbucket/GitLab repos via webhook;
  build checks or full ephemeral environments per PR, torn down on close.
- **Integration-testing add-ons**: opt-in mock receiver (captures outbound Maximo
  integrations, scriptable responses, inspection UI) and SMTP capture inbox (Mailpit).
- **Team features**: user logins with seeded admin, optional read-only API key,
  per-environment public URLs behind a reverse proxy.
- **Operational hardening**: idempotent rebuilds (leftover containers are replaced,
  failed starts can't orphan), complete best-effort teardown with retry, and
  plain-English cause+fix messages for the common Docker/registry/in-container
  failure modes.

[1.0.0]: https://github.com/ipqii/monohull/releases/tag/v1.0.0
