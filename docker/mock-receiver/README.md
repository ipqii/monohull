# mock-receiver

Small HTTP server that stands in for outbound integration targets (Boomi,
Oracle PPM, custom REST endpoints) during integration testing. It accepts
any POST, buffers the body in memory, runs a configurable **rules engine** to
decide the response, and serves a web **UI** so you can inspect what Maximo
actually sent and reply with realistic, content-driven payloads.

Vendored into Monohull so it can spin the same container up alongside a
Maximo environment on opt-in.

## How Monohull uses it

When a user checks **Include mock receiver** on the Create Environment
form, Monohull provisions a container running this image and attaches it to
the environment's bridge network with alias `mock`. The Maximo app
container can then post to `http://mock:8085/...` regardless of the host
port allocation.

The image (`monohull/mock-receiver:latest`) is built on demand from this
directory the first time it's needed; subsequent envs reuse the cached
image. **Rebuild the image** (below) to pick up changes to the server, UI,
or default rules.

## Build manually

```bash
docker build -t monohull/mock-receiver:latest .
docker run --rm -p 8085:8085 monohull/mock-receiver:latest
```

## UI

Open `http://<host>:<port>/__mock/` for the management UI:

- **Messages** — every buffered request, newest first, with the matched rule,
  the returned status, collapsible headers, and a pretty-printed request/response
  body (XML indented, JSON re-formatted). Auto-refreshes every 2s.
- **Rules** — a rendered summary of the active rules plus a YAML editor.
  **Save** applies the ruleset to the running receiver and persists it to
  `RULES_FILE`; **Export** downloads it; **Import** loads a `.yaml` file; **Add
  rule** inserts a skeleton.

## Rules

Rules live in [`rules.yaml`](./rules.yaml) (baked into the image at
`/app/rules.yaml`, overridable via `RULES_FILE`). They are evaluated
top-to-bottom against each inbound request; the **first** rule whose `match`
predicates all hold wins and produces the response. With no match the receiver
replies `200 OK` (and still honours `?status=` / `X-Mock-Status` for that
default-path back-compat).

```yaml
rules:
  - name: ack-bill-batch
    match:
      method: POST                          # optional; defaults to any
      pathRegex: "/maximo/.*"               # optional, case-insensitive
      bodyRegex: "<BILLBATCHNUM[^>]*>\\s*(\\d+)"   # capture group 1 -> {{1}}
    respond:
      status: 200
      contentType: application/xml
      headers:
        X-Mock-Rule: ack-bill-batch
      body: |
        <ack batch="{{1}}" receivedAt="{{now}}">received</ack>
```

### match predicates

All predicates that are present must hold for the rule to match.

| key             | meaning                                                        |
|-----------------|----------------------------------------------------------------|
| `method`        | HTTP method (e.g. `POST`)                                       |
| `pathRegex`     | regex the request path must match (case-insensitive)           |
| `bodyContains`  | substring the body must contain                                |
| `bodyRegex`     | regex the body must match; capture groups feed `{{1}}..{{n}}`   |
| `bodyNotRegex`  | regex the body must **not** match                              |
| `headerContains`| map of header-name → substring the header value must contain   |

### respond

| key           | meaning                                                |
|---------------|--------------------------------------------------------|
| `status`      | HTTP status code (default `200`)                       |
| `contentType` | `Content-Type` header shortcut                         |
| `headers`     | map of extra response headers                          |
| `body`        | response body template (see templating below)          |

### body templating

- `{{1}}`, `{{2}}`, … — capture groups from `bodyRegex` (or `pathRegex` if no
  `bodyRegex` is set).
- `{{billBatch}}` — the `<BILLBATCHNUM>` value, if present.
- `{{path}}` — the request path.
- `{{now}}` — current ISO-8601 timestamp.

### Workflow

Edit rules live in the UI → **Save** (applies immediately, persists to the
container's `rules.yaml`) → **Export** and commit the file back to this repo so
the next image build bakes it in.

## Endpoints

Inbound (consumed by Maximo / integrations):

- `POST *` — receive any payload. The response is rules-driven; with no matching
  rule it returns `200 OK`, overridable via `?status=<code>` or
  `X-Mock-Status: <code>`.

Legacy query API (kept for existing SIT tests):

- `GET /messages` — list all buffered messages.
- `GET /messages?batch=<num>` — most recent message whose XML contains
  `<BILLBATCHNUM>num</BILLBATCHNUM>`.
- `GET /messages/latest` — most recent message regardless of content.
- `DELETE /messages` — clear the buffer (call between test runs).
- `GET /health` — liveness probe.

Management UI + API (namespaced under `/__mock`):

- `GET /__mock/` — web UI.
- `GET /__mock/api/messages` · `DELETE /__mock/api/messages` — list / clear.
- `GET /__mock/api/rules` — current ruleset (parsed JSON + raw YAML).
- `GET /__mock/api/rules/export` — ruleset as a YAML download.
- `PUT /__mock/api/rules` — replace the ruleset from a posted YAML body.
