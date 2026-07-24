# Per-pull-request builds

Monohull can build every pull request on a connected repo — as a pass/fail build
check, or as a full throwaway Maximo environment reviewers can click into.

## Connect a repository

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

## Point the git provider at Monohull

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

## Watch the builds

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
