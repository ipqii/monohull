# Monohull — development environments on demand, for IBM Maximo®

**Give every developer their own Maximo. On demand. In minutes.**

Monohull is a self-service platform that spins up complete, isolated IBM Maximo
environments on your own Docker infrastructure — one per developer, per feature,
or per pull request — from a single web dashboard.

[Get started in 10 minutes :material-arrow-right:](quickstart.md){ .md-button .md-button--primary }
[Install guide](install.md){ .md-button }

---

## The problem Monohull solves

Maximo development has always been bottlenecked by the environment itself.

- **One shared dev server, many developers.** Someone reloads the database and
  everyone else is blocked. A bad config change breaks the environment for the
  whole team. Work serialises around a resource nobody owns.
- **Environments take days to build by hand.** Standing up Maximo means a
  database, a WebSphere/Liberty application server, an admin/build container,
  UpdateDB, an EAR build, security fixes, and a dozen manual steps that only one
  or two people on the team actually know.
- **"It works on my environment."** Subtle drift between hand-built
  environments turns into bugs that can't be reproduced.

The result: developers wait, integration is risky, and onboarding a new joiner
means someone loses a day building them a Maximo.

## The Monohull way

Monohull turns a Maximo environment into something you **request, use, and throw
away** — like any other cloud resource.

- **Every developer gets their own instance.** No shared server, no contention.
  Break it, reset it, rebuild it — you affect nobody but yourself.
- **From click to running Maximo in minutes.** Monohull orchestrates the database,
  the application server, and the admin container, and runs the entire
  build-and-configure pipeline for you automatically.
- **Identical every time.** Environments are built from versioned images and a
  repeatable pipeline, so what you test is what your teammate tests.
- **Watch it build, live.** Full build and action logs stream to your browser in
  real time — no SSH-ing into containers to tail a file.

The effect on a team is simple: **work happens in parallel instead of in a
queue.** Five developers can each be mid-experiment on their own Maximo at the
same time, a reviewer can bring up the exact environment a pull request produces,
and a new hire is productive on day one instead of day three.

---

## Key features

### Self-service environments
Create a fully configured Maximo environment from the dashboard. Monohull provisions
and wires together the three containers every environment needs:

- a **database** (DB2 or Oracle),
- a **WebSphere / Liberty** application server, and
- an **administration / build** container.

Start, stop, restart, and remove environments — individually or per container —
with one click.

### Automated build pipelines
Each environment is stood up by an ordered pipeline: build the EAR and Liberty
bundle, run UpdateDB, apply the required security and configuration fixes, start
the application server, and reset credentials — all automatically, with each
step's output streamed live. Pipelines are fully configurable, and you can add
your own **custom actions** to run against any container role.

### Per-pull-request builds
Point Monohull at your Git repository and it can build a fresh environment for every
pull request, so reviewers validate changes against a real, running Maximo rather
than reading a diff. Concurrency is managed so builds queue instead of
overwhelming the host.

### Live log streaming
Build logs, action logs, and container logs stream to the browser over
Server-Sent Events with a proper terminal-style viewer — line numbering, error
highlighting, follow-on-tail, copy and download. No more `docker logs -f`.

### Package build & deploy
Build a Maximo product add-on from a workspace in a clean, ephemeral builder
container, then deploy it straight into a running environment — the same
build-and-ship loop you'd do by hand, automated.

### Integration testing helpers
Every environment can include a **mock receiver** for outbound integrations and a
built-in **SMTP capture** inbox, so you can exercise interfaces and email without
touching real external systems.

### Public URLs, when you want them
Optionally publish each environment at its own HTTPS subdomain through the host's
reverse proxy, so a developer can share a link to their running Maximo — or keep
everything LAN-only. Your choice.

### Works across Maximo generations
Because environments are defined by the images you configure, Monohull runs both
traditional **Maximo 7.6** and **MAS Manage 9.x** environments side by side on the
same host, including a password-management tool that works with each generation's
credential scheme.

---

## How it works

Monohull is a single **Spring Boot + React** application that talks to the Docker
daemon on your host. You give it a set of **image configurations** (which
database, which Maximo images, which ports); from there it creates an isolated
Docker network per environment, launches the containers, runs the configured
pipeline, and keeps live Docker status in sync with the dashboard.

Everything is persisted in a MariaDB database with schema migrations, so your
environments, build history, custom actions, and pipeline definitions survive
restarts.

```
   Developer's browser
          │
          ▼
   ┌─────────────┐        Docker daemon        ┌──────────────────────────┐
   │  Monohull   │ ─────────────────────────▶  │  env "acme-eam-1"         │
   │ (Spring +   │                             │   ├─ db   (DB2 / Oracle)  │
   │  React UI)  │                             │   ├─ app  (Liberty/WAS)   │
   └─────┬───────┘                             │   └─ adm  (build/admin)   │
         │                                     └──────────────────────────┘
         ▼                                     ┌──────────────────────────┐
     MariaDB                                   │  env "acme-mas-2" …       │
  (state, history)                             └──────────────────────────┘
```

---

## Who it's for

- **Maximo development teams** who are tired of fighting over a shared
  environment and want to move faster with less risk.
- **Consultancies and delivery teams** running multiple client Maximo builds who
  need many environments, isolated from one another, on shared hardware.
- **Anyone onboarding new Maximo developers** who wants "here's your own Maximo"
  to be a two-minute answer, not a two-day project.

> **Monohull** — Maximo environments on demand, so your developers build software
> instead of building environments.
