# Quickstart

From a fresh Docker host to your **first Maximo environment building** in about
ten minutes: Monohull itself is up in two, the rest is telling it about your
images.

!!! note "What you need before you start"
    - A Docker host (Linux server, or Docker Desktop on Windows/macOS for
      evaluation).
    - **Your own Maximo container images** — a database image (DB2 or Oracle,
      typically with your restored Maximo database), a WebSphere/Liberty
      application-server image, and an SMP admin/build image, in a registry the
      host can reach. Monohull ships no IBM software; it orchestrates images you
      build under your own IBM entitlement.

    No Maximo images yet? You can still do steps 1–2 and explore the dashboard —
    everything up to the actual build works without them.

## 1. Start Monohull *(~2 minutes)*

The prebuilt multi-arch image is on GHCR (`ghcr.io/ipqii/monohull`). Grab the
canonical compose file and start it, choosing an admin password:

=== "Linux / macOS (bash)"

    ```bash
    curl -O https://raw.githubusercontent.com/ipqii/monohull/main/deploy/docker-compose.yml
    MONOHULL_ADMIN_PASSWORD='choose-something' docker compose up -d
    ```

=== "Windows (PowerShell)"

    ```powershell
    curl.exe -O https://raw.githubusercontent.com/ipqii/monohull/main/deploy/docker-compose.yml
    $env:MONOHULL_ADMIN_PASSWORD = 'choose-something'
    docker compose up -d
    ```

    !!! warning
        The bash one-liner's `VAR=value command` prefix doesn't exist in
        PowerShell — always set the variable via `$env:` as above. Also edit the
        compose file per its Windows note: remove the Docker-socket volume and
        set `APP_DOCKER_HOST: npipe:////./pipe/docker_engine`.

Open `http://localhost:8806` and sign in as `admin` with the password you chose.

## 2. Point Monohull at your registry *(~1 minute)*

If your Maximo images live in a private registry: **Registry** (sidebar) → enter
the registry URL, username, and password → **Save**.

## 3. Create a template *(~3 minutes)*

A **template** (image config) is the recipe environments are stamped out from.
**Environments → New Environment**, fill the required fields:

- **Client / Project** — labels, e.g. `acme` / `eam`.
- **Maximo Version** — `7.6.1.x` or `MAS`.
- **App / DB / ADM Image** — your three image references.
- **Database Vendor** — DB2 or Oracle.

Under **Pipeline**, keep the default build pipeline. **Save**.

## 4. Build your first environment *(~1 minute of clicking)*

**Dashboard → New Build** → pick your template → **Create**.

The environment card walks through `CREATING → BUILDING → CONFIGURING → RUNNING`.
Click the card to watch the build log stream live — EAR build, UpdateDB, config
fixes, app-server start, all automatic.

!!! info "How long until RUNNING?"
    That depends entirely on your images and pipeline — a restored demo database
    builds in minutes; a full UpdateDB on an industry solution can take an hour.
    The point is nobody has to babysit it: the pipeline runs unattended and the
    log is there when you come back.

## 5. Log into Maximo

When the card shows **RUNNING**, open it → **Containers** tab → **Access** card →
**Open Maximo**. Default login `maxadmin` / `maxadmin` (the key icon changes it).
The same card gives you a ready-made JDBC URL for the database.

---

## Where to next

- **[One-click profiles](guide/templates-profiles.md#one-click-profiles)** — turn
  your template into a shareable `.bundle.yaml` so a teammate goes from empty
  Monohull to a building environment in one click.
- **[Per-pull-request builds](guide/pr-builds.md)** — a fresh Maximo for every PR.
- **[Integration-testing helpers](guide/integration-helpers.md)** — capture
  outbound interfaces and email safely.
- **[Installation guide](install.md)** — production-ish setup, all configuration
  variables, public URL routing.
