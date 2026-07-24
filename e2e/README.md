# Monohull end-to-end tests

Playwright suite that exercises the running Monohull app via UI flows and API calls.
The tests are scoped to **UI + light backend**: they don't spin up Maximo
containers — they only verify the configuration UI, the REST API round-trips,
and that data the UI saves matches what the API returns.

## Running with Docker (matches CI)

The `e2e` service is defined in the root `docker-compose.yml` under the `test`
profile, so it doesn't run on a normal `docker compose up`.

```bash
# Start the app (and DB) if not already running
docker compose up -d monohull-app

# Run all tests against the running app
docker compose --profile test run --rm e2e

# Run a single spec / pass extra args to playwright
docker compose --profile test run --rm e2e npx playwright test tests/dashboard.spec.ts
```

The HTML report and traces are surfaced via bind mounts to:
- `e2e/playwright-report/` — open `index.html` to browse failures
- `e2e/test-results/` — raw artefacts (screenshots, videos)

## Running locally (no Docker)

```bash
cd e2e
npm install
npx playwright install chromium     # one-time
BASE_URL=http://localhost:8080 npm test
```

Without `BASE_URL` the tests default to `http://localhost:8080` (the host port
mapped by `docker-compose.yml`).

## Adding tests

- Specs live under `tests/`.
- Use `helpers/api.ts` to seed/teardown data. Each test should create data with
  a unique `uniqueId()` suffix and clean up in `afterEach`.
- The `globalSetup` already waits for the app's API to be reachable, so tests
  can assume the app is up.

## Conventions

- Prefer accessible selectors (`getByRole`, `getByLabel`) over CSS class names —
  the MUI theme overrides change frequently.
- Mix UI and API in the same test only when verifying that a UI action lands
  correctly in the API — keep pure-API checks in their own tests for speed.
