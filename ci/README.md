# Post-merge Maximo regression CI

Every merge to `main` triggers `.github/workflows/maximo-regression.yml` on the
self-hosted `dockerserver` runner. The run:

1. builds a **throwaway Monohull instance from the merged code**
   (`ci/docker-compose.ci.yml`, compose project `monohull-ci-<run id>`),
2. seeds it with a registry credential and the **vanilla Maximo profile**
   (`ci/seed.sh` + `ci/vanilla.bundle.template.yaml`) and launches an
   environment via `POST /api/profiles/launch`,
3. runs the Playwright **regression project** (`e2e/tests-regression/`): wait for
   `RUNNING`, verify Maximo serves (`/maximo/`, `oslc/whoami` with maxauth),
   assert the logs are clean (`BMXAA6472I` and `CWSID0108I` present, no
   `jms/maximo` JNDI errors — pins issue #7), tear the environment down through
   the API,
4. cleans up everything (`ci/janitor.sh` + `compose down -v`), even on failure.

A full run takes tens of minutes (real EAR build + updatedb).

## Isolation rules — why the CI instance can share the Docker host

Production Monohull instances run on the same daemon. The CI instance can never
collide with them because:

| Lever | CI value | Production |
|---|---|---|
| Dynamic env port range (`MONOHULL_PORTS_RANGE_START/END`) | 13000–13099 | 12000–12999 |
| Subnet pool (`MONOHULL_NETWORK_SUBNET_POOL`) | 10.200.0.0/16 | 10.100.0.0/16 |
| Env naming (`client`/`project` in the bundle) | `ci` / `r<run id>` — unique per run | real client names |
| Host volume root (`hostVolumePath` in the bundle) | `/docker/volumefs/monohull-ci` | production roots |
| `MONOHULL_MAXIMO_DOMAIN` | unset — never joins `made-public`/Traefik | set |
| Monohull's own port | 8899 | 8805 / 8807 |

The `client: ci` prefix is reserved for this pipeline — `ci/janitor.sh` deletes
anything matching `monohull-ci-*` / `made-monohull-ci-*` without asking.

## Runner host prerequisites (dockerserver)

- GitHub Actions runner registered to this repo with labels
  `self-hosted, dockerserver`, running as a user in the `docker` group.
- `~/monohull-ci/ci.env` (mode 600) with the private values that must not
  live in this public repo:

  ```
  CI_REGISTRY_URL=...
  CI_REGISTRY_USERNAME=...
  CI_REGISTRY_PASSWORD=...
  CI_VANILLA_APP_IMAGE=...
  CI_VANILLA_DB_IMAGE=...
  CI_VANILLA_ADM_IMAGE=...
  CI_AWS_DIR=...            # host dir with AWS creds for the DB image's restore
  ```

- `/docker/volumefs/monohull-ci` owned by the runner user.
- `python3`, `curl`, `envsubst` (gettext), `openssl` on the host — everything
  else runs in containers (the regression suite runs in the Playwright image
  with `--network host`).

## Security: self-hosted runner + public repo

The workflow triggers **only** on `push` to `main` and `workflow_dispatch` —
never `pull_request` — so code from forks can never execute on the runner. Keep
it that way, and keep "Require approval for all outside collaborators" enabled
in the repo's Actions settings.

## Operating it

- **Re-run:** Actions → "Maximo regression" → Run workflow.
- **A run died half-way:** run the workflow with `cleanup-only: true` — it just
  executes the janitor. (Every normal run also janitors before and after
  itself, so leftovers never survive two runs.)
- **Calibration:** the wait for `RUNNING` is `CI_WAIT_MINUTES` (default 75) and
  the job timeout is 120 min; tune after observing real run times.
