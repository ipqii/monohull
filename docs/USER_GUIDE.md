# Monohull User & Developer Guide

This guide covers day-to-day use of **Monohull**, the automated development
environment manager for IBM Maximo®: how to stand up Maximo environments, run
actions and pipelines against them, wire up per-pull-request builds, and use
the integration-testing helpers.

It assumes Monohull is already installed and reachable in a browser. For getting a
Monohull instance running, see **[INSTALL.md](INSTALL.md)**; for the "why", see the
**[overview](website/overview.md)**.

---

## Contents

1. [Core concepts](#core-concepts)
2. [Signing in & getting around](#signing-in--getting-around)
3. [Quick start: your first environment](#quick-start-your-first-environment)
4. [The Dashboard](#the-dashboard)
5. [Environment detail](#environment-detail)
6. [Accessing Maximo & the database](#accessing-maximo--the-database)
7. [Environments (image templates)](#environments-image-templates)
8. [Actions](#actions)
9. [Pipelines](#pipelines)
10. [Per-pull-request builds](#per-pull-request-builds)
11. [Registry credentials](#registry-credentials)
12. [Integration-testing helpers](#integration-testing-helpers)
13. [Import / export & sharing config](#import--export--sharing-config)
14. [Troubleshooting](#troubleshooting)

---

## Core concepts

A few terms recur throughout Monohull. Understanding them makes everything else
click into place.

| Concept | What it is |
|---|---|
| **Environment** | A running (or stopped) Maximo instance Monohull provisioned for you: a set of Docker containers on an isolated network, plus its build history and config. This is the thing you *use*. |
| **Container role** | Every environment has containers with fixed roles: **DB** (DB2 or Oracle), **APP** (the WebSphere/Liberty application server), and **ADM** (the admin/build container). Optional add-on roles are **MOCK** and **SMTP**. |
| **Environment template** (a.k.a. *image config*) | A reusable recipe — which DB/APP/ADM images to use, which database vendor, ports, volumes, and which build pipeline — that new environments are stamped out from. In the UI these live under **Environments**. |
| **Action** | A single command run against a container role — e.g. "Build EAR" on ADM, "Restart WebSphere" on APP. Actions are either **built-in** (shipped with Monohull) or **custom** (yours). |
| **Pipeline** | An ordered sequence of actions. The build pipeline is what turns a set of empty containers into a working Maximo. |
| **Connected repository** | A git repo Monohull watches so it can build every pull request — as a build check, or as a full throwaway environment. |

### The life of an environment

```
 create ──▶ CREATING ──▶ BUILDING ──▶ CONFIGURING ──▶ RUNNING
                                                          │
                                          stop ◀──────────┤──────────▶ remove
                                           │              │
                                        STOPPED ──▶ start ┘
```

When you create an environment, Monohull pulls the configured images, creates a
dedicated Docker network, launches the DB/APP/ADM containers, and then runs the
**build pipeline** (build the EAR, run UpdateDB, apply fixes, start the app
server, reset credentials). The whole thing streams to the browser live. When it
reaches `RUNNING`, the environment is ready to log into.

You can **stop** an environment (containers stopped but preserved), **start** it
again, or **remove** it (containers stopped and deleted). Its build history and
config stay in Monohull's database until you remove it.

---

## Signing in & getting around

Monohull requires a login. On first boot an admin account is seeded from the
`MONOHULL_ADMIN_USERNAME` / `MONOHULL_ADMIN_PASSWORD` the installer set. Enter those on
the **Sign in** screen.

The left **sidebar** is the primary navigation:

| Item | What it's for |
|---|---|
| **Dashboard** | Your running/stopped environments. The home base. |
| **Environments** | Environment **templates** (image configs) — the recipes new environments are built from. |
| **Actions** | Built-in and custom actions (commands you can run on containers). |
| **Pipelines** | Ordered sequences of actions. |
| **Repositories** | Connect git repos for per-PR builds. |
| **Registry** | Credentials for pulling images from a private Docker registry. |

The user panel at the bottom of the sidebar shows who you're signed in as and a
**sign-out** button. On a phone the sidebar collapses behind the menu icon in the
top bar.

---

## Quick start: your first environment

Creating an environment takes two things: a **template** to build from, and the
**New Build** dialog.

### 1. Define a template (once per project)

Go to **Environments → New Environment** and fill in at least the required
fields:

- **Client** and **Project** — labels that group and name your environments.
- **Maximo Version** — `7.6.1.1/.2/.3` or `MAS`.
- **App / DB / ADM Image** — the three registry image references the containers
  are created from (e.g. `registry.example.com/maximo/app:7.6.1.2`).
- **Database Vendor** — DB2 or Oracle.

Optionally set static host ports, storage paths, a workspace path, container
"extras", and the **pipeline** that builds environments of this template. Save.
(Full field reference is in [Environments](#environments-image-templates).)

### 2. Create the environment

From the **Dashboard**, click **New Build**:

1. **Image Configuration** — pick the template you just made (grouped by client).
2. **Environment Name** — auto-generated as
   `monohull-<client>-<project>-<n>`; edit if you like.
3. **Use static ports** — off by default (Monohull picks free host ports). Turn on to
   use the fixed ports from the template.
4. **Include mock receiver** / **Include SMTP server** — optional add-on
   containers for integration testing (see
   [Integration-testing helpers](#integration-testing-helpers)).

Click **Create**. The card appears on the dashboard and moves through
`CREATING → BUILDING → CONFIGURING → RUNNING` as the pipeline runs. Click the
card at any point to watch the build log live.

> **Tip — YAML mode:** the New Build dialog has a **Form / YAML** toggle. Switch
> to YAML to edit the request as text, and use **Export** to download it as a
> `.environment.yaml` you can keep in version control or reuse.

---

## The Dashboard

The dashboard lists every environment as a card showing:

- the **name** and a live **status badge** (RUNNING/BUILDING/STOPPED/ERROR…),
- the **Maximo version** and **DB vendor**,
- a mini row of **containers** with a green dot per running container.

Cards refresh automatically every few seconds. A colored left border mirrors the
status at a glance.

Card actions (hover, bottom-right):

- **Details** (arrow) — open the environment detail page.
- **Stop** — appears when RUNNING; stops all containers.
- **Start** — appears when STOPPED; starts them again.
- **Remove** (trash) — stops and deletes all containers. Asks for confirmation.

Click anywhere else on a card to open its detail page.

---

## Environment detail

Clicking an environment opens a page with a header (name, status, Maximo version,
DB vendor, build ID) plus **Stop All / Start All / Remove** buttons, and five
tabs.

### Containers tab

When the environment is RUNNING, the top of this tab shows an **Access** card and
(if enabled) a **Test Addons** card — see
[Accessing Maximo](#accessing-maximo--the-database).

Below that, one card per container (DB, APP, ADM, and any MOCK/SMTP), each with:

- the container **name** and **image** (both copy-to-clipboard),
- live **state** and start time,
- published **ports**,
- an **Actions** dropdown — runs any action whose target role matches this
  container; output streams in a dialog,
- **Restart**, **Stop/Start**, and **Logs** — raw `docker logs` in a dialog, with
  a **tail lines** input (default 500) and a refresh button.

### Pipeline tab

Shows the build pipeline as a vertical stepper — each action, its target role,
status, and exit code, with start/finish times. While a build is in flight the
running step spins and the view auto-refreshes.

Use **Re-run Pipeline** to run the whole build sequence again (available when the
environment is RUNNING, STOPPED, or ERROR). Handy after changing an image tag or
recovering from a failed build.

### Logs tab

The full build log in a terminal-style **LogViewer** — line numbers, error/warn
highlighting, and controls to **pause/resume auto-scroll** (follow mode), **copy**,
and **download** the log. While a build is active the log **streams live** over
Server-Sent Events; the view auto-scrolls as lines arrive until you scroll up,
and re-engages when you scroll back to the bottom. For finished builds it shows
history; very long logs load the tail first with a **Load older lines** button so
the tab never chokes on a multi-million-line build.

### Configuration tab

Per-environment overrides (distinct from the template):

- **Host Volume Path**, **DB Volume Name**.
- **HTTP / HTTPS / DB Port**.
- **Database Password** — passed to the DB container as `MAXIMO_DB_PASSWORD`; used
  by in-container scripts (e.g. database restore).
- **Pipeline (override)** — run a different pipeline than the template's default.
- **DB / APP / ADM container extras** — extra environment variables and bind
  mounts for each container.

Fields save on blur (when you click away).

### History tab

Every **action execution** against this environment — action name, status, exit
code, start/finish. Expand a row to read that execution's captured log output.

---

## Accessing Maximo & the database

When an environment is RUNNING, the **Access** card on the Containers tab gives
you everything needed to log in:

**Maximo UI**
- **Open Maximo** button and a copyable **URL**. Monohull builds the URL from the
  hostname you used to reach Monohull, so links work whether Monohull is local
  (`localhost`) or on a remote docker host. If the deployment advertises a public
  domain, that HTTPS URL is primary and the `host:port` LAN URL is shown as a
  fallback.
- **Login** — `maxadmin / maxadmin` by default. The **key** icon opens **Change
  Maximo password**, which re-encrypts a new password (using Maximo's own cipher)
  and writes it to `MAXUSER` on the ADM container. The environment must be
  running.

**Database**
- A ready-made **JDBC URL** (DB2 or Oracle form, per vendor), **host:port**,
  **user** (`maximo`), and **password** (reveal/copy). Point a SQL client
  straight at it.

---

## Environments (image templates)

The **Environments** page lists your templates. Each card shows the client /
project, Maximo version, DB vendor, the three image tags, any volume/workspace
paths, and the linked pipeline. Per card: **Edit**, **Export** (bundle), and
**Delete**.

**New Environment** / **Edit** opens a sectioned form (the left nav jumps between
sections):

| Section | Fields |
|---|---|
| **Identity** | Client, Project, Maximo Version. |
| **Images** | App / DB / ADM image references (required). |
| **Database** | Vendor (DB2/Oracle), Database Name (default `maxdb76`), DB Container Port (internal listener; defaults 50000 DB2 / 1521 Oracle). |
| **Storage & Paths** | Host Volume Path (base for per-env `config/` and `logs/`), DB Volume Name, Workspace Path (a local git repo mounted at `/workspace/<name>` in APP and ADM). |
| **Host Ports** | Optional static HTTP/HTTPS/DB and Mock/SMTP/Mailpit-UI ports, used when an environment opts into **static ports**. Leave blank to force dynamic allocation. |
| **Pipeline** | The build pipeline run when an environment of this template is created or rebuilt. |
| **DB / APP / ADM Extras** | Extra env vars and bind mounts applied to each container role. |

Required fields (Client, Project, Maximo Version, all three images) are flagged
in the sticky save bar until filled. The form warns before discarding unsaved
changes.

---

## Actions

An **action** is one command targeting one container role. The **Actions** page
splits them into **Built-in** (shipped with Monohull, resynced from configuration at
every restart) and **Custom** (yours).

Each card shows the target role and badges for **Host** / **Builder** execution,
**Built-in**, and **Auto** (part of the auto-build pipeline), plus timeout, key,
and scope. Per card: **Edit**, **Clone**, **Export**, and (custom only)
**Delete**.

### Built-in actions

These implement the standard Maximo build/configure sequence. In order, they are:

| Action | Role | What it does |
|---|---|---|
| **Run UpdateDB Pre-Processor** | ADM | Runs Maximo's UpdateDB pre-processor. |
| **Build EAR** | ADM | Builds the Maximo EAR + Liberty bundle and publishes it to the bind-mounted config dir. |
| **Run Maximo UpdateDB** | ADM | Runs UpdateDB (can take 30–60+ min on industry solutions). |
| **MAS pre-updatedb DB fixes** | DB | Clears stale `userdefined` flags that would otherwise break UpdateDB on a vanilla Service Provider DB. |
| **MAS post-updatedb DB fixes** | DB | SQL fixes so the restored DB accepts MAXADMIN auth and OSLC calls. |
| **Set MAXADMIN password** | ADM | Encrypts `maxadmin` with Maximo's cipher and writes it to `MAXUSER` so basic-auth logins work. |
| **Swap server.xml to dev variant** | ADM | Promotes the dev `server.xml` so APP boots with basic auth instead of OIDC; injects keystore + session-cookie config for MAS shells. |
| **Start APP Container** | APP | Pipeline marker — starts APP so later ADM actions can reach Maximo over HTTP. |
| **Restart WebSphere** | APP | Restarts the application server (a Docker-level restart). |
| **Build Package** | BUILDER | Builds a product-addon zip from the workspace in a clean, ephemeral ant + JDK 8 container. |
| **Deploy Package** | ADM | Unzips the built addon into `/opt/IBM/SMP/maximo` on the ADM container. |

> Built-ins can be edited for live experiments, but they **resync from
> configuration at every Monohull restart** — lasting changes belong in the server
> config, not the UI.

### Creating a custom action

**Add Action** (or **Clone** an existing one) opens a sectioned editor:

- **Definition** — Name and Description.
- **Execution**
  - **Target Role** — DB / APP / ADM / BUILDER.
  - **Execution Type**:
    - **Container Exec** — run the command inside the container (via `/bin/bash -c`).
    - **Host** — a Docker-level operation on the container; supported commands are
      `restart`, `stop`, `start`.
    - **Builder** — run in an ephemeral ant + JDK 8 container with the workspace at
      `$MADE_WORKSPACE`; leave the artifact at `/out/package.zip` and Monohull stages
      it to `ADM:/tmp/made-package/`.
  - **Command** — the script/command.
  - **Working Directory**, and **Run as user** (runs as `su - <user> -c '…'`;
    blank = container default).
- **Behaviour**
  - **Timeout (seconds)** — default 300.
  - **Allowed Exit Codes** — comma-separated codes treated as success (0 is always
    allowed). Useful for tools that exit non-zero on warnings.
  - **Include in Auto-Build Pipeline** — auto-run as part of the standard build.
- **Scope** — where the action is offered: **Global**, for a single **image
  config**, or for a single **environment**.

There's a **Form / YAML** toggle for editing actions as text.

### Running an action

Two ways:

1. **From a container card** (Environment detail → Containers) — the **Actions**
   dropdown lists actions whose target role matches that container. Output streams
   in a dialog.
2. **As part of a pipeline** — see below.

---

## Pipelines

A **pipeline** is an ordered list of actions. The **Pipelines** page is a
drag-and-drop builder:

- **Load Pipeline** — pick an existing one, or **New**.
- **Pipeline Name**, optional **Description**.
- **Scope** — **Global** (any environment) or bound to a specific environment.
  Scope controls which actions are available: a global pipeline sees global +
  image-config actions; an environment-scoped pipeline also sees that
  environment's actions.
- **Steps** — drag actions from the **Available Actions** panel on the right into
  the drop area; reorder by dragging; remove with the trash icon. Each step shows
  its number and target role.

**Save** persists it; **Export** downloads a `.pipeline.yaml`; **Delete** removes
it. A **Form / YAML** toggle lets you author the whole pipeline as text (a list
of `actionKey`s).

**Running a pipeline** happens against an environment: on the environment's
**Pipeline** tab, **Re-run Pipeline** executes the environment's pipeline (its
template default, or a Configuration-tab override). Steps run sequentially with
live status.

---

## Per-pull-request builds

Monohull can build every pull request on a connected repo — as a pass/fail build
check, or as a full throwaway Maximo environment reviewers can click into.

### Connect a repository

**Repositories → Connect Repository**:

| Field | Notes |
|---|---|
| **Name** | A label, e.g. `maximo-config`. |
| **Provider** | GitHub, Bitbucket, or GitLab. |
| **Default branch** | e.g. `main`. |
| **Auth method** | **HTTPS (token)** or **SSH (deploy key)**. |
| **Repository URL** | The clone URL (HTTPS or SSH form, matching the auth method). |
| **Full name (owner/repo)** | Used to match incoming webhook payloads, e.g. `acme/maximo-config`. |
| **Build mode** | **Build check only** (clone + build + report, nothing kept) or **Build + ephemeral env** (also deploy a full Maximo env, removed on PR close). |
| **Image config** | The template whose build pipeline compiles the PR source. |
| **Credentials** | HTTPS: clone username + token (PAT/app password). SSH: paste a PEM deploy key (stored write-only) and optional passphrase. |
| **Max concurrent** | Per-repo build concurrency. |
| **Enabled** | Toggle to pause building without disconnecting. |

### Point the git provider at Monohull

Each connected repo card shows a **Webhook URL** and **Webhook secret** (both
copyable; the secret can be revealed). Add a webhook in your provider and trigger
it on **pull-request created / updated** events:

- **GitHub** — Settings → Webhooks → Add webhook. Payload URL = the URL,
  Content type `application/json`, Secret = the secret, event = *Pull requests*.
- **GitLab** — Settings → Webhooks. URL = the URL, Secret token = the secret,
  enable *Merge request events*.
- **Bitbucket** — Repository settings → Webhooks → Add webhook. URL = the URL
  (the secret is embedded in it), triggers = *Pull Request Created / Updated*.

The card repeats the exact steps for the provider you chose.

### Watch the builds

The **Builds** button on a repo card opens its PR-build history. Global
concurrency across all repos is capped by the server's `MONOHULL_PR_BUILDS_MAX_CONCURRENT`
setting; extra builds queue.

---

## Registry credentials

If your images live in a private registry, go to **Registry** and add the
credentials Monohull uses to pull them:

- **Registry URL** — hostname (and optional port), e.g.
  `registry.example.com:5000`. Credentials are only sent for images whose host
  matches this value.
- **Username** and **Password** (on update, leave the password blank to keep the
  existing one).
- **Description** — optional.

**Save**/**Update** to store, **Delete** to remove. A status chip shows whether a
credential is configured.

---

## Integration-testing helpers

When creating an environment you can attach two helper containers so integrations
and email can be exercised without touching real external systems. When present,
they appear in a **Test Addons** card on the Containers tab.

### Mock receiver

Captures outbound HTTP integrations. From inside Maximo, point publish channels /
endpoints at **`http://mock:8085`** (the container also answers to the alias
`mock-receiver`, so existing endpoint rows pointing at `http://mock-receiver:8085`
resolve too). The captured requests are browsable at the **Mock UI**
(`http://<host>:<mockPort>/__mock/`), which also has a rules editor for scripting
templated responses.

### SMTP capture (Mailpit)

Captures outbound email. Point Maximo's SMTP at **`smtp:1025`**; read everything
it sends in the **Inbox UI** (Mailpit, `http://<host>:<mailpitUiPort>`). No mail
ever leaves the host.

Enable either from the **New Build** dialog (**Include mock receiver** / **Include
SMTP server**). If you use static ports, set the corresponding Mock/SMTP/Mailpit-UI
ports on the template first.

---

## Import / export & sharing config

Monohull config is portable as YAML, so you can version it or move it between
instances:

- **Environment (New Build) dialog** — **Export** a `.environment.yaml`.
- **Actions** — **Export** one action, or **Export All**; **Clone** to duplicate.
- **Pipelines** — **Export** a `.pipeline.yaml`.
- **Environment templates** — **Export** an **environment bundle**: the image
  config *plus* its linked pipeline *plus* that pipeline's custom actions, in one
  file.
- **Import Bundle** (Environments page) — load a bundle exported from another Monohull
  instance in a single transaction. Built-in actions resolve by key on the target
  instance; there's an **overwrite** switch for conflicts, and the importer
  reports exactly what it created or updated.

### One-click profiles

A template with **Launch Defaults** set (Environments → edit → Launch Defaults:
profile description, static ports, mock/SMTP add-ons) is a **profile** — launchable
without any dialog input. On the **Dashboard**, **Launch Profile** lists every
profile with its description; one click provisions a new environment (name
auto-generated) and starts the build, landing you on its live log.

Launch defaults travel inside the exported bundle, so a teammate can take your
`.bundle.yaml`, click **Launch Profile → Import .bundle.yaml and launch** on a
fresh Monohull, and go from empty install to a building environment in one step.
If the profile already exists on their instance, the import is skipped and the
existing copy launches (an **overwrite** switch forces the update instead). Note
the profile must have a **pipeline** linked — launching is refused otherwise,
since the environment would never build.

---

## Troubleshooting

**A build failed (ERROR status).** Open the environment → **Logs** tab to read the
build output, and the **Pipeline** tab to see which step failed and its exit code.
Fix the cause (bad image tag, DB issue, etc.) and use **Re-run Pipeline**.

**The environment is RUNNING but Maximo won't load.** The app server can take
several minutes to bind its ports after the containers start. Give it time, then
check the APP container's **Logs**. A **Restart WebSphere** action or a container
**Restart** can clear a wedged startup.

**Static-ports warning when creating an environment.** The template is missing one
or more of the required static port values (HTTP/HTTPS/DB, or Mock/SMTP/Mailpit-UI
if those addons are enabled). Set them on the template's **Host Ports** section,
or turn **static ports** off to let Monohull allocate them.

**No image configs / "add one first".** You need at least one **Environment**
template before you can create an environment. Create one under **Environments**.

**Image pulls fail.** Add the private-registry credentials under **Registry**, and
confirm the host can reach that registry.

**The UI looks stale after an upgrade.** Monohull's UI is a PWA with an app-shell
cache — reload once after a new version deploys and the new shell activates.
