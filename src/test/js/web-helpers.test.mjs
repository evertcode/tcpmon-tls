import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';

const repoRoot = path.resolve(import.meta.dirname, '..', '..', '..');
const webRoot = path.join(repoRoot, 'src', 'main', 'resources', 'web');
const jsRoot = path.join(webRoot, 'js');

// Minimal CSS selector engine covering what the app's JS actually uses:
// tag/#id/.class/[attr]/[attr="value"] tokens, :not(...), descendant
// combinators ("A B"), and comma-separated selector lists.
function parseCompoundSelector(compound) {
  let remaining = compound;
  const notSelectors = [];
  remaining = remaining.replace(/:not\(([^)]*)\)/g, (_, inner) => {
    notSelectors.push(inner.trim());
    return '';
  });
  const attrs = [];
  remaining = remaining.replace(/\[([a-zA-Z0-9_-]+)(?:="([^"]*)")?\]/g, (_, name, value) => {
    attrs.push({ name, value: value === undefined ? null : value });
    return '';
  });
  const classes = [];
  remaining = remaining.replace(/\.([a-zA-Z0-9_-]+)/g, (_, cls) => {
    classes.push(cls);
    return '';
  });
  let id = null;
  remaining = remaining.replace(/#([a-zA-Z0-9_-]+)/g, (_, val) => {
    id = val;
    return '';
  });
  const tag = remaining.trim() || null;
  return { tag, id, classes, attrs, notSelectors };
}

function elementMatchesCompound(node, parsed) {
  if (!node || node.nodeType !== 'element') return false;
  if (parsed.tag && node.tagName !== parsed.tag) return false;
  if (parsed.id && node.id !== parsed.id) return false;
  for (const cls of parsed.classes) {
    const nodeClasses = new Set(String(node.className || '').split(/\s+/).filter(Boolean));
    if (!nodeClasses.has(cls)) return false;
  }
  for (const attr of parsed.attrs) {
    let actual = node.attributes ? node.attributes[attr.name] : undefined;
    if (actual === undefined && attr.name === 'tabindex' && typeof node.tabIndex === 'number') {
      actual = String(node.tabIndex);
    }
    if (actual === undefined && attr.name.startsWith('data-') && node.dataset) {
      const key = attr.name.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase());
      actual = node.dataset[key];
    }
    if (actual === undefined) return false;
    if (attr.value !== null && actual !== attr.value) return false;
  }
  for (const notSelector of parsed.notSelectors) {
    if (elementMatchesCompound(node, parseCompoundSelector(notSelector))) return false;
  }
  return true;
}

function matchesSimpleSelector(node, simpleSelector) {
  const parts = simpleSelector.trim().split(/\s+/).map(parseCompoundSelector);
  if (!parts.length || !elementMatchesCompound(node, parts[parts.length - 1])) return false;
  let ancestor = node.parentNode;
  for (let i = parts.length - 2; i >= 0; i--) {
    let found = false;
    while (ancestor) {
      if (elementMatchesCompound(ancestor, parts[i])) {
        found = true;
        break;
      }
      ancestor = ancestor.parentNode;
    }
    if (!found) return false;
    ancestor = ancestor.parentNode;
  }
  return true;
}

function matchesSelector(node, selector) {
  return selector.split(',').some(part => matchesSimpleSelector(node, part));
}

function collectMatches(root, selector, results) {
  for (const child of root.children || []) {
    if (matchesSelector(child, selector)) results.push(child);
    collectMatches(child, selector, results);
  }
}

class FakeNode {
  constructor(tagName, nodeType = 'element') {
    this.tagName = tagName;
    this.nodeType = nodeType;
    this.children = [];
    this.dataset = {};
    this.style = {};
    this.className = '';
    this.id = '';
    this._textContent = '';
    this.attributes = {};
    this.listeners = {};
    this.parentNode = null;
    this.tabIndex = -1;
    this.classList = {
      add: (...tokens) => {
        const classes = new Set(String(this.className || '').split(/\s+/).filter(Boolean));
        for (const token of tokens) classes.add(token);
        this.className = [...classes].join(' ');
      },
      remove: (...tokens) => {
        const removeSet = new Set(tokens);
        this.className = String(this.className || '')
          .split(/\s+/)
          .filter(Boolean)
          .filter(token => !removeSet.has(token))
          .join(' ');
      },
      contains: token => new Set(String(this.className || '').split(/\s+/).filter(Boolean)).has(token),
      toggle: (token, force) => {
        const has = new Set(String(this.className || '').split(/\s+/).filter(Boolean)).has(token);
        const shouldHave = force === undefined ? !has : Boolean(force);
        if (shouldHave) {
          this.classList.add(token);
        } else {
          this.classList.remove(token);
        }
        return shouldHave;
      }
    };
  }

  appendChild(child) {
    child.parentNode = this;
    this.children.push(child);
    return child;
  }

  append(...items) {
    for (const item of items) {
      if (typeof item === 'string') {
        this.appendChild(new FakeNode('#text', 'text')).textContent = item;
      } else {
        this.appendChild(item);
      }
    }
  }

  replaceChildren(...items) {
    this.children = [];
    this.append(...items);
  }

  setAttribute(name, value) {
    this.attributes[name] = String(value);
  }

  getAttribute(name) {
    return Object.prototype.hasOwnProperty.call(this.attributes, name)
      ? this.attributes[name]
      : null;
  }

  addEventListener(type, listener) {
    this.listeners[type] = listener;
  }

  remove() {
    if (!this.parentNode) return;
    this.parentNode.children = this.parentNode.children.filter(child => child !== this);
    this.parentNode = null;
  }

