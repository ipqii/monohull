// Mock HTTP receiver for Maximo outbound integration channels.
//
// Accepts POST on any path, buffers the request body, runs a configurable rules
// engine to decide the response (status + headers + templated body), and serves a
// small web UI plus a query API so SIT tests can fetch what Maximo just sent.
//
// Inbound (consumed by Maximo / integrations):
//   POST   *                        — receive any payload; response is rules-driven
//                                      (falls back to 200 OK; status still overridable
//                                      via X-Mock-Status / ?status= when no rule matches)
//
// Legacy query API (kept for existing SIT tests):
//   GET    /messages                — list all buffered messages (newest first)
//   GET    /messages?batch=<num>    — most recent message containing <BILLBATCHNUM>num</BILLBATCHNUM>
//   GET    /messages/latest         — most recent message regardless of content
//   DELETE /messages                — clear the buffer (used between test runs)
//   GET    /health                  — liveness probe
//
// Management UI + API (namespaced under /__mock so it never collides with an
// integration target path):
//   GET    /__mock/                 — web UI (pretty-printed messages + rule editor)
//   GET    /__mock/api/messages     — buffered messages as JSON
//   DELETE /__mock/api/messages     — clear the buffer
//   GET    /__mock/api/rules        — current ruleset (parsed JSON + raw YAML)
//   GET    /__mock/api/rules/export — current ruleset as a YAML download
//   PUT    /__mock/api/rules        — replace the ruleset from posted YAML
//
// The message buffer is in-memory and capped at MAX_BUFFER entries to avoid leaks.

const express = require('express');
const path = require('path');
const rules = require('./rules');

const PORT = parseInt(process.env.PORT || '8085', 10);
const MAX_BUFFER = parseInt(process.env.MAX_BUFFER || '500', 10);
const RULES_FILE = process.env.RULES_FILE || path.join(__dirname, 'rules.yaml');

rules.init(RULES_FILE);

const app = express();

const messages = [];

function extractBillBatch(body) {
  const m = body && body.match(/<BILLBATCHNUM[^>]*>\s*(\d+)\s*<\/BILLBATCHNUM>/i);
  return m ? m[1] : null;
}

// --- management UI + API (registered before the catch-all POST) ------------

app.use('/__mock', express.static(path.join(__dirname, 'public')));

app.get('/__mock/api/messages', (_req, res) => {
  res.json({ count: messages.length, messages });
});

app.delete('/__mock/api/messages', (_req, res) => {
  messages.length = 0;
  res.json({ cleared: true });
});

app.get('/__mock/api/rules', (_req, res) => {
  res.json({ rules: rules.list(), yaml: rules.serializeYaml() });
});

app.get('/__mock/api/rules/export', (_req, res) => {
  res
    .type('application/x-yaml')
    .set('Content-Disposition', 'attachment; filename="rules.yaml"')
    .send(rules.serializeYaml());
});

// Accept a raw YAML body for import/save.
app.put('/__mock/api/rules', express.text({ type: '*/*', limit: '2mb' }), (req, res) => {
  try {
    rules.replaceFromYaml(typeof req.body === 'string' ? req.body : '');
    res.json({ ok: true, rules: rules.list(), yaml: rules.serializeYaml() });
  } catch (e) {
    res.status(400).json({ error: e.message });
  }
});

// --- legacy query API ------------------------------------------------------

app.get('/messages', (req, res) => {
  if (req.query.batch) {
    const hit = messages.find((m) => m.billBatch === String(req.query.batch));
    if (!hit) return res.status(404).json({ error: 'no message for batch', batch: req.query.batch });
    return res.json(hit);
  }
  res.json({ count: messages.length, messages });
});

app.get('/messages/latest', (_req, res) => {
  if (!messages.length) return res.status(404).json({ error: 'no messages received yet' });
  res.json(messages[0]);
});

app.delete('/messages', (_req, res) => {
  messages.length = 0;
  res.json({ cleared: true });
});

app.get('/health', (_req, res) => res.json({ ok: true, buffered: messages.length }));

// --- inbound payload capture (catch-all) -----------------------------------

app.use(express.text({ type: '*/*', limit: '10mb' }));

app.post(/.*/, (req, res) => {
  const body = typeof req.body === 'string' ? req.body : '';
  const billBatch = extractBillBatch(body);

  const decision = rules.evaluate({
    method: req.method,
    path: req.path,
    body,
    headers: req.headers,
    billBatch,
  });

  // No rule matched → preserve the legacy default, including the X-Mock-Status /
  // ?status= override so existing tests that rely on it keep working.
  let status = decision.status;
  let responseBody = decision.body;
  let contentType = decision.contentType || 'text/plain';
  if (decision.matchedRule == null) {
    status = parseInt(req.query.status || req.headers['x-mock-status'] || '200', 10);
    responseBody = 'OK';
    contentType = 'text/plain';
  }

  const entry = {
    receivedAt: new Date().toISOString(),
    method: req.method,
    path: req.path,
    headers: req.headers,
    billBatch,
    body,
    matchedRule: decision.matchedRule,
    responseStatus: status,
    responseBody,
  };
  messages.unshift(entry);
  if (messages.length > MAX_BUFFER) messages.length = MAX_BUFFER;

  if (decision.headers) {
    for (const [h, v] of Object.entries(decision.headers)) res.set(h, String(v));
  }
  res.status(status).type(contentType).send(responseBody);
});

app.listen(PORT, () => {
  console.log(`mock-receiver listening on :${PORT} (max buffer ${MAX_BUFFER}, rules ${RULES_FILE})`);
  console.log(`mock-receiver UI at http://localhost:${PORT}/__mock/`);
});
