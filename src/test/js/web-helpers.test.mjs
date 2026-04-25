import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';

const repoRoot = path.resolve(import.meta.dirname, '..', '..', '..');
const webRoot = path.join(repoRoot, 'src', 'main', 'resources', 'web');
const jsRoot = path.join(webRoot, 'js');

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

  addEventListener(type, listener) {
    this.listeners[type] = listener;
  }

  remove() {
    if (!this.parentNode) return;
    this.parentNode.children = this.parentNode.children.filter(child => child !== this);
    this.parentNode = null;
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
  loadScript(context, 'sessions.js');
  loadScript(context, 'details.js');
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
  }, 'Recapture request');

  assert.equal(button.tagName, 'button');
  assert.equal(button.className, 'primary action-main');
  assert.equal(button.dataset.action, 'replay-payload');
  assert.equal(button.dataset.routeId, 'route-a');
  assert.equal(button.dataset.destination, 'listener');
  assert.equal(button.textContent, 'Recapture request');
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

test('renderRequestActions keeps primary actions visible and secondary actions in menu', () => {
  const ctx = loadWebHelpers();

  const actions = ctx.renderRequestActions({ sessionId: 'session-1', routeId: 'route-a' }, 2);
  const visibleButtons = actions.children.filter(child => child.tagName === 'button');
  const menu = findFirst(actions, node => node.className === 'payload-actions-menu');

  assert.equal(visibleButtons.length, 2);
  assert.equal(visibleButtons[0].textContent, 'Recapture request');
  assert.equal(visibleButtons[1].textContent, 'Send direct');
  assert.ok(menu);
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
