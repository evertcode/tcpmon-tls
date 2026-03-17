function renderRequestActions(data, payload) {
  if (!payload?.base64 || !data?.routeId) {
    return null;
  }
  const actions = document.createElement('div');
  actions.className = 'payload-actions';
  actions.append(
    buildPayloadActionButton('primary action-main', 'replay-payload', {
      routeId: data.routeId,
      base64: payload.base64,
      destination: 'listener'
    }, 'Recapture request'),
    buildPayloadActionButton('secondary action-alt', 'replay-payload', {
      routeId: data.routeId,
      base64: payload.base64,
      destination: 'target'
    }, 'Send direct'),
    buildPayloadActionButton('utility', 'copy-curl-from-session', {}, 'Copy as cURL')
  );
  return actions;
}

async function exportHar() {
  const sessions = sessionsForActiveRoute();
  const activeRoute = getState('activeRoute');
  if (!sessions.length) { setStatus('error', 'No sessions to export'); return; }
  setStatus('info', 'Building HAR export...');
  const entries = [];
  for (const session of sessions) {
    try {
      const data = await fetchJson('/api/sessions/' + session.sessionId);
      const exchanges = data.exchanges || [];
      for (const exchange of exchanges) {
        const req = exchange.request;
        const res = exchange.response;
        if (!req) continue;
        const reqDecoded = req.decoded || {};
        const resDecoded = res ? (res.decoded || {}) : {};
        const reqHeaders = Array.isArray(reqDecoded.headers) ? reqDecoded.headers : [];
        const resHeaders = Array.isArray(resDecoded.headers) ? resDecoded.headers : [];
        const reqMeta = reqDecoded.request || {};
        const resStart = resDecoded.startLine || '';
        const statusCode = parseInt((resStart.split(' ')[1] || '0'), 10) || 0;
        const startedAt = new Date(session.startedAt || Date.now()).toISOString();
        const ttfb = calcTtfb(data.events || []);
        const totalMs = session.durationMs != null ? Number(session.durationMs) : 0;
        const url = 'https://' + (data.targetAddress || 'unknown') + (reqMeta.path || '/') + (reqMeta.query ? '?' + reqMeta.query : '');
        const entry = {
          startedDateTime: startedAt,
          time: totalMs,
          request: {
            method: reqMeta.method || 'GET',
            url,
            httpVersion: reqMeta.version || 'HTTP/1.1',
            headers: reqHeaders.map(h => ({ name: h.name || '', value: h.value || '' })),
            queryString: [],
            cookies: [],
            headersSize: -1,
            bodySize: req.size || 0,
            postData: reqDecoded.bodyText ? { mimeType: '', text: reqDecoded.bodyText } : undefined
          },
          response: res ? {
            status: statusCode,
            statusText: resStart.split(' ').slice(2).join(' ') || '',
            httpVersion: (resStart.split(' ')[0]) || 'HTTP/1.1',
            headers: resHeaders.map(h => ({ name: h.name || '', value: h.value || '' })),
            cookies: [],
            content: {
              size: res.size || 0,
              mimeType: (resHeaders.find(h => String(h.name || '').toLowerCase() === 'content-type') || {}).value || '',
              text: resDecoded.bodyText || ''
            },
            redirectURL: '',
            headersSize: -1,
            bodySize: res.size || 0
          } : { status: 0, statusText: '', httpVersion: 'HTTP/1.1', headers: [], cookies: [], content: { size: 0, mimeType: '', text: '' }, redirectURL: '', headersSize: -1, bodySize: -1 },
          cache: {},
          timings: { send: 0, wait: ttfb != null ? ttfb : totalMs, receive: 0 }
        };
        entries.push(entry);
      }
    } catch (e) {
      // Skip failed sessions.
    }
  }
  const har = {
    log: {
      version: '1.2',
      creator: { name: 'tcpmon-tls', version: '1.0' },
      entries
    }
  };
  const blob = new Blob([JSON.stringify(har, null, 2)], { type: 'application/json' });
  const link = document.createElement('a');
  const dateStr = new Date().toISOString().slice(0, 10);
  link.href = URL.createObjectURL(blob);
  link.download = `tcpmon-${activeRoute || 'export'}-${dateStr}.har`;
  link.click();
  URL.revokeObjectURL(link.href);
  setStatus('success', `HAR exported: ${entries.length} request${entries.length !== 1 ? 's' : ''}`);
}