  querySelectorAll(selector) {
    const results = [];
    collectMatches(this, selector, results);
    return results;
  }

  querySelector(selector) {
    return this.querySelectorAll(selector)[0] || null;
  }

  closest(selector) {
    let node = this;
    while (node) {
      if (matchesSelector(node, selector)) return node;
      node = node.parentNode;
    }
    return null;
  }

  set textContent(value) {
    this._textContent = String(value);
  }

  get textContent() {
    if (this.nodeType === 'text') {
      return this._textContent;
    }
    if (this.children.length) {
      return this.children.map(child => child.textContent).join('');
    }
    return this._textContent;
  }
}

function findFirst(node, predicate) {
  if (predicate(node)) {
    return node;
  }
  for (const child of node.children || []) {
    const match = findFirst(child, predicate);
    if (match) return match;
  }
  return null;
}

class FakeDocument {
  constructor() {
    this.nodesById = new Map();
  }

  createElement(tagName) {
    return new FakeNode(tagName);
  }

  createElementNS(_namespace, tagName) {
    return new FakeNode(tagName);
  }

  createDocumentFragment() {
    return new FakeNode('#fragment', 'fragment');
  }

  createTextNode(text) {
    const node = new FakeNode('#text', 'text');
    node.textContent = text;
    return node;
  }

  createRange() {
    return {
      createContextualFragment: html => {
        const fragment = this.createDocumentFragment();
        fragment.html = html;
        return fragment;
      }
    };
  }

  getElementById(id) {
    if (!this.nodesById.has(id)) {
      const node = new FakeNode('div');
      node.id = id;
      this.nodesById.set(id, node);
    }
    return this.nodesById.get(id);
  }

  querySelectorAll(selector) {
    const seen = new Set();
    const results = [];
    for (const root of this.nodesById.values()) {
      if (matchesSelector(root, selector) && !seen.has(root)) {
        seen.add(root);
        results.push(root);
      }
      for (const node of root.querySelectorAll(selector)) {
        if (!seen.has(node)) {
          seen.add(node);
          results.push(node);
        }
      }
    }
    return results;
  }

  querySelector(selector) {
    return this.querySelectorAll(selector)[0] || null;
  }
}

function createBrowserLikeContext() {
  const document = new FakeDocument();
  const context = {
    console,
    Date,
    setTimeout,
    clearTimeout,
    URL,
    Blob,
    navigator: {},
    fetch: () => { throw new Error('fetch not implemented in unit test'); },
    document,
    window: null,
    globalThis: null
  };
  context.window = context;
  context.globalThis = context;
  return vm.createContext(context);
}

function loadScript(context, fileName) {
  const source = fs.readFileSync(path.join(jsRoot, fileName), 'utf8');
  vm.runInContext(source, context, { filename: fileName });
}

function loadWebHelpers() {
  const context = createBrowserLikeContext();
  loadScript(context, 'state.js');
  loadScript(context, 'utils.js');
  loadScript(context, 'routes.js');
  loadScript(context, 'route-modal.js');
  loadScript(context, 'sessions.js');
  loadScript(context, 'details.js');
  loadScript(context, 'overview.js');
  loadScript(context, 'command-palette.js');
  loadScript(context, 'actions.js');
  return context;
}

test('formatDuration renders timing classes and placeholders', () => {
  const ctx = loadWebHelpers();

  assert.equal(ctx.formatDuration(null), '<span class="muted">—</span>');
  assert.equal(ctx.formatDuration('oops'), '<span class="muted">—</span>');
  assert.equal(ctx.formatDuration(150), '<span class="timing-fast">150 ms</span>');
  assert.equal(ctx.formatDuration(750), '<span class="timing-medium">750 ms</span>');
  assert.equal(ctx.formatDuration(1500), '<span class="timing-slow">1.5 s</span>');
});

test('formatBytes renders bytes, kilobytes and megabytes', () => {
  const ctx = loadWebHelpers();

  assert.equal(ctx.formatBytes(null), '<span class="muted">—</span>');
  assert.equal(ctx.formatBytes(0), '<span class="muted">0 B</span>');
  assert.equal(ctx.formatBytes(12), '12 B');
  assert.equal(ctx.formatBytes(2048), '2.0 KB');
  assert.equal(ctx.formatBytes(3 * 1024 * 1024), '3.0 MB');
});

test('formatBody pretty prints json and xml payloads', () => {
  const ctx = loadWebHelpers();

  const jsonBody = ctx.formatBody({
    isHttp: true,
    bodyText: '{"a":1,"b":{"c":2}}',
    headers: [{ name: 'Content-Type', value: 'application/json' }]
  });
  assert.equal(jsonBody, '{\n  "a": 1,\n  "b": {\n    "c": 2\n  }\n}');

  const xmlBody = ctx.formatBody({
    isHttp: true,
    bodyText: '<root><child>ok</child></root>',
    headers: [{ name: 'Content-Type', value: 'application/xml' }]
  });
  assert.equal(xmlBody, '<root>\n  <child>ok</child>\n</root>');
});

test('prettyPrintXml indents nested elements', () => {
  const ctx = loadWebHelpers();

  assert.equal(
    ctx.prettyPrintXml('<a><b/><c><d>v</d></c></a>'),
    '<a>\n  <b/>\n  <c>\n    <d>v</d>\n  </c>\n</a>'
  );
});

test('formatExportBody pretty prints downloaded payload bodies', () => {
  const ctx = loadWebHelpers();

  assert.equal(
    ctx.formatExportBody(
      {
        isHttp: true,
        headers: [{ name: 'Content-Type', value: 'application/json' }]
      },
      '{"id":5,"products":[{"productId":7,"quantity":1}]}'
    ),
    '{\n  "id": 5,\n  "products": [\n    {\n      "productId": 7,\n      "quantity": 1\n    }\n  ]\n}'
  );
});

