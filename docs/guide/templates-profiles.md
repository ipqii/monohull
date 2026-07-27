# Templates, profiles & sharing

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
| **Database** | Vendor (DB2/Oracle), Database Name (default `maxdb76`), DB Container Port (internal listener; defaults 50000 DB2 / 1521 Oracle), [DB Command](#db-command). |
| **Storage & Paths** | Host Volume Path (base for per-env `config/` and `logs/`), DB Volume Name, [DB Volume Target](#db-volume-target), Workspace Path (a local git repo mounted at `/workspace/<name>` in APP and ADM). |
| **Host Ports** | Optional static HTTP/HTTPS/DB and Mock/SMTP/Mailpit-UI ports, used when an environment opts into **static ports**. Leave blank to force dynamic allocation. |
| **Pipeline** | The build pipeline run when an environment of this template is created or rebuilt. |
| **DB / APP / ADM Extras** | Extra env vars and bind mounts applied to each container role. |

Required fields (Client, Project, Maximo Version, all three images) are flagged
in the sticky save bar until filled. The form warns before discarding unsaved
changes.

### DB Command

Some Maximo database images do not ship the database inside the image. Their
entrypoint instead branches on its **first argument** to decide what to do at
startup — typically restoring a backup when passed something like `restore`, and
otherwise creating an empty database.

**DB Command** is that argument list. Monohull hands it to the DB container's
entrypoint verbatim; quoting works as you'd expect, so
`restore --file "my backup.tar.gz"` arrives as three arguments. Leave it blank
for images that already contain the database — the container then runs the
image's own `CMD`, which is what Monohull has always done.

Getting this wrong used to be quiet and expensive: the container starts fine,
reports itself ready, and the build only falls over several pipeline actions
later when the first action to touch a Maximo table hits an undefined-name error.
Monohull now checks for the Maximo schema as soon as the database reports ready
and fails the build there, naming this setting if the entrypoint's own logs point
at it. If your schema is created *by* a pipeline action rather than by the image,
turn that check off with `monohull.build.verify-db-schema=false`.

Whatever the image needs to *do* the restore is separate — usually credentials
for wherever the backup lives, supplied through **DB Extras** (env vars and bind
mounts) and the per-environment **Database Password**.

### DB Volume Target

Where the environment's database volume is mounted inside the DB container. It
has to be the path the image actually writes its data to. Blank uses
`/database` for DB2 and `/opt/oracle` for Oracle.

Getting this wrong doesn't error. The volume mounts over an empty directory, the
database is written to the container's writable layer instead, and everything
works — until the container is recreated, at which point the database is gone
and a restore has to run again from scratch. If a rebuild keeps re-restoring a
database you thought was persisted, this is why.

To find the right path, ask the running container where the database is. For
DB2:

```bash
docker exec <env>-db su - db2inst1 -c "db2 list db directory" | grep -i "local database directory"
```

Then check whether the volume is actually holding anything:

```bash
docker exec <env>-db du -sh /database
```

A few kilobytes means the volume is empty and the target is wrong.

### Database checks before the pipeline runs

Once the DB container reports ready, Monohull checks two things before any
pipeline action touches the database, and fails the build there if either is
wrong:

- **The listener** — that something is accepting connections on
  [DB Container Port](#environments-image-templates). This is the port UpdateDB,
  the APP container's JDBC URL, and the published host port all use. DB2 resolves
  its port through a service *name* in `SVCENAME`, so a mismatch is easy to miss;
  when the check fails on DB2 it reports the port the image is really on.
- **The schema** — that the Maximo tables exist. See
  [DB Command](#db-command) for why they might not.

Both are gated by `monohull.build.verify-db-schema`, which defaults to `true`.
Set it to `false` when a pipeline action is what creates the schema.

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

## One-click profiles

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
