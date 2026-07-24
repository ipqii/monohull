// Rules engine for the mock receiver.
//
// Rules are authored in YAML (see rules.yaml) and evaluated top-to-bottom against
// each inbound request; the first matching rule wins. A matched rule produces a
// configurable HTTP response (status, headers, content-type and a templated body
// that can echo captured values back to the caller). With no match the receiver
// falls back to the legacy default (200 OK).
//
// The ruleset is held in memory and reloaded from RULES_FILE on boot; UI edits are
// persisted back to that file so they survive a container restart.

const fs = require('fs');
const yaml = require('js-yaml');

const DEFAULT_RULES_FILE = process.env.RULES_FILE || '/app/rules.yaml';

let rulesFile = DEFAULT_RULES_FILE;
let rules = [];

// --- loading / persistence -------------------------------------------------

function init(file) {
  rulesFile = file || DEFAULT_RULES_FILE;
  try {
    const text = fs.readFileSync(rulesFile, 'utf8');
    rules = parse(text);
    console.log(`mock-receiver: loaded ${rules.length} rule(s) from ${rulesFile}`);
  } catch (e) {
    rules = [];
    console.warn(`mock-receiver: no rules loaded from ${rulesFile} (${e.message}); starting empty`);
  }
}

// Parse + validate YAML text into a normalized rules array. Throws on malformed
// YAML, bad shape, or invalid regex so callers can surface a 400.
function parse(text) {
  const doc = yaml.load(text);
  if (doc == null) return [];
  if (typeof doc !== 'object' || !Array.isArray(doc.rules)) {
    throw new Error('rules file must be a mapping with a "rules" list');
  }
  return doc.rules.map((raw, i) => validateRule(raw, i));
}

function validateRule(raw, i) {
  if (!raw || typeof raw !== 'object') {
    throw new Error(`rule #${i + 1} must be a mapping`);
  }
  const match = raw.match || {};
  const respond = raw.respond || {};
  if (typeof match !== 'object') throw new Error(`rule #${i + 1}: "match" must be a mapping`);
  if (typeof respond !== 'object') throw new Error(`rule #${i + 1}: "respond" must be a mapping`);

  // Pre-compile regexes so an invalid pattern fails fast at save/import time.
  const compiled = {};
  for (const key of ['pathRegex', 'bodyRegex', 'bodyNotRegex']) {
    if (match[key] != null) {
      try {
        compiled[key] = new RegExp(String(match[key]), 'i');
      } catch (e) {
        throw new Error(`rule #${i + 1}: invalid ${key}: ${e.message}`);
      }
    }
  }

  return {
    name: raw.name ? String(raw.name) : `rule-${i + 1}`,
    match: {
      method: match.method ? String(match.method).toUpperCase() : null,
      pathRegex: match.pathRegex != null ? String(match.pathRegex) : null,
      bodyContains: match.bodyContains != null ? String(match.bodyContains) : null,
      bodyRegex: match.bodyRegex != null ? String(match.bodyRegex) : null,
      bodyNotRegex: match.bodyNotRegex != null ? String(match.bodyNotRegex) : null,
      headerContains:
        match.headerContains && typeof match.headerContains === 'object'
          ? match.headerContains
          : null,
      _compiled: compiled,
    },
    respond: {
      status: respond.status != null ? parseInt(respond.status, 10) : 200,
      contentType: respond.contentType != null ? String(respond.contentType) : null,
      headers: respond.headers && typeof respond.headers === 'object' ? respond.headers : null,
      body: respond.body != null ? String(respond.body) : '',
    },
  };
}

// Replace the in-memory ruleset from YAML text and persist it to RULES_FILE.
// Throws (without mutating state) if the text is invalid.
function replaceFromYaml(text) {
  const next = parse(text);
  rules = next;
  try {
    fs.writeFileSync(rulesFile, text, 'utf8');
  } catch (e) {
    console.warn(`mock-receiver: could not persist rules to ${rulesFile}: ${e.message}`);
  }
  return rules;
}