test('formatExportJsonBody embeds valid JSON bodies as JSON values', () => {
  const ctx = loadWebHelpers();

  const body = ctx.formatExportJsonBody(
    {
      isHttp: true,
      headers: [{ name: 'Content-Type', value: 'application/json' }]
    },
    '{"id":5,"products":[{"productId":7,"quantity":1}]}'
  );

  assert.deepEqual(JSON.parse(JSON.stringify(body)), {
    id: 5,
    products: [{ productId: 7, quantity: 1 }]
  });
  assert.equal(
    ctx.formatExportJsonBody(
      {
        isHttp: true,
        headers: [{ name: 'Content-Type', value: 'application/xml' }]
      },
      '<root><ok>true</ok></root>'
    ),
    '<root>\n  <ok>true</ok>\n</root>'
  );
});

test('buildExchangeXml preserves readable body content inside CDATA', () => {
  const ctx = loadWebHelpers();

  const xml = ctx.buildExchangeXml(
    {
      exportedAt: '2026-04-23T12:00:00.000Z',
      sessionId: 'session-1',
      targetAddress: 'api.example.com:443',
      startedAt: '2026-04-23T11:59:59.000Z',
      durationMs: 123
    },
    {
      method: 'POST',
      path: '/orders',
      query: '',
      body: '{\n  "id": 5,\n  "userId": 3\n}'
    },
    {
      body: '<root>\n  <ok>true</ok>\n</root>'
    }
  );

  assert.match(xml, /<body>\n\s+<!\[CDATA\[\{\n  "id": 5,\n  "userId": 3\n\}\]\]>\n\s+<\/body>/);
  assert.match(xml, /<body>\n\s+<!\[CDATA\[<root>\n  <ok>true<\/ok>\n<\/root>\]\]>\n\s+<\/body>/);
  assert.equal(xml.includes('&quot;id&quot;'), false);
});

test('calcTtfb returns milliseconds between first request and response payload', () => {
  const ctx = loadWebHelpers();

  const ttfb = ctx.calcTtfb([
    { type: 'PAYLOAD', direction: 'CLIENT_TO_TARGET', timestamp: '2026-03-17T10:00:00.000Z' },
    { type: 'PAYLOAD', direction: 'TARGET_TO_CLIENT', timestamp: '2026-03-17T10:00:00.125Z' }
  ]);

  assert.equal(ttfb, 125);
  assert.equal(ctx.calcTtfb([]), null);
});

test('generateCurl builds a reproducible curl command from decoded request data', () => {
  const ctx = loadWebHelpers();

  const curl = ctx.generateCurl('api.example.com:443', {
    isHttp: true,
    request: {
      method: 'POST',
      path: '/v1/messages',
      query: 'limit=10'
    },
    headers: [
      { name: 'Content-Type', value: 'application/json' },
      { name: 'X-Custom', value: "it's ok" },
      { name: 'Content-Length', value: '99' }
    ],
    bodyText: '{"hello":"world"}'
  });

  assert.equal(
    curl,
    "curl -X POST 'https://api.example.com:443/v1/messages?limit=10' \\\n" +
      "  -H 'Content-Type: application/json' \\\n" +
      "  -H 'X-Custom: it\\'s ok' \\\n" +
      "  -d '{\"hello\":\"world\"}'"
  );
});

test('buildPayloadActionButton creates a button with dataset and label', () => {
  const ctx = loadWebHelpers();

  const button = ctx.buildPayloadActionButton('primary action-main', 'replay-payload', {
    routeId: 'route-a',
    destination: 'listener'
  }, 'Recapture request', 'replay');

  assert.equal(button.tagName, 'button');
  assert.equal(button.className, 'primary action-main');
  assert.equal(button.dataset.action, 'replay-payload');
  assert.equal(button.dataset.routeId, 'route-a');
  assert.equal(button.dataset.destination, 'listener');
  assert.equal(button.textContent, 'Recapture request');
  assert.equal(button.children[0].tagName, 'svg');
  assert.equal(button.children[0].attributes['aria-hidden'], 'true');
});

test('buildEmptyState renders message, hint and optional action', () => {
  const ctx = loadWebHelpers();
  const action = ctx.document.createElement('button');
  action.textContent = 'Create route';

  const empty = ctx.buildEmptyState('No routes configured yet.', 'Create a listener and target.', action);

  assert.equal(empty.className, 'empty empty-state');
  assert.equal(empty.textContent, 'No routes configured yet.Create a listener and target.Create route');
  assert.equal(findFirst(empty, node => node.tagName === 'button').textContent, 'Create route');
});

test('activeRouteCaptureHint uses configured listener and target', () => {
  const ctx = loadWebHelpers();
  ctx.patchState({
    activeRoute: 'route-a',
    proxyConfig: {
      routes: [{
        id: 'route-a',
        listener: { host: '127.0.0.1', port: 9000 },
        target: { host: 'api.example.test', port: 443 }
      }]
    }
  });

  assert.equal(
    ctx.activeRouteCaptureHint(),
    'Send client traffic to 127.0.0.1:9000; tcpmon will forward it to api.example.test:443.'
  );
});

