# Monohull

[![CI](https://github.com/ipqii/monohull/actions/workflows/ci.yml/badge.svg)](https://github.com/ipqii/monohull/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/ipqii/monohull)](https://github.com/ipqii/monohull/releases)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

> Automated development environments **for IBM Maximo®** — one click from empty Docker
> host to a running Maximo environment.

<!-- TODO before launch: dashboard screenshot here (MXF-23) -->

Standing up a Maximo development environment by hand takes days: database restore,
SMP installation, EAR builds, updatedb, app-server configuration, integration stubs.
Monohull turns that into a web form. Define an **image configuration** once (which
database, which Maximo images, which volumes), and every environment after that is a
button: provisioned, built, started, monitored, and torn down from a browser.

Monohull is a single Spring Boot + React application that drives your Docker host —
it creates and orchestrates the database, Maximo admin, and application containers for
each environment, streams their build and runtime logs live, and cleans up completely
when an environment is deleted.

## Features

- **Environments on demand** — create a full Maximo environment (DB + admin + app
  containers on an isolated network) from a saved image configuration; start, stop,
  rebuild, or tear down from the dashboard, with live log streaming throughout.
- **Built-in build pipeline** — EAR build, UpdateDB (with pre-processor), and
  vendor-specific database fixes run as ordered, resumable **actions**; add your own
  custom actions (shell against any container role, or containerized builder tasks).
- **PR builds** — connect a GitHub / Bitbucket / GitLab repository via webhook and
  Monohull builds every pull request, optionally standing up a full ephemeral
  environment per PR, removed when the PR closes.
- **Integration test add-ons** — opt-in per environment: a **mock receiver** that
  captures outbound Maximo integrations (with a rules engine and inspection UI) and a
  throwaway **SMTP sink** for mail testing.
- **Shareable configuration** — export image configs and custom actions as a YAML
  bundle; import them on another Monohull instance.
- **Team-ready** — user logins, seeded admin account, optional bearer-key API access
  for dashboards and automation, per-environment public URLs behind a reverse proxy.

## What you need

- A Linux Docker host (Docker Desktop on Windows/macOS works for evaluation).
- **Your own IBM Maximo installation media and licenses.** Monohull ships no IBM
  software whatsoever — it orchestrates containers built from images *you* provide
  under *your* IBM entitlement (e.g. a DB2 image with your restored Maximo database
  and an SMP/WebSphere Liberty admin image from your installation).
- Java 17 + Maven only if you build from source; the released container needs neither.

## Quickstart

No clone needed — the prebuilt image is on GHCR (`ghcr.io/ipqii/monohull`, amd64 + arm64):

**Linux / macOS (bash):**

```bash
curl -O https://raw.githubusercontent.com/ipqii/monohull/main/deploy/docker-compose.yml
MONOHULL_ADMIN_PASSWORD='choose-something' docker compose up -d
```

**Windows (PowerShell):**

```powershell
curl.exe -O https://raw.githubusercontent.com/ipqii/monohull/main/deploy/docker-compose.yml
$env:MONOHULL_ADMIN_PASSWORD = 'choose-something'
docker compose up -d
```

> The `VAR=value command` prefix on the bash line is bash-only syntax. In PowerShell the
> variable must be set with `$env:` as above — rewriting the bash line as
> `MONOHULL_ADMIN_PASSWORD='...'; docker compose up -d` leaves the variable unset and
> docker compose will never see it.

(Working from a clone instead? `docker compose up -d --build` in the repo root builds
from source with the same layout. Careful: that dev-oriented compose file falls back to
`admin`/`admin` when `MONOHULL_ADMIN_PASSWORD` isn't set, rather than refusing to start.)

Open `http://localhost:8806` and log in as `admin`. First steps in the UI:

1. **Registry** — point Monohull at the registry that holds your Maximo images.
2. **Image configs** — define your first client/project image configuration.
3. **Create environment** — pick the config, name the environment, click create, and
   watch the build log.

The full walkthrough lives in [docs/INSTALL.md](docs/INSTALL.md) and
[docs/USER_GUIDE.md](docs/USER_GUIDE.md).

## Configuration

Everything is environment-variable driven; the important ones:

| Variable | Purpose | Default |
|---|---|---|
| `MONOHULL_ADMIN_USERNAME` / `MONOHULL_ADMIN_PASSWORD` | Initial login seeded on first boot | `admin` / *(set one!)* |
| `MONOHULL_API_KEY` | Static bearer key for read-only service access; empty disables | *(empty)* |
| `MONOHULL_NETWORK_SUBNET_POOL` | /16 block carved into per-environment /24 networks | `10.100.0.0/16` |
| `MONOHULL_MAXIMO_DOMAIN` | Base domain for per-environment public Maximo URLs (reverse-proxy labels); empty = LAN-only | *(empty)* |
| `MONOHULL_PUBLIC_BASE_URL` | Public URL Monohull itself is reachable at (webhook rendering) | *(empty)* |
| `MONOHULL_PR_BUILDS_MAX_CONCURRENT` | Concurrent PR builds cap | `2` |
| `APP_DOCKER_HOST` | Docker daemon endpoint (`unix://…` or `npipe://…`) | unix socket |

## Building from source

```bash
mvn clean package        # builds the React frontend and the Spring Boot jar
docker compose up -d --build
```

The Playwright e2e suite runs against a live instance:
`docker compose --profile test run --rm e2e`.

## Contributing

Issues and pull requests are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[Apache License 2.0](LICENSE).

---
*Maximo® is a registered trademark of International Business Machines Corporation.
Monohull is an independent open-source project and is not affiliated with, endorsed
by, or sponsored by IBM. Monohull contains no IBM software; users must hold their own
licenses for any IBM software they run with it.*