function resolvePayload(isRequest) {
  const data = getState('lastLoadedSession');
  if (!data) return null;
  const activeExchangeIndex = getState('activeExchangeIndex');
  const exchange = (data.exchanges || [])[activeExchangeIndex] || {};
  return isRequest
    ? (exchange.request || data.latestRequest)
    : (exchange.response || data.latestResponse);
}

function copyCurrentBody(isRequest) {
  const payload = resolvePayload(isRequest);
  if (!payload) return;
  const bodyText = formatBody(payload.decoded || {});
  if (!bodyText) return;
  copyText(bodyText);
}

function copyCurrentHeaders(isRequest) {
  const payload = resolvePayload(isRequest);
  if (!payload) return;
  const headers = Array.isArray(payload.decoded?.headers) ? payload.decoded.headers : [];
  if (!headers.length) return;
  const text = headers.map(h => (h.name || '') + ': ' + (h.value || '')).join('\n');
  copyText(text);
}

function copyCurlFromSession() {
  const lastLoadedSession = getState('lastLoadedSession');
  const payload = resolvePayload(true);
  if (!payload) return;
  const curl = generateCurl((lastLoadedSession || {}).targetAddress || '', payload.decoded || {});
  copyText(curl);
}

function generateCurl(targetAddress, decoded) {
  if (!decoded.isHttp) return '';
  const req = decoded.request || {};
  const headers = Array.isArray(decoded.headers) ? decoded.headers : [];
  const method = req.method || 'GET';
  const path = req.path || '/';
  const query = req.query ? '?' + req.query : '';
  let host = targetAddress || '';
  if (host && !host.startsWith('http')) {
    host = 'https://' + host;
  }
  const url = host + path + query;
  const parts = [`curl -X ${method} '${url}'`];
  for (const h of headers) {
    const name = String(h.name || '').toLowerCase();
    if (name === 'content-length' || name === 'transfer-encoding') continue;
    parts.push(`  -H '${h.name}: ${String(h.value || '').replaceAll("'", "\\'")}'`);
  }
  const body = decoded.bodyText || '';
  if (body) {
    parts.push(`  -d '${body.replaceAll("'", "\\'")}'`);
  }
  return parts.join(' \\\n');
}

function renderHeadersTable(headers, decoded, isRequest) {
  if (!decoded.isHttp) {
    const pre = document.createElement('pre');
    pre.textContent = 'Non-HTTP payload';
    return pre;
  }
  if (!headers.length) {
    const pre = document.createElement('pre');
    pre.textContent = 'No headers';
    return pre;
  }
  const fragment = document.createDocumentFragment();

  const toolbar = document.createElement('div');
  toolbar.style.display = 'flex';
  toolbar.style.justifyContent = 'flex-end';
  toolbar.style.marginBottom = '6px';
  toolbar.appendChild(buildPayloadActionButton('utility', 'copy-current-headers', { isRequest: String(isRequest) }, 'Copy headers'));

  const table = document.createElement('table');
  table.className = 'headers-table';
  const tbody = document.createElement('tbody');
  for (const header of headers) {
    const row = document.createElement('tr');
    row.append(buildHeaderCell(header.name || ''), buildHeaderCell(header.value || ''));
    tbody.appendChild(row);
  }
  table.appendChild(tbody);

  fragment.append(toolbar, table);
  return fragment;
}

function showEdit(pendingId, decodedPayload, base64Value) {
  const editor = document.getElementById('editor');
  if (decodedPayload?.isHttp) {
    const request = decodedPayload.request || {};
    editor.replaceChildren(buildHttpEditorCard(pendingId, request, decodedPayload));
    return;
  }
  editor.replaceChildren(buildRawEditorCard(pendingId, base64Value || ''));
}

async function releasePending(pendingId) {
  try {
    await fetchJson('/api/pending/' + pendingId + '/forward', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{}'
    });
    setStatus('success', `Pending payload ${pendingId} forwarded`);
  } catch (error) {
    setStatus('error', error.message);
  }
}

async function submitEdited(pendingId) {
  const content = document.getElementById('payload-editor').value;
  try {
    await fetchJson('/api/pending/' + pendingId + '/forward', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ encoding: 'utf8', content })
    });
    setStatus('success', `Pending payload ${pendingId} forwarded with edits`);
  } catch (error) {
    setStatus('error', error.message);
  }
}

async function submitStructuredHttp(pendingId) {
  try {
    await fetchJson('/api/pending/' + pendingId + '/forward', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        http: {
          method: document.getElementById('http-method').value,
          path: document.getElementById('http-path').value,
          query: document.getElementById('http-query').value,
          version: document.getElementById('http-version').value,
          headersText: document.getElementById('http-headers').value,
          bodyText: document.getElementById('http-body').value
        }
      })
    });
    setStatus('success', `Pending HTTP payload ${pendingId} forwarded with structured edits`);
  } catch (error) {
    setStatus('error', error.message);
  }
}