test('updateRouteModalSummary renders route draft context', () => {
  const ctx = loadWebHelpers();
  ctx.document.getElementById('rm-id').value = 'orders-proxy';
  ctx.document.getElementById('rm-listener-host').value = '0.0.0.0';
  ctx.document.getElementById('rm-listener-port').value = '9001';
  ctx.document.getElementById('rm-listener-transport').value = 'TLS';
  ctx.document.getElementById('rm-target-host').value = 'api.internal.test';
  ctx.document.getElementById('rm-target-port').value = '443';
  ctx.document.getElementById('rm-target-transport').value = 'PLAIN';

  ctx.updateRouteModalSummary();

  const summary = ctx.document.getElementById('route-modal-summary');
  assert.equal(summary.children.length, 4);
  assert.equal(summary.children[0].textContent, 'orders-proxy');
  assert.equal(summary.children[1].textContent, 'Listener 0.0.0.0:9001');
  assert.equal(summary.children[2].textContent, 'Target api.internal.test:443');
  assert.equal(summary.children[3].textContent, 'TLS → PLAIN');
});

test('renderRequestActions keeps primary actions visible and secondary actions in menu', () => {
  const ctx = loadWebHelpers();

  const actions = ctx.renderRequestActions({ sessionId: 'session-1', routeId: 'route-a' }, 2);
  const visibleButtons = actions.children.filter(child => child.tagName === 'button');
  const menu = findFirst(actions, node => node.className === 'payload-actions-menu');

  assert.equal(visibleButtons.length, 2);
  assert.ok(menu);
  assert.equal(visibleButtons[0].textContent, 'Recapture via listener');
  assert.equal(visibleButtons[1].textContent, 'Replay to target');
  assert.equal(findFirst(menu, node => node.textContent === 'Copy as cURL').dataset.action, 'copy-curl-from-session');
  assert.equal(findFirst(menu, node => node.textContent === 'Download JSON').dataset.format, 'json');
  assert.equal(findFirst(menu, node => node.textContent === 'Download XML').dataset.format, 'xml');
});

test('buildPayloadBodySection renders a body viewer for formatted json bodies', () => {
  const ctx = loadWebHelpers();

  const body = ctx.buildPayloadBodySection(
    '{\n  "ok": true\n}',
    true,
    true,
    false,
    'session-1',
    0,
    {
      isHttp: true,
      bodyText: '{"ok":true}',
      headers: [{ name: 'Content-Type', value: 'application/json' }]
    }
  );

  const viewer = findFirst(body, node => node.className === 'body-viewer');
  assert.ok(viewer);
  assert.equal(viewer.dataset.mode, 'json');
  assert.equal(findFirst(body, node => node.tagName === 'pre'), null);
  assert.equal(findFirst(body, node => node.className === 'body-viewer-code').textContent, '{  "ok": true}');
});

test('buildPayloadBodySection groups body and header copy actions in toolbar', () => {
  const ctx = loadWebHelpers();

  const body = ctx.buildPayloadBodySection(
    '{"ok":true}',
    true,
    true,
    false,
    'session-1',
    0,
    {
      isHttp: true,
      bodyText: '{"ok":true}',
      headers: [{ name: 'Content-Type', value: 'application/json' }]
    },
    true
  );

  const toolbar = findFirst(body, node => node.className === 'payload-body-toolbar');
  assert.ok(toolbar);
  assert.equal(findFirst(toolbar, node => node.textContent === 'Copy headers').dataset.action, 'copy-current-headers');
  assert.equal(findFirst(toolbar, node => node.textContent === 'Copy body').dataset.action, 'copy-current-body');
});

test('body viewer folds and expands JSON object blocks', () => {
  const ctx = loadWebHelpers();

  const viewer = ctx.buildBodyViewer('{\n  "items": [\n    {\n      "id": 1\n    }\n  ]\n}', 'json');
  const code = findFirst(viewer, node => node.className === 'body-viewer-code');
  const toggle = findFirst(viewer, node => node.className === 'body-viewer-fold-toggle');

  assert.ok(toggle);
  assert.equal(code.textContent.includes('"id": 1'), true);

  toggle.listeners.click();

  assert.equal(toggle.textContent, '-');
  const collapsedCode = findFirst(viewer, node => node.className === 'body-viewer-code');
  assert.equal(collapsedCode.textContent.includes('"id": 1'), false);
  assert.equal(collapsedCode.textContent.includes('{ ... }'), true);

  const expandToggle = findFirst(viewer, node => node.className === 'body-viewer-fold-toggle');
  expandToggle.listeners.click();

  const expandedCode = findFirst(viewer, node => node.className === 'body-viewer-code');
  assert.equal(expandedCode.textContent.includes('"id": 1'), true);
});

test('body viewer folds XML element blocks', () => {
  const ctx = loadWebHelpers();

  const viewer = ctx.buildBodyViewer('<root>\n  <items>\n    <item>one</item>\n  </items>\n</root>', 'xml');
  const code = findFirst(viewer, node => node.className === 'body-viewer-code');
  const toggle = findFirst(viewer, node => node.className === 'body-viewer-fold-toggle');

  assert.ok(toggle);
  assert.equal(code.textContent.includes('<item>one</item>'), true);

  toggle.listeners.click();

  const collapsedCode = findFirst(viewer, node => node.className === 'body-viewer-code');
  assert.equal(collapsedCode.textContent.includes('<item>one</item>'), false);
  assert.equal(collapsedCode.textContent.includes('<root> ... </root>'), true);
});