// Emit the current ruleset as YAML (used by export + the UI editor).
function serializeYaml() {
  const plain = rules.map((r) => {
    const match = {};
    if (r.match.method) match.method = r.match.method;
    if (r.match.pathRegex != null) match.pathRegex = r.match.pathRegex;
    if (r.match.bodyContains != null) match.bodyContains = r.match.bodyContains;
    if (r.match.bodyRegex != null) match.bodyRegex = r.match.bodyRegex;
    if (r.match.bodyNotRegex != null) match.bodyNotRegex = r.match.bodyNotRegex;
    if (r.match.headerContains) match.headerContains = r.match.headerContains;
    const respond = { status: r.respond.status };
    if (r.respond.contentType) respond.contentType = r.respond.contentType;
    if (r.respond.headers) respond.headers = r.respond.headers;
    respond.body = r.respond.body;
    return { name: r.name, match, respond };
  });
  return yaml.dump({ rules: plain }, { lineWidth: -1, quotingType: '"' });
}

function list() {
  // Strip internal compiled regexes before handing rules to the UI.
  return rules.map((r) => ({
    name: r.name,
    match: {
      method: r.match.method,
      pathRegex: r.match.pathRegex,
      bodyContains: r.match.bodyContains,
      bodyRegex: r.match.bodyRegex,
      bodyNotRegex: r.match.bodyNotRegex,
      headerContains: r.match.headerContains,
    },
    respond: r.respond,
  }));
}

// --- evaluation ------------------------------------------------------------

function ruleMatches(rule, ctx) {
  const m = rule.match;
  if (m.method && m.method !== ctx.method.toUpperCase()) return null;
  if (m._compiled.pathRegex && !m._compiled.pathRegex.test(ctx.path)) return null;
  if (m.bodyContains && ctx.body.indexOf(m.bodyContains) < 0) return null;
  if (m._compiled.bodyNotRegex && m._compiled.bodyNotRegex.test(ctx.body)) return null;
  if (m.headerContains) {
    for (const [h, sub] of Object.entries(m.headerContains)) {
      const val = ctx.headers[h.toLowerCase()];
      if (val == null || String(val).indexOf(String(sub)) < 0) return null;
    }
  }
  // bodyRegex must match last so its capture groups feed the template.
  let captures = [];
  if (m._compiled.bodyRegex) {
    const hit = ctx.body.match(m._compiled.bodyRegex);
    if (!hit) return null;
    captures = hit;
  } else if (m._compiled.pathRegex) {
    const hit = ctx.path.match(m._compiled.pathRegex);
    captures = hit || [];
  }
  return { captures };
}

// Render {{1}}..{{n}} capture refs and named helpers in a response body template.
function renderTemplate(template, captures, ctx) {
  return String(template).replace(/\{\{\s*([\w]+)\s*\}\}/g, (whole, token) => {
    if (/^\d+$/.test(token)) {
      const idx = parseInt(token, 10);
      return captures[idx] != null ? captures[idx] : '';
    }
    switch (token) {
      case 'billBatch':
        return ctx.billBatch || '';
      case 'path':
        return ctx.path || '';
      case 'now':
        return new Date().toISOString();
      default:
        return whole;
    }
  });
}

// Evaluate the ruleset against a request context.
// ctx: { method, path, body, headers, billBatch }
// Returns { status, headers, contentType, body, matchedRule } — matchedRule is null
// when nothing matched and the caller should apply the legacy default response.
function evaluate(ctx) {
  for (const rule of rules) {
    const hit = ruleMatches(rule, ctx);
    if (!hit) continue;
    return {
      matchedRule: rule.name,
      status: rule.respond.status || 200,
      contentType: rule.respond.contentType || null,
      headers: rule.respond.headers || null,
      body: renderTemplate(rule.respond.body, hit.captures, ctx),
    };
  }
  return { matchedRule: null, status: 200, contentType: null, headers: null, body: 'OK' };
}

module.exports = { init, parse, replaceFromYaml, serializeYaml, list, evaluate };
