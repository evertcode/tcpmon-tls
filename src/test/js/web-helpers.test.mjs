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
  }

  appendChild(child) {
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

class FakeDocument {
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

test('buildSelectedSessionLabel prefers loaded session details for active selection', () => {
  const ctx = loadWebHelpers();

  const activeSession = ctx.resolveActiveSessionSummary(
    [{ sessionId: 'session-1', requestMethod: '', requestPath: '', clientAddress: '' }],
    'session-1',
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
    's1',
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