test('body viewer load-full-body handler updates content using formatted payload text', async () => {
  const ctx = loadWebHelpers();
  ctx.fetchJson = async () => ({ bodyText: '<root><ok>true</ok></root>' });

  const body = ctx.buildPayloadBodySection(
    '<root/>',
    true,
    false,
    true,
    'session-1',
    2,
    {
      isHttp: true,
      bodyText: '<root/>',
      headers: [{ name: 'Content-Type', value: 'application/xml' }]
    }
  );

  const button = findFirst(body, node => node.tagName === 'button' && node.textContent === 'Load full body');
  await button.listeners.click();

  const viewerCode = findFirst(body, node => node.className === 'body-viewer-code');
  assert.equal(viewerCode.textContent, '<root>  <ok>true</ok></root>');
  assert.equal(findFirst(body, node => node.tagName === 'button' && node.textContent === 'Copy body').dataset.isRequest, 'false');
});

test('loadSessionDetails hydrates truncated request and response bodies before rendering', async () => {
  const ctx = loadWebHelpers();
  const requestedUrls = [];
  ctx.fetchJson = async url => {
    requestedUrls.push(url);
    if (url === '/api/sessions/session-1') {
      return {
        sessionId: 'session-1',
        routeId: 'route-a',
        exchanges: [{
          index: 0,
          request: {
            timestamp: '2026-04-23T12:00:00.000Z',
            size: 100,
            direction: 'CLIENT_TO_TARGET',
            decoded: {
              isHttp: true,
              bodyText: '{"preview":true}',
              bodyTruncated: true,
              startLine: 'POST /orders HTTP/1.1',
              headers: [{ name: 'Content-Type', value: 'application/json' }]
            }
          },
          response: {
            timestamp: '2026-04-23T12:00:00.125Z',
            size: 100,
            direction: 'TARGET_TO_CLIENT',
            decoded: {
              isHttp: true,
              bodyText: '{"preview":true}',
              bodyTruncated: true,
              startLine: 'HTTP/1.1 200 OK',
              headers: [{ name: 'Content-Type', value: 'application/json' }]
            }
          }
        }],
        events: []
      };
    }
    if (url.includes('direction=request')) {
      return { bodyText: '{"fullRequest":true}' };
    }
    if (url.includes('direction=response')) {
      return { bodyText: '{"fullResponse":true}' };
    }
    return {};
  };
  ctx.patchState({ activeSession: null, activeExchangeIndex: 0 });

  await ctx.loadSessionDetails('session-1');

  const payloads = ctx.document.getElementById('payloads');
  assert.equal(requestedUrls.includes('/api/sessions/session-1'), true);
  assert.equal(requestedUrls.some(url => url.includes('direction=request')), true);
  assert.equal(requestedUrls.some(url => url.includes('direction=response')), true);
  assert.equal(payloads.textContent.includes('fullRequest'), true);
  assert.equal(payloads.textContent.includes('fullResponse'), true);
  assert.equal(payloads.textContent.includes('Load full body'), false);
});

test('buildExchangeButtons creates exchange selectors and compare action', () => {
  const ctx = loadWebHelpers();
  ctx.patchState({
    diffMode: false,
    activeExchangeIndex: 1
  });

  const fragment = ctx.buildExchangeButtons([{ index: 0 }, { index: 1 }, { index: 2 }]);
  const actions = fragment.children[0];

  assert.equal(actions.className, 'actions');
  assert.equal(actions.children.length, 4);
  assert.equal(actions.children[0].dataset.action, 'select-exchange');
  assert.equal(actions.children[1].className, 'primary');
  assert.equal(actions.children[3].dataset.action, 'toggle-diff-mode');
  assert.equal(actions.children[3].textContent, 'Compare');
});

test('buildRequestTableElement marks selectable request rows for keyboard and aria', () => {
  const ctx = loadWebHelpers();

  const table = ctx.buildRequestTableElement([
    {
      sessionId: 'session-1',
      exchangeIndex: 0,
      requestMethod: 'GET',
      requestPath: '/health',
      responseStatusCode: 200,
      durationMs: 12,
      responseSizeBytes: 20,
      clientAddress: '127.0.0.1:5000',
      startedAt: '2026-04-23T12:00:00.000Z'
    }
  ], 'session-1', 0);

  const row = findFirst(table, node => node.tagName === 'tr' && node.dataset.sessionId === 'session-1');
  assert.equal(table.className, 'request-table');
  assert.equal(row.tabIndex, 0);
  assert.equal(row.attributes['aria-selected'], 'true');
});

test('buildSelectedSessionLabel prefers loaded session details for active selection', () => {
  const ctx = loadWebHelpers();

  const activeSession = ctx.resolveActiveSessionSummary(
    [{ sessionId: 'session-1', requestMethod: '', requestPath: '', clientAddress: '' }],
    [],
    'session-1',
    0,
    {
      sessionId: 'session-1',
      clientAddress: '127.0.0.1:54321',
      latestRequest: {
        request: {
          method: 'POST',
          path: '/v1/messages',
          query: 'limit=10'
        }
      }
    }
  );

  const label = ctx.buildSelectedSessionLabel(
    activeSession,
    'session-1'
  );

  assert.equal(label, 'POST /v1/messages?limit=10');
  assert.equal(activeSession.clientAddress, '127.0.0.1:54321');
});

test('calculateAverageDuration ignores missing durations and rounds the average', () => {
  const ctx = loadWebHelpers();

  const avg = ctx.calculateAverageDuration([
    { durationMs: 100 },
    { durationMs: null },
    { durationMs: 301 }
  ]);

  assert.equal(avg, 201);
  assert.equal(ctx.calculateAverageDuration([{ durationMs: null }]), null);
});

