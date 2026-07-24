# Your first environment

Creating an environment takes two things: a **template** to build from, and the
**New Build** dialog.

## 1. Define a template (once per project)

Go to **Environments → New Environment** and fill in at least the required
fields:

- **Client** and **Project** — labels that group and name your environments.
- **Maximo Version** — `7.6.1.1/.2/.3` or `MAS`.
- **App / DB / ADM Image** — the three registry image references the containers
  are created from (e.g. `registry.example.com/maximo/app:7.6.1.2`).
- **Database Vendor** — DB2 or Oracle.

Optionally set static host ports, storage paths, a workspace path, container
"extras", and the **pipeline** that builds environments of this template. Save.
(Full field reference is in
[Templates, profiles & sharing](templates-profiles.md).)

## 2. Create the environment

From the **Dashboard**, click **New Build**:

1. **Image Configuration** — pick the template you just made (grouped by client).
2. **Environment Name** — auto-generated as
   `monohull-<client>-<project>-<n>`; edit if you like.
3. **Use static ports** — off by default (Monohull picks free host ports). Turn on to
   use the fixed ports from the template.
4. **Include mock receiver** / **Include SMTP server** — optional add-on
   containers for integration testing (see
   [Integration-testing helpers](integration-helpers.md)).

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
[Accessing Maximo & the database](#accessing-maximo-the-database).

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
