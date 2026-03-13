package com.cafeina.tcpmon.web;

public final class WebAssets {
    private WebAssets() {
    }

    public static String indexHtml() {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>tcpmon-tls</title>
                  <style>
                    :root {
                      --bg: #07111f;
                      --panel: #0f1c2e;
                      --panel-2: #13253b;
                      --text: #f2f6fa;
                      --muted: #8ea3b9;
                      --accent: #4fd1c5;
                      --warn: #f6ad55;
                    }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      font-family: "IBM Plex Sans", "Segoe UI", sans-serif;
                      color: var(--text);
                      background:
                        radial-gradient(circle at top left, rgba(79,209,197,0.14), transparent 35%),
                        linear-gradient(160deg, #06101d 0%, #0b1524 35%, #132238 100%);
                    }
                    header {
                      padding: 20px 24px;
                      border-bottom: 1px solid rgba(255,255,255,0.08);
                      position: sticky;
                      top: 0;
                      backdrop-filter: blur(10px);
                      background: rgba(7,17,31,0.75);
                    }
                    main {
                      display: grid;
                      grid-template-columns: 360px 1fr;
                      gap: 16px;
                      padding: 16px;
                      min-height: calc(100vh - 78px);
                    }
                    .panel {
                      background: rgba(15, 28, 46, 0.92);
                      border: 1px solid rgba(255,255,255,0.06);
                      border-radius: 18px;
                      padding: 16px;
                      box-shadow: 0 18px 48px rgba(0,0,0,0.22);
                    }
                    .session {
                      border: 1px solid rgba(255,255,255,0.06);
                      border-radius: 12px;
                      padding: 12px;
                      margin-bottom: 10px;
                      cursor: pointer;
                    }
                    .session:hover, .session.active { border-color: var(--accent); }
                    .flow {
                      display: grid;
                      grid-template-columns: 1fr 1fr;
                      gap: 16px;
                      margin-top: 16px;
                    }
                    .exchange-list {
                      display: grid;
                      gap: 10px;
                      margin-top: 16px;
                    }
                    .exchange-item {
                      padding: 12px;
                      border-radius: 12px;
                      border: 1px solid rgba(255,255,255,0.08);
                      cursor: pointer;
                    }
                    .exchange-item.active { border-color: var(--accent); }
                    .tag {
                      display: inline-block;
                      font-size: 11px;
                      padding: 4px 8px;
                      border-radius: 999px;
                      background: rgba(79,209,197,0.15);
                      color: var(--accent);
                      margin-bottom: 10px;
                    }
                    .muted { color: var(--muted); font-size: 12px; }
                    .section-title {
                      margin: 0 0 10px;
                      font-size: 14px;
                      letter-spacing: 0.08em;
                      text-transform: uppercase;
                      color: var(--muted);
                    }
                    .stack { display: grid; gap: 12px; }
                    .body-box {
                      min-height: 180px;
                      max-height: 420px;
                      overflow: auto;
                    }
                    .editor-grid {
                      display: grid;
                      gap: 10px;
                    }
                    .editor-grid input, .editor-grid textarea {
                      width: 100%;
                      background: #09131f;
                      color: white;
                      border: 1px solid rgba(255,255,255,0.1);
                      border-radius: 12px;
                      padding: 12px;
                    }
                    .meta-grid {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 12px;
                    }
                    pre {
                      white-space: pre-wrap;
                      overflow-wrap: anywhere;
                      background: rgba(0,0,0,0.2);
                      border-radius: 12px;
                      padding: 12px;
                    }
                    textarea {
                      width: 100%;
                      min-height: 160px;
                      background: #09131f;
                      color: white;
                      border: 1px solid rgba(255,255,255,0.1);
                      border-radius: 12px;
                      padding: 12px;
                    }
                    button {
                      background: var(--accent);
                      color: #041318;
                      border: none;
                      border-radius: 999px;
                      padding: 10px 16px;
                      font-weight: 700;
                      cursor: pointer;
                      margin-right: 8px;
                    }
                    .warn { color: var(--warn); }
                    @media (max-width: 900px) {
                      main { grid-template-columns: 1fr; }
                      .flow { grid-template-columns: 1fr; }
                    }
                  </style>
                </head>
                <body>
                  <header>
                    <strong>tcpmon-tls</strong>
                    <span class="muted"> local control plane for sessions, interception and replay</span>
                  </header>
                  <main>
                    <section class="panel">
                      <h3>Sessions</h3>
                      <div id="sessions"></div>
                    </section>
                    <section class="panel">
                      <h3>Details</h3>
                      <div id="details" class="muted">Select a session.</div>
                    </section>
                  </main>
                  <script>
                    let activeSession = null;
                    let activeExchangeIndex = 0;

                    async function fetchJson(url, options) {
                      const response = await fetch(url, options);
                      return response.json();
                    }

                    async function refreshSessions() {
                      const data = await fetchJson('/api/sessions');
                      const list = document.getElementById('sessions');
                      list.innerHTML = '';
                      for (const session of data.sessions) {
                        const card = document.createElement('div');
                        card.className = 'session' + (session.sessionId === activeSession ? ' active' : '');
                        card.innerHTML = `<strong>${session.sessionId}</strong>
                          <div class="muted">${session.clientAddress} -> ${session.targetAddress}</div>
                          <div class="muted">${session.status} | events: ${session.eventCount} | pending: ${session.pendingCount}</div>`;
                        card.onclick = () => loadSession(session.sessionId);
                        list.appendChild(card);
                      }
                    }

                    async function loadSession(sessionId) {
                      activeSession = sessionId;
                      await refreshSessions();
                      const data = await fetchJson('/api/sessions/' + sessionId);
                      const details = document.getElementById('details');
                      const exchanges = data.exchanges || [];
                      if (activeExchangeIndex >= exchanges.length) activeExchangeIndex = 0;
                      const activeExchange = exchanges[activeExchangeIndex] || {};
                      const events = data.events.map(event => {
                        const replay = event.type === 'PAYLOAD' && event.direction === 'CLIENT_TO_TARGET'
                          ? `<button onclick="replayEvent('${sessionId}','${event.eventId}', 'listener')">Recapture request</button>
                             <button onclick="replayEvent('${sessionId}','${event.eventId}', 'target')">Send direct</button>`
                          : '';
                        const pending = event.pendingId
                          ? `<button onclick="releasePending('${event.pendingId}')">Forward original</button>
                             <button onclick='showEdit("${event.pendingId}", ${JSON.stringify(event.decoded || null)}, "${event.details?.base64 || ''}")'>Edit/Forward</button>`
                          : '';
                        return `<div class="panel" style="margin-bottom:12px">
                          <div><strong>${event.type}</strong> ${event.direction || ''}</div>
                          <div class="muted">${event.timestamp || ''} size=${event.size || 0}</div>
                          <pre>${event.previewText || ''}\n${event.previewHex || ''}</pre>
                          ${replay}${pending}
                        </div>`;
                      }).join('');
                      const exchangeList = renderExchangeList(exchanges);
                      const requestView = renderPayloadCard('Request', activeExchange.request || data.latestRequest, 'CLIENT_TO_TARGET');
                      const responseView = renderPayloadCard('Response', activeExchange.response || data.latestResponse, 'TARGET_TO_CLIENT');
                      details.innerHTML = `
                        <div class="meta-grid">
                          <div>
                            <div class="muted">${data.clientAddress} -> ${data.targetAddress}</div>
                            <div class="muted">status=${data.status}</div>
                          </div>
                          <pre>${JSON.stringify({ inboundTls: data.inboundTls, outboundTls: data.outboundTls }, null, 2)}</pre>
                        </div>
                        <div class="flow">
                          ${requestView}
                          ${responseView}
                        </div>
                        ${exchangeList}
                        <h4>Events</h4>
                        ${events || '<div class="muted">No events yet.</div>'}
                        <div id="editor"></div>`;
                    }

                    function renderExchangeList(exchanges) {
                      if (!exchanges.length || exchanges.length === 1) {
                        return '';
                      }
                      const items = exchanges.map(exchange => {
                        const requestLine = exchange.request?.decoded?.startLine || 'No request';
                        const responseLine = exchange.response?.decoded?.startLine || 'No response';
                        const activeClass = exchange.index === activeExchangeIndex ? ' active' : '';
                        return `<div class="exchange-item${activeClass}" onclick="selectExchange(${exchange.index})">
                          <strong>Exchange ${exchange.index + 1}</strong>
                          <div class="muted">${escapeHtml(requestLine)}</div>
                          <div class="muted">${escapeHtml(responseLine)}</div>
                        </div>`;
                      }).join('');
                      return `<div>
                        <div class="section-title">Exchanges</div>
                        <div class="exchange-list">${items}</div>
                      </div>`;
                    }

                    function selectExchange(index) {
                      activeExchangeIndex = index;
                      if (activeSession) loadSession(activeSession);
                    }

                    function renderPayloadCard(title, event, expectedDirection) {
                      if (!event) {
                        return `<div class="panel"><h3>${title}</h3><div class="muted">No ${title.toLowerCase()} payload captured yet.</div></div>`;
                      }
                      const decoded = event.decoded || {};
                      const headersText = decoded.isHttp ? (decoded.headersText || '') : 'Non-HTTP payload';
                      const bodyText = decoded.bodyText || '';
                      const chunkCount = event.chunkCount ? ` | chunks=${event.chunkCount}` : '';
                      const meta = `
                        <div class="tag">${event.direction || expectedDirection}</div>
                        <div class="muted">${event.timestamp || ''} | ${event.size || 0} bytes${chunkCount}</div>
                        ${decoded.startLine ? `<pre>${escapeHtml(decoded.startLine)}</pre>` : ''}
                      `;
                      return `<div class="panel stack">
                        <div>
                          <h3>${title}</h3>
                          ${meta}
                        </div>
                        <div>
                          <div class="section-title">Headers</div>
                          <pre>${escapeHtml(headersText)}</pre>
                        </div>
                        <div>
                          <div class="section-title">Body</div>
                          <pre class="body-box">${escapeHtml(bodyText)}</pre>
                        </div>
                      </div>`;
                    }

                    function escapeHtml(value) {
                      return String(value ?? '')
                        .replaceAll('&', '&amp;')
                        .replaceAll('<', '&lt;')
                        .replaceAll('>', '&gt;');
                    }

                    function escapeAttr(value) {
                      return String(value ?? '')
                        .replaceAll('&', '&amp;')
                        .replaceAll('"', '&quot;')
                        .replaceAll('<', '&lt;')
                        .replaceAll('>', '&gt;');
                    }

                    async function releasePending(pendingId) {
                      await fetchJson('/api/pending/' + pendingId + '/forward', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
                      if (activeSession) await loadSession(activeSession);
                    }

                    function showEdit(pendingId, decodedPayload, base64Value) {
                      const editor = document.getElementById('editor');
                      if (decodedPayload?.isHttp) {
                        editor.innerHTML = `
                          <h4>Edit pending HTTP payload ${pendingId}</h4>
                          <div class="editor-grid">
                            <label class="muted">Start line</label>
                            <input id="http-start-line" value="${escapeAttr(decodedPayload.startLine || '')}">
                            <label class="muted">Headers</label>
                            <textarea id="http-headers">${escapeHtml(decodedPayload.headersText || '')}</textarea>
                            <label class="muted">Body</label>
                            <textarea id="http-body">${escapeHtml(decodedPayload.bodyText || '')}</textarea>
                            <div style="margin-top:12px">
                              <button onclick="submitStructuredHttp('${pendingId}')">Forward edited HTTP</button>
                            </div>
                          </div>`;
                        return;
                      }
                      const decoded = atob(base64Value || '');
                      editor.innerHTML = `
                        <h4>Edit pending payload ${pendingId}</h4>
                        <p class="muted">Payload is sent as UTF-8 by default. Use hex/base64 manually if needed.</p>
                        <textarea id="payload-editor">${escapeHtml(decoded)}</textarea>
                        <div style="margin-top:12px">
                          <button onclick="submitEdited('${pendingId}')">Forward edited</button>
                        </div>`;
                    }

                    async function submitEdited(pendingId) {
                      const content = document.getElementById('payload-editor').value;
                      await fetchJson('/api/pending/' + pendingId + '/forward', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ encoding: 'utf8', content })
                      });
                      if (activeSession) await loadSession(activeSession);
                    }

                    async function submitStructuredHttp(pendingId) {
                      const startLine = document.getElementById('http-start-line').value;
                      const headersText = document.getElementById('http-headers').value;
                      const bodyText = document.getElementById('http-body').value;
                      await fetchJson('/api/pending/' + pendingId + '/forward', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                          http: {
                            startLine,
                            headersText,
                            bodyText
                          }
                        })
                      });
                      if (activeSession) await loadSession(activeSession);
                    }

                    async function replayEvent(sessionId, eventId, destination) {
                      const result = await fetchJson('/api/replay', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ sessionId, eventId, destination })
                      });
                      alert(JSON.stringify(result, null, 2));
                    }

                    refreshSessions();
                    setInterval(refreshSessions, 3000);
                  </script>
                </body>
                </html>
                """;
    }
}
