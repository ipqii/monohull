// Mock Receiver UI — dependency-free. Renders buffered messages (pretty-printed)
// and a rules editor backed by the /__mock/api endpoints.

const API = '/__mock/api';

// --- helpers ---------------------------------------------------------------

function el(tag, attrs, children) {
  const node = document.createElement(tag);
  if (attrs) {
    for (const [k, v] of Object.entries(attrs)) {
      if (k === 'class') node.className = v;
      else if (k === 'text') node.textContent = v;
      else if (v != null) node.setAttribute(k, v);
    }
  }
  for (const c of children || []) node.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
  return node;
}

function escapeHtml(s) {
  return String(s).replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]));
}

// Best-effort pretty printer: JSON via parse/stringify, XML via a light indenter,
// anything else returned verbatim.
function prettyPrint(body) {
  if (!body) return '';
  const trimmed = body.trim();
  if ((trimmed.startsWith('{') || trimmed.startsWith('['))) {
    try { return JSON.stringify(JSON.parse(trimmed), null, 2); } catch (_) { /* fall through */ }
  }
  if (trimmed.startsWith('<')) {
    try { return formatXml(trimmed); } catch (_) { /* fall through */ }
  }
  return body;
}

function formatXml(xml) {
  // Insert newlines between tags, then re-indent by depth.
  const withBreaks = xml.replace(/>\s*</g, '>\n<');
  let depth = 0;
  return withBreaks
    .split('\n')
    .map((line) => {
      const l = line.trim();
      if (!l) return '';
      if (/^<\/.+>/.test(l)) depth = Math.max(0, depth - 1);
      const indented = '  '.repeat(depth) + l;
      // Open tag that isn't self-closing and doesn't also close on the same line.
      if (/^<[^!?/][^>]*[^/]>$/.test(l) && !/^<.+>.*<\/.+>$/.test(l)) depth++;
      return indented;
    })
    .filter((l) => l.length)
    .join('\n');
}

async function api(method, path, body, asText) {
  const opts = { method };
  if (body != null) { opts.body = body; opts.headers = { 'Content-Type': 'text/plain' }; }
  const r = await fetch(API + path, opts);
  if (asText) return { ok: r.ok, text: await r.text() };
  let data = null;
  try { data = await r.json(); } catch (_) { /* ignore */ }
  return { ok: r.ok, data };
}

// --- tabs ------------------------------------------------------------------

document.querySelectorAll('.tab').forEach((tab) => {
  tab.addEventListener('click', () => {
    document.querySelectorAll('.tab').forEach((t) => t.classList.remove('active'));
    document.querySelectorAll('.tab-panel').forEach((p) => p.classList.remove('active'));
    tab.classList.add('active');
    document.getElementById('tab-' + tab.dataset.tab).classList.add('active');
    if (tab.dataset.tab === 'rules') loadRules();
  });
});

// --- messages --------------------------------------------------------------

const messagesEl = document.getElementById('messages');
const emptyEl = document.getElementById('messages-empty');
const countEl = document.getElementById('msg-count');

function renderMessage(m, idx) {
  const statusClass = m.responseStatus >= 400 ? 'status-bad' : 'status-ok';
  const ruleBadge = m.matchedRule
    ? el('span', { class: 'badge rule', text: 'rule: ' + m.matchedRule })
    : el('span', { class: 'badge no-rule', text: 'no rule' });

  const head = el('div', { class: 'msg-head' }, [
    el('span', { class: 'method', text: m.method }),
    el('span', { class: 'path', text: m.path }),
    el('span', { class: 'badge ' + statusClass, text: '→ ' + m.responseStatus }),
    ruleBadge,
    el('span', { class: 'ts', text: m.receivedAt }),
  ]);

  const bodyWrap = el('div', { class: 'msg-body' });

  // request body
  const reqPretty = prettyPrint(m.body);
  const reqBlock = el('div', { class: 'body-block' }, [
    el('p', { class: 'section-label', text: 'Request body' + (m.billBatch ? ' · batch ' + m.billBatch : '') }),
    el('pre', { class: 'code', text: reqPretty || '(empty)' }),
  ]);
  reqBlock.appendChild(makeCopyBtn(reqPretty));
  bodyWrap.appendChild(reqBlock);

  // headers (collapsed)
  const headersDetails = el('details', { class: 'headers' }, [
    el('summary', { text: 'Request headers' }),
    el('pre', { class: 'code', text: JSON.stringify(m.headers, null, 2) }),
  ]);
  bodyWrap.appendChild(headersDetails);

  // response body
  if (m.responseBody) {
    const respPretty = prettyPrint(m.responseBody);
    const respBlock = el('div', { class: 'body-block' }, [
      el('p', { class: 'section-label', text: 'Response body' }),
      el('pre', { class: 'code', text: respPretty }),
    ]);
    respBlock.appendChild(makeCopyBtn(respPretty));
    bodyWrap.appendChild(respBlock);
  }

  const card = el('div', { class: 'msg' }, [head, bodyWrap]);
  head.addEventListener('click', () => card.classList.toggle('open'));
  if (idx === 0) card.classList.add('open');
  return card;
}