test('buildRouteHeaderViewModel splits route health from active selection context', () => {
  const ctx = loadWebHelpers();

  const model = ctx.buildRouteHeaderViewModel(
    'route-a',
    [
      {
        sessionId: 's2',
        listenerAddress: '127.0.0.1:9000',
        targetAddress: 'api.example.com:443',
        clientAddress: '127.0.0.1:55000',
        durationMs: 150,
        pendingCount: 2,
        requestMethod: 'GET',
        requestPath: '/health',
        responseStatusCode: '200',
        startedAt: '2026-03-17T10:00:00.000Z',
        live: false
      },
      {
        sessionId: 's1',
        listenerAddress: '127.0.0.1:9000',
        targetAddress: 'api.example.com:443',
        clientAddress: '127.0.0.1:54000',
        durationMs: null,
        pendingCount: 1,
        requestMethod: 'POST',
        requestPath: '/v1/messages',
        responseStatusCode: '',
        startedAt: '2026-03-17T10:01:00.000Z',
        live: true
      }
    ],
    [
      {
        sessionId: 's1',
        routeId: 'route-a',
        exchangeIndex: 0,
        requestMethod: 'POST',
        requestPath: '/v1/messages',
        responseStatusCode: '',
        clientAddress: '127.0.0.1:54000',
        durationMs: null,
        startedAt: '2026-03-17T10:01:00.000Z'
      },
      {
        sessionId: 's2',
        routeId: 'route-a',
        exchangeIndex: 0,
        requestMethod: 'GET',
        requestPath: '/health',
        responseStatusCode: '200',
        clientAddress: '127.0.0.1:55000',
        durationMs: 150,
        startedAt: '2026-03-17T10:00:00.000Z'
      }
    ],
    's1',
    0,
    null
  );

  assert.equal(model.total, 2);
  assert.equal(model.liveCount, 1);
  assert.equal(model.pendingCount, 3);
  assert.equal(model.avgDurationMs, 150);
  assert.equal(model.activeSelection.clientAddress, '127.0.0.1:54000');
  assert.equal(model.activeSelection.statusCode, '');
  assert.equal(model.activeSelection.durationMs, null);
});

test('buildIcon returns an svg element with the requested name', () => {
  const ctx = loadWebHelpers();

  const svg = ctx.buildIcon('search');

  assert.equal(svg.tagName, 'svg');
  assert.equal(svg.attributes.class, 'icon');
  assert.equal(svg.attributes.viewBox, '0 0 24 24');
  assert.equal(svg.attributes['aria-hidden'], 'true');
  assert.ok(svg.innerHTML.includes('circle'));
});

test('buildIcon falls back to a generic glyph for an unknown name', () => {
  const ctx = loadWebHelpers();

  const unknown = ctx.buildIcon('definitely-not-an-icon');
  const fallback = ctx.buildIcon('file');

  assert.equal(unknown.innerHTML, fallback.innerHTML);
});

test('buildSkeleton renders the requested row count', () => {
  const ctx = loadWebHelpers();

  const skeleton = ctx.buildSkeleton('table', 5);

  assert.equal(skeleton.className, 'skeleton skeleton-table');
  assert.equal(skeleton.attributes['aria-hidden'], 'true');
  assert.equal(skeleton.children.length, 5);
  assert.ok(skeleton.children.every(row => row.className === 'skeleton-row'));
  assert.ok(skeleton.children[0].children.every(line => line.className.startsWith('skeleton-line')));

  const unknownVariant = ctx.buildSkeleton('nope', 0);
  assert.equal(unknownVariant.className, 'skeleton skeleton-table');
  assert.equal(unknownVariant.children.length, 1);
});

test('buildErrorState renders the message and wires the retry action', () => {
  const ctx = loadWebHelpers();
  let retries = 0;

  const state = ctx.buildErrorState('Could not load this data.', 'Retry', () => { retries += 1; });

  assert.equal(state.className, 'error-state');
  assert.equal(state.attributes.role, 'alert');
  const message = state.children.find(child => child.className === 'error-state-message');
  assert.equal(message.textContent, 'Could not load this data.');

  const retryButton = state.children.find(child => child.dataset.action === 'retry');
  assert.ok(retryButton, 'expected a retry button');
  retryButton.listeners.click();
  assert.equal(retries, 1);

  const withoutRetry = ctx.buildErrorState('Could not load this data.', 'Retry', null);
  assert.equal(withoutRetry.children.some(child => child.dataset.action === 'retry'), false);
});

test('hydrateStaticIcons wires every data-icon button', () => {
  const ctx = loadWebHelpers();

  const iconOnly = ctx.document.getElementById('add-route-btn');
  iconOnly.dataset.icon = 'plus';
  iconOnly.setAttribute('aria-label', 'Add route');

  const labelled = ctx.document.getElementById('refresh-routes-btn');
  labelled.dataset.icon = 'refresh';
  labelled.dataset.iconLabel = 'Refresh';

  const untouched = ctx.document.getElementById('route-search');

  const hydrated = ctx.hydrateStaticIcons(ctx.document);

  assert.equal(hydrated, 2);

  assert.equal(iconOnly.children.length, 1);
  assert.equal(iconOnly.children[0].tagName, 'svg');
  assert.equal(iconOnly.classList.contains('icon-only'), true);
  assert.equal(iconOnly.attributes['aria-label'], 'Add route');

  assert.equal(labelled.children.length, 2);
  assert.equal(labelled.children[0].tagName, 'svg');
  assert.equal(labelled.children[1].className, 'button-label');
  assert.equal(labelled.children[1].textContent, 'Refresh');
  assert.equal(labelled.classList.contains('icon-only'), false);

  assert.equal(untouched.children.length, 0);
});

