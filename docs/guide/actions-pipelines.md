# Actions & pipelines

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
| **Swap web.xml to dev variant** | ADM | Uses Maximo's `web-dev.xml` deployment descriptors so Liberty leaves authentication to Maximo. Without it the EAR carries security-constraints on `/ui/*` and `/oslc/*` with BASIC auth, and the browser shows its own credential prompt instead of Maximo's login page. Runs before **Build EAR**, since the descriptors are compiled into the EAR. |
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
  the drop area; reorder by dragging; remove with the trash icon. While you drag,
  the steps below the pointer move down to open a gap where the action will land:
  hover a step to insert before it, or the space kept open below the list to add to
  the end.
  Each step shows its number and target role, and an **↗** button that opens that
  action's definition in a new tab — so you can read or edit the command without losing the
  pipeline you're building. The same button is on every card in the Available
  Actions panel.

**Save** persists it; **Export** downloads a `.pipeline.yaml`; **Delete** removes
it. A **Form / YAML** toggle lets you author the whole pipeline as text (a list
of `actionKey`s).

**Running a pipeline** happens against an environment: on the environment's
**Pipeline** tab, **Re-run Pipeline** executes the environment's pipeline (its
template default, or a Configuration-tab override). Steps run sequentially with
live status, and each one carries the same **↗** link to its action definition —
the quickest route from a failed step to the command that failed.
