# Troubleshooting

**A build failed (ERROR status).** Open the environment → **Logs** tab to read the
build output, and the **Pipeline** tab to see which step failed and its exit code.
Fix the cause (bad image tag, DB issue, etc.) and use **Re-run Pipeline**.

**The build stops with "has no Maximo schema".** The DB container started and
reported ready, but its database is empty, so Monohull stopped before any pipeline
action could fail against it. The usual cause is a DB image that restores a backup
only when its entrypoint is given an argument, with no
[**DB Command**](templates-profiles.md#db-command) set — the build log's `[hint]`
lines say so when the image's own startup output shows it. Set DB Command on the
template (or the environment's **Configuration** tab), remove the environment's DB
volume so the entrypoint re-runs its restore, and rebuild. If your schema is meant
to be created by a pipeline action instead, set
`monohull.build.verify-db-schema=false`.

**A DB action fails with `SQL0204N ... is an undefined name` (DB2 exit 4).** Same
root cause as above on an environment built before that check existed: the table
the action wants isn't there because the database was never populated.

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

**Can't log in after install.** The admin login is seeded on **first boot only**,
from `MONOHULL_ADMIN_USERNAME` / `MONOHULL_ADMIN_PASSWORD`. If the app first booted
without a password set, the account was seeded with a fallback: `changeme` (bare
app) or `admin` (the dev docker-compose). On Windows PowerShell, note that
`MONOHULL_ADMIN_PASSWORD='x' docker compose up` is bash-only syntax — set the
variable with `$env:MONOHULL_ADMIN_PASSWORD='x'` first. To re-seed, wipe the
Monohull database volume (`docker compose down -v`) and start again with the
variable set.

**The UI looks stale after an upgrade.** Monohull's UI is a PWA with an app-shell
cache — reload once after a new version deploys and the new shell activates.