test('buildOverviewViewModel computes the error rate per route', () => {
  const ctx = loadWebHelpers();

  const viewModel = ctx.buildOverviewViewModel({
    windowMinutes: 60,
    totals: { requests: 10, errors: 2, clientErrors: 1, p50Ms: 120, p95Ms: 900 },
    routes: [
      { routeId: 'route-a', listener: '127.0.0.1:9000', target: 'a.example.com:443',
        status: 'healthy', requests: 8, errors: 0, clientErrors: 1, sparkline: [1, 2, 5] },
      { routeId: 'route-b', listener: '127.0.0.1:9001', target: 'b.example.com:443',
        status: 'failing', requests: 2, errors: 2, clientErrors: 0, sparkline: [0, 2] }
    ],
    slowestPaths: [{ method: 'GET', path: '/slow', routeId: 'route-a', p95Ms: 900, count: 3 }]
  });

  assert.equal(viewModel.empty, false);
  assert.equal(viewModel.windowMinutes, 60);
  assert.equal(viewModel.totals.requests, 10);
  assert.equal(viewModel.totals.errorRate, 0.2);

  assert.equal(viewModel.routes[0].routeId, 'route-a');
  assert.equal(viewModel.routes[0].errorRate, 0);
  assert.equal(viewModel.routes[1].routeId, 'route-b');
  assert.equal(viewModel.routes[1].errorRate, 1);

  assert.equal(viewModel.slowestPaths.length, 1);
  assert.equal(viewModel.slowestPaths[0].path, '/slow');
  assert.equal(viewModel.slowestPaths[0].count, 3);
});

test('buildOverviewViewModel marks a route as degraded above the error threshold', () => {
  const ctx = loadWebHelpers();

  const viewModel = ctx.buildOverviewViewModel({
    windowMinutes: 15,
    totals: { requests: 3, errors: 1 },
    routes: [
      { routeId: 'healthy-route', status: 'healthy', requests: 1, errors: 0, sparkline: [] },
      { routeId: 'degraded-route', status: 'degraded', requests: 1, errors: 0, sparkline: [] },
      { routeId: 'failing-route', status: 'failing', requests: 1, errors: 1, sparkline: [] },
      { routeId: 'idle-route', status: 'idle', requests: 0, errors: 0, sparkline: [] }
    ],
    slowestPaths: []
  });

  const byId = Object.fromEntries(viewModel.routes.map(route => [route.routeId, route]));
  assert.equal(byId['healthy-route'].degraded, false);
  assert.equal(byId['degraded-route'].degraded, true);
  assert.equal(byId['failing-route'].degraded, true);
  assert.equal(byId['idle-route'].degraded, false);
  assert.equal(byId['idle-route'].errorRate, 0);
});

test('buildSparkline renders one point per bucket', () => {
  const ctx = loadWebHelpers();

  const sparkline = ctx.buildSparkline([0, 3, 1, 6], { width: 80, height: 20 });

  assert.equal(sparkline.tagName, 'svg');
  assert.equal(sparkline.attributes['aria-hidden'], 'true');
  assert.equal(sparkline.children.length, 4);
  assert.equal(sparkline.children[0].attributes.class, 'sparkline-bar sparkline-bar-empty');
  assert.equal(sparkline.children[3].attributes.class, 'sparkline-bar');
  // The tallest bucket fills the full height.
  assert.equal(sparkline.children[3].attributes.height, '20');
  assert.equal(sparkline.children[3].attributes.y, '0');

  assert.equal(ctx.buildSparkline([], {}).children.length, 0);
});

test('buildOverviewViewModel returns an empty state flag when there is no traffic', () => {
  const ctx = loadWebHelpers();

  const viewModel = ctx.buildOverviewViewModel({
    windowMinutes: 1440,
    totals: { requests: 0, errors: 0, p50Ms: null, p95Ms: null },
    routes: [{ routeId: 'route-a', status: 'idle', requests: 0, errors: 0, sparkline: [0, 0] }],
    slowestPaths: []
  });

  assert.equal(viewModel.empty, true);
  assert.equal(viewModel.totals.errorRate, 0);
  assert.equal(viewModel.routes[0].status, 'idle');

  const missingPayload = ctx.buildOverviewViewModel(null);
  assert.equal(missingPayload.empty, true);
  assert.equal(missingPayload.routes.length, 0);
  assert.equal(missingPayload.slowestPaths.length, 0);
});

function requestRow(overrides = {}) {
  return {
    sessionId: overrides.sessionId || 's-1',
    exchangeIndex: 0,
    routeId: 'route-a',
    requestMethod: 'GET',
    requestPath: '/one',
    responseStatusCode: '200',
    durationMs: 100,
    responseSizeBytes: 100,
    clientAddress: '127.0.0.1:1',
    startedAt: '2026-08-21T12:00:00Z',
    ...overrides
  };
}

test('sortRequestRows orders by duration in both directions', () => {
  const ctx = loadWebHelpers();
  const rows = [
    requestRow({ sessionId: 's-a', durationMs: 500 }),
    requestRow({ sessionId: 's-b', durationMs: 20 }),
    requestRow({ sessionId: 's-c', durationMs: 1500 })
  ];

  const descending = ctx.sortRequestRows(rows, 'duration', 'desc');
  assert.deepEqual(Array.from(descending, row => row.sessionId), ['s-c', 's-a', 's-b']);

  const ascending = ctx.sortRequestRows(rows, 'duration', 'asc');
  assert.deepEqual(Array.from(ascending, row => row.sessionId), ['s-b', 's-a', 's-c']);

  // An unknown key leaves the server order untouched.
  assert.deepEqual(
    Array.from(ctx.sortRequestRows(rows, 'nope', 'desc'), row => row.sessionId),
    ['s-a', 's-b', 's-c']
  );
  // The input array is not mutated.
  assert.deepEqual(Array.from(rows, row => row.sessionId), ['s-a', 's-b', 's-c']);
});