function makeCopyBtn(text) {
  const btn = el('button', { class: 'copy-btn', text: 'Copy' });
  btn.addEventListener('click', (e) => {
    e.stopPropagation();
    navigator.clipboard.writeText(text).then(() => {
      btn.textContent = 'Copied';
      setTimeout(() => (btn.textContent = 'Copy'), 1200);
    });
  });
  return btn;
}

async function loadMessages() {
  const { ok, data } = await api('GET', '/messages');
  if (!ok || !data) return;
  countEl.textContent = data.count + ' buffered';
  messagesEl.innerHTML = '';
  if (!data.messages.length) {
    emptyEl.style.display = 'block';
    return;
  }
  emptyEl.style.display = 'none';
  data.messages.forEach((m, i) => messagesEl.appendChild(renderMessage(m, i)));
}

document.getElementById('refresh-btn').addEventListener('click', loadMessages);
document.getElementById('clear-btn').addEventListener('click', async () => {
  await api('DELETE', '/messages');
  loadMessages();
});

let pollTimer = null;
function setPolling(on) {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
  if (on) pollTimer = setInterval(loadMessages, 2000);
}
const autoEl = document.getElementById('auto-refresh');
autoEl.addEventListener('change', () => setPolling(autoEl.checked));

// --- rules -----------------------------------------------------------------

const editorEl = document.getElementById('rules-editor');
const summaryEl = document.getElementById('rules-summary');
const errorEl = document.getElementById('rules-error');
const rulesStatusEl = document.getElementById('rules-status');

function showError(msg) {
  if (!msg) { errorEl.hidden = true; return; }
  errorEl.hidden = false;
  errorEl.textContent = msg;
}

function renderRuleSummary(rules) {
  summaryEl.innerHTML = '';
  rules.forEach((r) => {
    const matchBits = [];
    const m = r.match || {};
    if (m.method) matchBits.push('method=' + m.method);
    if (m.pathRegex) matchBits.push('path~' + m.pathRegex);
    if (m.bodyContains) matchBits.push('contains "' + m.bodyContains + '"');
    if (m.bodyRegex) matchBits.push('body~' + m.bodyRegex);
    if (m.bodyNotRegex) matchBits.push('body!~' + m.bodyNotRegex);
    if (m.headerContains) matchBits.push('headers ' + JSON.stringify(m.headerContains));
    const resp = r.respond || {};
    summaryEl.appendChild(
      el('div', { class: 'rule-card' }, [
        el('h3', { text: r.name }),
        el('div', { class: 'row' }, [matchBitsToNode('match', matchBits.join('  ·  ') || 'any')]),
        el('div', { class: 'row' }, [matchBitsToNode('respond', resp.status + (resp.contentType ? ' ' + resp.contentType : ''))]),
      ])
    );
  });
}

function matchBitsToNode(label, value) {
  const frag = document.createDocumentFragment();
  frag.appendChild(el('b', { text: label + ': ' }));
  frag.appendChild(document.createTextNode(value));
  return frag;
}

async function loadRules() {
  const { ok, data } = await api('GET', '/rules');
  if (!ok || !data) return;
  editorEl.value = data.yaml;
  renderRuleSummary(data.rules);
  showError(null);
}

document.getElementById('rules-save').addEventListener('click', async () => {
  showError(null);
  const { ok, data } = await api('PUT', '/rules', editorEl.value);
  if (!ok) { showError((data && data.error) || 'Failed to save rules'); return; }
  editorEl.value = data.yaml;
  renderRuleSummary(data.rules);
  rulesStatusEl.textContent = 'Saved ✓';
  setTimeout(() => (rulesStatusEl.textContent = ''), 1500);
});

document.getElementById('rules-export').addEventListener('click', () => {
  const blob = new Blob([editorEl.value], { type: 'application/x-yaml' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = 'rules.yaml'; a.click();
  URL.revokeObjectURL(url);
});

const fileEl = document.getElementById('rules-file');
document.getElementById('rules-import').addEventListener('click', () => fileEl.click());
fileEl.addEventListener('change', () => {
  const file = fileEl.files[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = () => { editorEl.value = reader.result; showError('Imported — click Save to apply.'); errorEl.hidden = false; };
  reader.readAsText(file);
  fileEl.value = '';
});

document.getElementById('rules-add').addEventListener('click', () => {
  const skeleton = [
    '',
    '  - name: new-rule',
    '    match:',
    '      bodyContains: "<SOMETAG"',
    '    respond:',
    '      status: 200',
    '      contentType: application/xml',
    '      body: |',
    '        <ack>OK</ack>',
  ].join('\n');
  editorEl.value = editorEl.value.replace(/\s*$/, '') + '\n' + skeleton + '\n';
  showError('Added a rule skeleton — edit then click Save.');
  errorEl.hidden = false;
});

// --- boot ------------------------------------------------------------------

loadMessages();
setPolling(true);
