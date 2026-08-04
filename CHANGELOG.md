# Changelog

All notable changes to Monohull are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/), and Monohull uses
[Semantic Versioning](https://semver.org/).

## [1.0.1] — 2026-08-04

### Changed
- New brand mark: a heeling sailboat replaces the rocket across the app icon, favicon,
  PWA icons, sidebar, mobile app bar, and login card. Source artwork lives in
  `docs/brand/`; every PNG derivative is generated from `frontend/public/monohull-icon.svg`.

### Added
- **Terminal** button on every container card: an interactive shell (xterm.js in the
  browser, bridged over a websocket to `docker exec` with a real PTY) straight into the
  DB, APP, ADM, or addon container — no SSH to the docker host needed. Prefers bash and
  falls back to sh for slim images, resizes with the window, and requires the same
  login session as the rest of the API.
- The pipeline builder shows where a dragged action will land: the steps below the
  pointer move down to open a gap. Hovering a step inserts before it and the space
  below the list adds to the end, so the list moves exactly once per step the
  pointer crosses. Previously it didn't react at all until the drop.
- Jump from an action in a pipeline straight to its definition. Pipeline steps —
  in the builder, in the Available Actions palette, and on an environment's
  Pipeline tab — carry a link to the action editor, which is the shortest route
  from a failed step to the command that failed. It opens in a new tab, so an
  unsaved pipeline survives the detour.
- **Swap web.xml to dev variant** pipeline action, sequenced before Build EAR. Maximo
  ships two sets of web deployment descriptors and the ant targets that choose between
  them are commented out in the vanilla `maximo-all.xml`, so the EAR was always built
  with security-constraints on `/ui/*` and `/oslc/*` under BASIC auth. Liberty answered
  those before Maximo ran, giving a browser credential popup instead of Maximo's login
  page — and, with no user registry in the dev `server.xml`, no credentials could
  satisfy it. This is the deployment-descriptor counterpart to the existing
  *Swap server.xml to dev variant*.
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

[1.0.1]: https://github.com/ipqii/monohull/releases/tag/v1.0.1
[1.0.0]: https://github.com/ipqii/monohull/releases/tag/v1.0.0