async function replayEvent(sessionId, eventId, destination) {
  try {
    const result = await fetchJson('/api/replay', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionId, eventId, destination })
    });
    setStatus('success', `Replay ${destination} completed: sent ${result.bytesSent} bytes, received ${result.bytesReceived ?? 0} bytes from ${result.target}`);
  } catch (error) {
    setStatus('error', error.message);
  }
}

async function replayPayload(routeId, base64, destination) {
  try {
    const result = await fetchJson('/api/replay', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ routeId, base64, destination })
    });
    setStatus('success', `Replay ${destination} completed: sent ${result.bytesSent} bytes, received ${result.bytesReceived ?? 0} bytes from ${result.target}`);
  } catch (error) {
    setStatus('error', error.message);
  }
}

async function selectExchange(index) {
  patchState({
    activeExchangeIndex: index,
    diffMode: false
  });
  const activeSession = getState('activeSession');
  if (activeSession) {
    await loadSessionDetails(activeSession);
  }
}

async function copyText(text) {
  if (!text) return;
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
    } else {
      const helper = document.createElement('textarea');
      helper.value = text;
      helper.setAttribute('readonly', 'true');
      helper.style.position = 'absolute';
      helper.style.left = '-9999px';
      document.body.appendChild(helper);
      helper.select();
      document.execCommand('copy');
      document.body.removeChild(helper);
    }
    setStatus('success', 'Copied to clipboard');
  } catch (error) {
    setStatus('error', 'Unable to copy');
  }
}

function buildHttpEditorCard(pendingId, request, decodedPayload) {
  const card = document.createElement('section');
  card.className = 'editor-card';

  const title = document.createElement('h3');
  title.textContent = 'Edit pending HTTP payload';

  const grid = document.createElement('div');
  grid.className = 'editor-grid';

  const requestGrid = document.createElement('div');
  requestGrid.className = 'request-grid';
  requestGrid.append(
    buildEditorInput('http-method', request.method || '', 'Method'),
    buildEditorInput('http-path', request.path || '', 'Path'),
    buildEditorInput('http-query', request.query || '', 'Query'),
    buildEditorInput('http-version', request.version || 'HTTP/1.1', 'Version')
  );

  const headers = document.createElement('textarea');
  headers.id = 'http-headers';
  headers.rows = 8;
  headers.placeholder = 'Headers';
  headers.value = decodedPayload.headersText || '';

  const body = document.createElement('textarea');
  body.id = 'http-body';
  body.rows = 10;
  body.placeholder = 'Body';
  body.value = decodedPayload.bodyText || '';

  const actions = document.createElement('div');
  actions.className = 'actions editor-actions';
  actions.appendChild(buildEditorActionButton('submit-structured-http', pendingId, 'Forward edited HTTP'));

  grid.append(requestGrid, headers, body, actions);
  card.append(title, grid);
  return card;
}

function buildRawEditorCard(pendingId, base64Value) {
  const card = document.createElement('section');
  card.className = 'editor-card';

  const title = document.createElement('h3');
  title.textContent = 'Edit pending payload';

  const grid = document.createElement('div');
  grid.className = 'editor-grid';

  const textarea = document.createElement('textarea');
  textarea.id = 'payload-editor';
  textarea.rows = 10;
  textarea.value = atob(base64Value || '');

  const actions = document.createElement('div');
  actions.className = 'actions editor-actions';
  actions.appendChild(buildEditorActionButton('submit-edited', pendingId, 'Forward edited'));

  grid.append(textarea, actions);
  card.append(title, grid);
  return card;
}

function buildEditorInput(id, value, placeholder) {
  const input = document.createElement('input');
  input.id = id;
  input.value = value;
  input.placeholder = placeholder;
  return input;
}

function buildEditorActionButton(action, pendingId, label) {
  const button = document.createElement('button');
  button.className = 'primary action-edit';
  button.dataset.action = action;
  button.dataset.pendingId = pendingId;
  button.textContent = label;
  return button;
}

function buildPayloadActionButton(className, action, dataset, label) {
  const button = document.createElement('button');
  button.className = className;
  button.dataset.action = action;
  for (const [key, value] of Object.entries(dataset)) {
    button.dataset[key] = value;
  }
  button.textContent = label;
  return button;
}

function buildHeaderCell(text) {
  const cell = document.createElement('td');
  cell.textContent = text;
  return cell;
}
