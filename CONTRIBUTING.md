# Contributing to Monohull

Thanks for considering a contribution — issues, docs fixes, and code are all welcome.

## Ground rules

- **Open an issue first** for anything beyond a small fix, so we can agree on the
  approach before you invest time.
- By submitting a contribution you agree it is licensed under the repository's
  [Apache-2.0 license](LICENSE) (see §5 of the license — no separate CLA).
- Never include IBM software, IBM installation files, or content derived from IBM
  distributions (build scripts, server templates, jars) in a contribution. Monohull
  orchestrates such material from the *user's* installation; it must never ship it.
- No real hostnames, credentials, or customer references in code, comments, tests,
  or example values — use `example.com`-style placeholders.

## Dev setup

Prerequisites: JDK 17, Maven 3.9+, Docker. Node is downloaded by the build
(frontend-maven-plugin) — no local install needed.

```bash
mvn clean package                      # full build: React frontend + Spring Boot jar
docker compose up -d                   # run app + MariaDB
cd frontend && npm run dev             # frontend hot-reload against the running app
```

## Tests

- `mvn test` — backend unit tests.
- `docker compose --profile test run --rm e2e` — Playwright end-to-end suite against
  a live instance (UI flows + API). New features need e2e coverage of at least the
  happy path; bug fixes need a regression test where practical.

## Database migrations

Schema changes are Flyway migrations under `java/src/main/resources/db/migration/`.
**Never edit an existing migration** — checksums are validated on every start; always
add a new `V<n>__description.sql`.

## Pull requests

- Branch from `develop`; PRs target `develop`.
- Keep PRs focused — one concern per PR.
- Match the surrounding code style; comments explain *why*, not *what*.
- Reference the issue in the PR description.
