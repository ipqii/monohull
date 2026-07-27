# Changelog

All notable changes to Monohull are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/), and Monohull uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed
- New brand mark: a heeling sailboat replaces the rocket across the app icon, favicon,
  PWA icons, sidebar, mobile app bar, and login card. Source artwork lives in
  `docs/brand/`; every PNG derivative is generated from `frontend/public/monohull-icon.svg`.

### Added
- **DB Command** on image templates and per-environment config: the argument list
  handed to the DB image's entrypoint. Database images that restore a backup only
  when passed an argument (e.g. `restore`) previously had no way to receive one
  from Monohull, so they silently came up with an empty database.
- The database is now checked for a Maximo schema as soon as it reports ready, and
  the build fails there — naming DB Command when the image's own startup log shows
  it took no restore branch — instead of surfacing several pipeline actions later
  as a bare vendor error. Set `monohull.build.verify-db-schema=false` when a
  pipeline action is what creates the schema.
- The same check now also confirms the database is listening on **DB Container
  Port** before the pipeline runs. DB-role actions use the local command-line
  processor over IPC and pass regardless, so a wrong port previously surfaced as a
  `Connection refused` stack trace from UpdateDB several steps later. On DB2 the
  failure reports the port the image is really on, resolved through `SVCENAME`.
- **DB Volume Target** on image templates: where the database volume is mounted
  inside the DB container. It was hardcoded to `/database` (DB2) and `/opt/oracle`
  (Oracle), which silently persists nothing when an image keeps its data elsewhere
  — the database ends up in the container's writable layer and is lost whenever the
  container is recreated. Blank keeps the previous defaults, and the build log now
  says which default it used.

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
