# Installing Monohull

Monohull is a single Spring Boot application (with a bundled React UI) that
orchestrates Docker containers on a host. To run it you need a **Docker host**, a
**MariaDB database** for its own state, and the Monohull application itself.

!!! tip "In a hurry?"
    The [Quickstart](quickstart.md) gets you from a fresh Docker host to a
    building Maximo environment in about ten minutes using the prebuilt image.

This guide covers two paths:

1. **[Run with Docker Compose](#option-a-run-with-docker-compose)** — the
   recommended way to run Monohull on a server.
2. **[Build and run from source](#option-b-build-and-run-from-source)** — for
   developing Monohull itself.

---

## Prerequisites

| Requirement | Notes |
|---|---|
| **Docker Engine** | Monohull manages environments by talking to the Docker daemon. It needs access to the Docker socket (`/var/run/docker.sock`). |
| **MariaDB 11** | Stores Monohull's own state (environments, build history, actions, pipelines). Schema is created/updated automatically by Flyway on first boot. |
| **A host directory for shared volumes** | Used for per-environment bind mounts and PR-build workspaces. The same path must resolve identically inside Monohull and inside the containers it launches (default `/docker/volumefs`). |
| **Access to your Maximo images** | Monohull launches the database, application-server, and admin images you configure. Ensure the host can pull them from your registry. |

For **building** Monohull from source you additionally need:

| Requirement | Notes |
|---|---|
| **JDK 17** | The build targets Java 17. |
| **Maven 3.9+** | Builds the Java app and drives the frontend build. |
| **Node.js 18+ / npm** | Builds the React frontend (invoked by Maven; also used for the frontend dev server). |

---

## Option A — Run with Docker Compose

This runs Monohull and its MariaDB together, with Monohull bound to the host's Docker
socket so it can manage environments.

### 1. Create a `docker-compose.yml`

```yaml
services:
  monohull-db:
    image: mariadb:11
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: made     # internal schema name is historical — keep as-is
      MYSQL_USER: made
      MYSQL_PASSWORD: made     # change these defaults for anything non-local
    volumes:
      - monohull-db-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "healthcheck.sh", "--connect", "--innodb_initialized"]
      interval: 10s
      timeout: 5s
      retries: 15
      start_period: 30s

  monohull-app:
    image: ghcr.io/ipqii/monohull:1            # prebuilt public image (or your own build)
    pull_policy: always
    ports:
      - "8806:8080"                          # Monohull UI on host port 8806
    environment:
      SPRING_DATASOURCE_URL: jdbc:mariadb://monohull-db:3306/made
      SPRING_DATASOURCE_USERNAME: made
      SPRING_DATASOURCE_PASSWORD: made
      APP_DOCKER_HOST: unix:///var/run/docker.sock
      APP_DOCKER_HOST_HOME: /home/youruser
      MONOHULL_ADMIN_USERNAME: admin
      MONOHULL_ADMIN_PASSWORD: change-me-on-first-login
      # Optional — leave unset for a LAN-only install:
      # MONOHULL_MAXIMO_DOMAIN: maximo.example.com
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
      - /docker/volumefs:/docker/volumefs
    depends_on:
      monohull-db:
        condition: service_healthy

volumes:
  monohull-db-data:
```

### 2. Start it

```bash
docker compose up -d
```

MariaDB comes up first; Monohull waits for it, then runs its Flyway migrations
automatically on first boot.

### 3. Open the UI

Browse to `http://<host>:8806/` and log in with the `MONOHULL_ADMIN_USERNAME` /
`MONOHULL_ADMIN_PASSWORD` you set. (The admin account is seeded on first boot only,
while the user table is empty.)

### 4. Configure your first image set

In the UI, go to **Image Config** and define the database, application-server,
and admin images for the kind of Maximo environment you want to create. Then
create an environment from the dashboard — Monohull will provision the containers and
run the build pipeline, streaming the log live.

---

## Option B — Build and run from source

### 1. Build

The build compiles the Java app **and** the React frontend into a single JAR.

On Windows PowerShell, point the build at a JDK 17 first:

```powershell
$env:JAVA_HOME="C:\path\to\jdk-17"; $env:Path="$env:JAVA_HOME\bin;$env:Path"
```

Then:

```bash
mvn clean package
```

This produces `target/monohull.jar` (the name is version-independent).

### 2. Provide a database

Run a MariaDB and create the `made` database/user (matching whatever you put in
config below). For example:

```bash
docker run -d --name monohull-db \
  -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=made \
  -e MYSQL_USER=made -e MYSQL_PASSWORD=made \
  -p 3306:3306 mariadb:11
```

### 3. Run

The defaults in `application.yml` already point at
`jdbc:mariadb://localhost:3306/made` with user/password `made`/`made`, so:

```bash
java -jar target/monohull.jar
```

Monohull listens on port **8080** by default — open `http://localhost:8080/`.

> **Note:** the machine running Monohull must be able to reach a Docker daemon (via
> `APP_DOCKER_HOST` / `app.docker.host`) to create environments.

### Frontend dev server (optional)

For UI development with hot reload, run the Vite dev server, which proxies API
calls to a Monohull instance on `localhost:8080`:

```bash
cd frontend
npm install
npm run dev
```

---

## Configuration reference

Monohull reads configuration from `application.yml`, overridable by environment
variables. The most commonly set variables:

| Environment variable | Default | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:mariadb://localhost:3306/made` | Monohull's own database. |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `made` / `made` | Database credentials. |
| `APP_DOCKER_HOST` | `unix:///var/run/docker.sock` | How Monohull reaches the Docker daemon it manages. |
| `APP_DOCKER_HOST_HOME` | — | Home directory on the Docker host, used to resolve host-side paths for bind mounts. |
| `MONOHULL_ADMIN_USERNAME` | `admin` | Initial admin login, seeded on first boot when no users exist. |
| `MONOHULL_ADMIN_PASSWORD` | — | Initial admin password. **Set this** — if unset, the account is seeded with the default password `changeme` and a loud warning in the log. |
| `MONOHULL_MAXIMO_DOMAIN` | *(empty)* | Base domain for per-environment public Maximo URLs (e.g. `maximo.example.com`). Empty ⇒ LAN-only, no public routing. |
| `MONOHULL_PUBLIC_BASE_URL` | *(empty)* | Public URL Monohull itself is reachable at (e.g. `https://monohull.example.com`), used to render the PR-build webhook URL. |
| `MONOHULL_NETWORK_SUBNET_POOL` | `10.100.0.0/16` | `/16` block Monohull carves per-environment `/24` networks from. Must not overlap the host LAN or other cluster networks. |
| `MONOHULL_PR_BUILDS_MAX_CONCURRENT` | `2` | Max concurrent per-pull-request builds; extra builds queue. |
| `MONOHULL_PR_BUILDS_WORKSPACE_ROOT` | `/docker/volumefs/pr-builds` | Root for per-PR checkouts. Must be on a host-mounted volume so the path resolves for bind mounts. |
| `MONOHULL_API_KEY` | *(empty)* | Static bearer key for service-to-service read access. Empty disables API-key auth. |

### Public environment routing (optional)

If you set `MONOHULL_MAXIMO_DOMAIN`, Monohull labels each environment's application
container for a host reverse proxy (Traefik) and publishes it at
`https://<environment-name>.<domain>/maximo`. This requires a reverse proxy on
the host and DNS/TLS for the wildcard domain. Leave `MONOHULL_MAXIMO_DOMAIN` empty for
a LAN-only install, where environments are reached directly on their published
host ports.

---

## Verifying the install

1. **App is up:** `curl -s -o /dev/null -w '%{http_code}' http://<host>:8806/`
   returns `200`.
2. **Database is connected:** Monohull starts without Flyway/JPA errors in the log
   (schema validation is enabled).
3. **Docker access works:** create a trivial environment from the dashboard and
   confirm its containers appear (both in the Monohull UI and in `docker ps`).

---

## Upgrading

Pull/point to the new Monohull image (Compose) or rebuild the JAR (source) and
restart. Flyway applies any new schema migrations automatically on boot; your
existing environments, history, and configuration are preserved in MariaDB.

> **Browser cache note:** the UI is a PWA with an app-shell cache. After an
> upgrade, the first page load may serve the previous shell — reload once and the
> new version activates.