test('sortRequestRows keeps a stable order for equal keys', () => {
  const ctx = loadWebHelpers();
  const rows = [
    requestRow({ sessionId: 's-a', durationMs: 100 }),
    requestRow({ sessionId: 's-b', durationMs: 100 }),
    requestRow({ sessionId: 's-c', durationMs: 100 }),
    requestRow({ sessionId: 's-d', durationMs: 900 })
  ];

  assert.deepEqual(
    Array.from(ctx.sortRequestRows(rows, 'duration', 'desc'), row => row.sessionId),
    ['s-d', 's-a', 's-b', 's-c']
  );
  assert.deepEqual(
    Array.from(ctx.sortRequestRows(rows, 'duration', 'asc'), row => row.sessionId),
    ['s-a', 's-b', 's-c', 's-d']
  );
});

test('latencyLevel classifies a duration against the thresholds', () => {
  const ctx = loadWebHelpers();

  assert.equal(ctx.latencyLevel(0), 'fast');
  assert.equal(ctx.latencyLevel(299), 'fast');
  assert.equal(ctx.latencyLevel(300), 'moderate');
  assert.equal(ctx.latencyLevel(999), 'moderate');
  assert.equal(ctx.latencyLevel(1000), 'slow');
  assert.equal(ctx.latencyLevel(5000), 'slow');
  assert.equal(ctx.latencyLevel(null), null);
  assert.equal(ctx.latencyLevel('oops'), null);

  assert.equal(ctx.latencyClass(50), 'timing-fast');
  assert.equal(ctx.latencyClass(500), 'timing-medium');
  assert.equal(ctx.latencyClass(2000), 'timing-slow');
  assert.equal(ctx.latencyClass(null), 'muted');
});

test('buildRequestTableElement applies the compact density class', () => {
  const ctx = loadWebHelpers();
  const rows = [requestRow()];

  const compact = ctx.buildRequestTableElement(rows, null, 0, false, { density: 'compact' });
  assert.equal(compact.className, 'request-table request-table-compact');
  assert.equal(compact.dataset.density, 'compact');

  const comfortable = ctx.buildRequestTableElement(rows, null, 0, false, { density: 'comfortable' });
  assert.equal(comfortable.className, 'request-table');
  assert.equal(comfortable.dataset.density, 'comfortable');

  // An unknown density falls back to comfortable.
  assert.equal(ctx.buildRequestTableElement(rows, null, 0, false, {}).dataset.density, 'comfortable');
});

test('buildRequestTableElement marks the active sort column', () => {
  const ctx = loadWebHelpers();
  const rows = [requestRow()];

  const table = ctx.buildRequestTableElement(rows, null, 0, false, {
    sortKey: 'duration',
    sortDirection: 'asc'
  });
  const headers = table.querySelectorAll('th');
  assert.equal(headers.length, 7);

  const durationHeader = headers.find(header => header.dataset.sortKey === 'duration');
  assert.equal(durationHeader.attributes['aria-sort'], 'ascending');
  assert.equal(durationHeader.classList.contains('is-sorted'), true);
  assert.equal(durationHeader.dataset.action, 'sort-requests');

  const pathHeader = headers.find(header => header.dataset.sortKey === 'path');
  assert.equal(pathHeader.attributes['aria-sort'], 'none');
  assert.equal(pathHeader.classList.contains('is-sorted'), false);

  // The route column only appears when the table shows every route.
  const withRoute = ctx.buildRequestTableElement(rows, null, 0, true, {});
  assert.equal(withRoute.querySelectorAll('th').length, 8);
});

test('buildPaletteCommands includes one command per route', () => {
  const ctx = loadWebHelpers();

  const withoutRoutes = ctx.buildPaletteCommands({});
  const routeCommands = withoutRoutes.filter(command => command.id.startsWith('open-route:'));
  assert.equal(routeCommands.length, 0);
  assert.ok(withoutRoutes.some(command => command.label === 'Go to Overview'));
  assert.ok(withoutRoutes.some(command => command.label === 'Clear filters'));
  assert.ok(withoutRoutes.some(command => command.label === 'Toggle theme'));

  const withRoutes = ctx.buildPaletteCommands({
    proxyConfig: { routes: [{ id: 'route-a' }, { id: 'route-b' }] }
  });
  const openCommands = withRoutes.filter(command => command.id.startsWith('open-route:'));
  assert.equal(openCommands.length, 2);
  assert.deepEqual(Array.from(openCommands, command => command.label),
    ['Open route: route-a', 'Open route: route-b']);
  assert.ok(openCommands.every(command => typeof command.run === 'function'));
});

test('filterPaletteCommands matches on the command label', () => {
  const ctx = loadWebHelpers();
  const commands = ctx.buildPaletteCommands({
    proxyConfig: { routes: [{ id: 'payments-api' }, { id: 'billing-api' }] }
  });

  assert.deepEqual(
    Array.from(ctx.filterPaletteCommands(commands, 'payments'), command => command.label),
    ['Open route: payments-api']
  );
  // The match ignores case and surrounding blanks.
  assert.deepEqual(
    Array.from(ctx.filterPaletteCommands(commands, '  THEME  '), command => command.label),
    ['Toggle theme']
  );
  assert.equal(ctx.filterPaletteCommands(commands, '').length, commands.length);
  assert.equal(ctx.filterPaletteCommands(commands, 'no-such-command').length, 0);
  assert.equal(ctx.filterPaletteCommands(null, 'x').length, 0);
});
