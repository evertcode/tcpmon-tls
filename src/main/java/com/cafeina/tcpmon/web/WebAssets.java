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
                      --canvas: #f4f5f7;
                      --surface: #ffffff;
                      --surface-2: #f8fafc;
                      --surface-3: #eef2f7;
                      --text: #18212f;
                      --text-muted: #5f6b7a;
                      --border: rgba(24, 33, 47, 0.12);
                      --border-strong: rgba(24, 33, 47, 0.2);
                      --accent: #0f6cbd;
                      --accent-soft: rgba(15, 108, 189, 0.1);
                      --route: #106c5a;
                      --ok: #13795b;
                      --warn: #a15c07;
                      --danger: #b42318;
                      --mono: "IBM Plex Mono", "SFMono-Regular", Consolas, monospace;
                      --sans: "Segoe UI", "Helvetica Neue", sans-serif;
                      --shadow: 0 10px 26px rgba(24, 33, 47, 0.06);
                    }
                    * { box-sizing: border-box; }
                    html { background: var(--canvas); }
                    body {
                      margin: 0;
                      min-height: 100vh;
                      background: var(--canvas);
                      color: var(--text);
                      font-family: var(--sans);
                    }
                    .app {
                      display: grid;
                      grid-template-rows: auto 1fr;
                      min-height: 100vh;
                    }
                    .topbar {
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      gap: 16px;
                      padding: 12px 16px;
                      background: var(--surface);
                      border-bottom: 1px solid var(--border);
                    }
                    .topbar-title {
                      display: flex;
                      flex-direction: column;
                      gap: 2px;
                    }
                    .topbar-title strong {
                      font-size: 16px;
                    }
                    .topbar-meta {
                      display: flex;
                      gap: 10px;
                      flex-wrap: wrap;
                    }
                    .metric {
                      padding: 6px 10px;
                      border: 1px solid var(--border);
                      border-radius: 999px;
                      background: var(--surface-2);
                      font-size: 12px;
                      color: var(--text-muted);
                    }
                    .layout {
                      display: grid;
                      grid-template-columns: 360px minmax(0, 1fr);
                      min-height: 0;
                    }
                    .sidebar {
                      background: var(--surface);
                      border-right: 1px solid var(--border);
                      display: grid;
                      grid-template-rows: auto auto 1fr;
                      min-height: 0;
                    }
                    .sidebar-section {
                      padding: 12px;
                      border-bottom: 1px solid var(--border);
                    }
                    .sidebar h2,
                    .content h2,
                    .content h3 {
                      margin: 0;
                      font-size: 14px;
                    }
                    .toolbar {
                      display: grid;
                      gap: 8px;
                    }
                    .toolbar-row {
                      display: grid;
                      grid-template-columns: 1fr 1fr auto;
                      gap: 8px;
                    }
                    input,
                    select,
                    textarea {
                      width: 100%;
                      border: 1px solid var(--border);
                      border-radius: 8px;
                      background: var(--surface);
                      color: var(--text);
                      font: inherit;
                      padding: 9px 10px;
                    }
                    input:focus,
                    select:focus,
                    textarea:focus {
                      outline: 2px solid rgba(15, 108, 189, 0.14);
                      border-color: rgba(15, 108, 189, 0.45);
                    }
                    button {
                      border: 1px solid transparent;
                      border-radius: 8px;
                      padding: 9px 12px;
                      font: inherit;
                      font-weight: 600;
                      cursor: pointer;
                    }
                    button.primary {
                      background: var(--accent);
                      color: white;
                    }
                    button.secondary {
                      background: var(--surface-2);
                      border-color: var(--border);
                      color: var(--text);
                    }
                    button.ghost {
                      background: transparent;
                      border-color: var(--border);
                      color: var(--text);
                    }
                    .session-list {
                      overflow: auto;
                      padding: 8px;
                    }
                    .session-row {
                      border: 1px solid var(--border);
                      border-radius: 10px;
                      padding: 10px;
                      margin-bottom: 8px;
                      background: var(--surface);
                      cursor: pointer;
                    }
                    .session-row:hover {
                      border-color: var(--border-strong);
                      background: var(--surface-2);
                    }
                    .session-row.active {
                      border-color: rgba(15, 108, 189, 0.45);
                      background: var(--accent-soft);
                    }
                    .row-top,
                    .row-bottom {
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      gap: 8px;
                    }
                    .row-top {
                      margin-bottom: 6px;
                    }
                    .session-id {
                      font: 12px var(--mono);
                    }
                    .session-path {
                      font: 11px var(--mono);
                      color: var(--text-muted);
                      white-space: nowrap;
                      overflow: hidden;
                      text-overflow: ellipsis;
                    }
                    .pill {
                      display: inline-flex;
                      align-items: center;
                      border-radius: 999px;
                      padding: 3px 8px;
                      font-size: 11px;
                      line-height: 1;
                      border: 1px solid transparent;
                      white-space: nowrap;
                    }
                    .pill.route {
                      background: rgba(16, 108, 90, 0.08);
                      color: var(--route);
                      border-color: rgba(16, 108, 90, 0.18);
                    }
                    .pill.open {
                      background: rgba(19, 121, 91, 0.08);
                      color: var(--ok);
                    }
                    .pill.closed {
                      background: rgba(24, 33, 47, 0.06);
                      color: var(--text-muted);
                    }
                    .pill.error {
                      background: rgba(180, 35, 24, 0.08);
                      color: var(--danger);
                    }
                    .content {
                      display: grid;
                      grid-template-rows: auto auto auto 1fr auto;
                      gap: 12px;
                      padding: 12px;
                      min-height: 0;
                    }
                    .banner {
                      padding: 10px 12px;
                      border-radius: 8px;
                      border: 1px solid var(--border);
                      background: var(--surface);
                      font-size: 13px;
                    }
                    .banner.success {
                      color: var(--ok);
                      border-color: rgba(19, 121, 91, 0.2);
                      background: rgba(19, 121, 91, 0.06);
                    }
                    .banner.error {
                      color: var(--danger);
                      border-color: rgba(180, 35, 24, 0.2);
                      background: rgba(180, 35, 24, 0.06);
                    }
                    .header-card,
                    .timeline-card,
                    .payload-card,
                    .events-card,
                    .editor-card {
                      background: var(--surface);
                      border: 1px solid var(--border);
                      border-radius: 12px;
                      box-shadow: var(--shadow);
                    }
                    .header-card,
                    .timeline-card,
                    .events-card,
                    .editor-card {
                      padding: 12px;
                    }
                    .header-grid {
                      display: grid;
                      grid-template-columns: 1.4fr 1fr;
                      gap: 12px;
                    }
                    .session-headline {
                      display: flex;
                      justify-content: space-between;
                      align-items: start;
                      gap: 12px;
                    }
                    .session-headline strong {
                      display: block;
                      font-size: 15px;
                    }
                    .session-headline code,
                    .mono {
                      font-family: var(--mono);
                    }
                    .session-meta-grid {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 8px 16px;
                      margin-top: 10px;
                      font-size: 12px;
                    }
                    .label {
                      display: block;
                      margin-bottom: 4px;
                      color: var(--text-muted);
                      font-size: 11px;
                      text-transform: uppercase;
                      letter-spacing: 0.04em;
                    }
                    .tls-box {
                      background: var(--surface-2);
                      border: 1px solid var(--border);
                      border-radius: 10px;
                      padding: 10px;
                    }
                    .timeline-list {
                      display: flex;
                      gap: 8px;
                      overflow: auto;
                    }
                    .timeline-item {
                      min-width: 220px;
                      padding: 10px;
                      border: 1px solid var(--border);
                      border-radius: 10px;
                      background: var(--surface-2);
                      cursor: pointer;
                      text-align: left;
                    }
                    .timeline-item.active {
                      border-color: rgba(15, 108, 189, 0.45);
                      background: var(--accent-soft);
                    }
                    .timeline-item strong {
                      display: block;
                      margin-bottom: 4px;
                    }
                    .payload-grid {
                      display: grid;
                      grid-template-columns: 1fr 1fr;
                      gap: 12px;
                      min-height: 0;
                    }
                    .payload-card {
                      display: grid;
                      grid-template-rows: auto auto auto 1fr;
                      min-height: 360px;
                    }
                    .payload-header {
                      padding: 12px 12px 0;
                      display: flex;
                      justify-content: space-between;
                      align-items: start;
                      gap: 12px;
                    }
                    .payload-section {
                      padding: 0 12px 12px;
                    }
                    .payload-body {
                      padding: 0 12px 12px;
                      min-height: 0;
                    }
                    pre {
                      margin: 0;
                      white-space: pre-wrap;
                      overflow-wrap: anywhere;
                      font: 12px/1.5 var(--mono);
                      color: var(--text);
                      background: var(--surface-2);
                      border: 1px solid var(--border);
                      border-radius: 8px;
                      padding: 10px;
                    }
                    .scroll {
                      max-height: 100%;
                      overflow: auto;
                    }
                    .muted {
                      color: var(--text-muted);
                      font-size: 12px;
                    }
                    .events-list {
                      display: grid;
                      gap: 8px;
                      max-height: 320px;
                      overflow: auto;
                    }
                    .event-row {
                      border: 1px solid var(--border);
                      border-radius: 10px;
                      padding: 10px;
                      background: var(--surface-2);
                    }
                    .event-top {
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      gap: 8px;
                      margin-bottom: 6px;
                    }
                    .actions {
                      display: flex;
                      flex-wrap: wrap;
                      gap: 8px;
                      margin-top: 10px;
                    }
                    .editor-grid {
                      display: grid;
                      gap: 8px;
                      margin-top: 10px;
                    }
                    .request-grid {
                      display: grid;
                      grid-template-columns: 120px 1fr 200px 130px;
                      gap: 8px;
                    }
                    .empty {
                      display: grid;
                      place-items: center;
                      min-height: 220px;
                      color: var(--text-muted);
                      border: 1px dashed var(--border);
                      border-radius: 12px;
                      background: var(--surface);
                    }
                    @media (max-width: 1180px) {
                      .layout,
                      .header-grid,
                      .payload-grid {
                        grid-template-columns: 1fr;
                      }
                      .layout {
                        grid-template-rows: auto 1fr;
                      }
                    }
                    @media (max-width: 760px) {
                      .toolbar-row,
                      .request-grid,
                      .session-meta-grid {
                        grid-template-columns: 1fr;
                      }
                    }
                  </style>
                </head>
                <body>
                  <div class="app">
                    <header class="topbar">
                      <div class="topbar-title">
                        <strong>tcpmon-tls control plane</strong>
                        <span class="muted">Sessions, exchanges, replay and pending edits</span>
                      </div>
                      <div id="topbar-metrics" class="topbar-meta"></div>
                    </header>

                    <div class="layout">
                      <aside class="sidebar">
                        <div class="sidebar-section">
                          <h2>Sessions</h2>
                        </div>
                        <div class="sidebar-section">
                          <div class="toolbar">
                            <input id="session-search" type="search" placeholder="Search session, route or host" oninput="renderSessionList()">
                            <div class="toolbar-row">
                              <select id="route-filter" onchange="renderSessionList()">
                                <option value="">All routes</option>
                              </select>
                              <select id="status-filter" onchange="renderSessionList()">
                                <option value="">All statuses</option>
                                <option value="OPEN">Open</option>
                                <option value="CLOSED">Closed</option>
                                <option value="ERROR">Error</option>
                              </select>
                              <button class="secondary" onclick="refreshSessions(true)">Refresh</button>
                            </div>
                          </div>
                        </div>
                        <div id="sessions" class="session-list"></div>
                      </aside>

                      <main class="content">
                        <div id="status-banner"></div>
                        <div id="session-header"></div>
                        <div id="exchange-timeline"></div>
                        <div id="payloads"></div>
                        <div id="events-and-editor"></div>
                      </main>
                    </div>
                  </div>

                  <script>
                    let allSessions = [];
                    let activeSession = null;
                    let activeExchangeIndex = 0;
                    let statusMessage = null;

                    async function fetchJson(url, options) {
                      const response = await fetch(url, options);
                      const data = await response.json();
                      if (!response.ok) {
                        const error = new Error(data.error || 'Request failed');
                        error.payload = data;
                        throw error;
                      }
                      return data;
                    }

                    async function refreshSessions(preserveSelection = true) {
                      const data = await fetchJson('/api/sessions');
                      allSessions = Array.isArray(data.sessions) ? data.sessions : [];
                      updateMetrics();
                      updateRouteFilter();
                      renderSessionList();

                      if (!allSessions.length) {
                        activeSession = null;
                        renderEmptyState('No sessions yet.');
                        return;
                      }

                      if (preserveSelection && activeSession && allSessions.some(session => session.sessionId === activeSession)) {
                        return;
                      }

                      if (!activeSession || !allSessions.some(session => session.sessionId === activeSession)) {
                        await loadSession(allSessions[0].sessionId);
                      }
                    }

                    function updateMetrics() {
                      const open = allSessions.filter(session => String(session.status || '').toUpperCase() === 'OPEN').length;
                      const pending = allSessions.reduce((sum, session) => sum + Number(session.pendingCount || 0), 0);
                      const routes = new Set(allSessions.map(session => session.routeId || 'default')).size;
                      document.getElementById('topbar-metrics').innerHTML = `
                        <span class="metric">${allSessions.length} sessions</span>
                        <span class="metric">${routes} routes</span>
                        <span class="metric">${open} open</span>
                        <span class="metric">${pending} pending</span>
                      `;
                    }

                    function updateRouteFilter() {
                      const select = document.getElementById('route-filter');
                      const current = select.value;
                      const routes = [...new Set(allSessions.map(session => session.routeId || 'default'))].sort();
                      select.innerHTML = '<option value="">All routes</option>' +
                        routes.map(route => `<option value="${escapeAttr(route)}">${escapeHtml(route)}</option>`).join('');
                      if (routes.includes(current)) {
                        select.value = current;
                      }
                    }

                    function filteredSessions() {
                      const query = document.getElementById('session-search').value.trim().toLowerCase();
                      const route = document.getElementById('route-filter').value;
                      const status = document.getElementById('status-filter').value;
                      return allSessions.filter(session => {
                        const routeId = session.routeId || 'default';
                        if (route && routeId !== route) return false;
                        if (status && String(session.status || '').toUpperCase() !== status) return false;
                        if (!query) return true;
                        const haystack = [
                          session.sessionId,
                          routeId,
                          session.clientAddress,
                          session.listenerAddress,
                          session.targetAddress,
                          session.status
                        ].join(' ').toLowerCase();
                        return haystack.includes(query);
                      });
                    }

                    function renderSessionList() {
                      const sessions = filteredSessions();
                      const container = document.getElementById('sessions');
                      if (!sessions.length) {
                        container.innerHTML = '<div class="empty">No matching sessions.</div>';
                        return;
                      }
                      container.innerHTML = sessions.map(session => {
                        const statusClass = String(session.status || 'closed').toLowerCase();
                        return `<div class="session-row${session.sessionId === activeSession ? ' active' : ''}" onclick="loadSession('${session.sessionId}')">
                          <div class="row-top">
                            <span class="session-id">${escapeHtml(session.sessionId)}</span>
                            <span class="pill route">${escapeHtml(session.routeId || 'default')}</span>
                          </div>
                          <div class="row-bottom">
                            <span class="pill ${escapeHtml(statusClass)}">${escapeHtml(session.status || 'UNKNOWN')}</span>
                            <span class="muted">${escapeHtml(session.eventCount || 0)} events / ${escapeHtml(session.pendingCount || 0)} pending</span>
                          </div>
                          <div class="session-path">${escapeHtml(session.clientAddress || '')}</div>
                          <div class="session-path">${escapeHtml(session.targetAddress || '')}</div>
                        </div>`;
                      }).join('');
                    }

                    async function loadSession(sessionId) {
                      activeSession = sessionId;
                      renderSessionList();
                      const data = await fetchJson('/api/sessions/' + sessionId);
                      const exchanges = data.exchanges || [];
                      if (activeExchangeIndex >= exchanges.length) activeExchangeIndex = 0;
                      const activeExchange = exchanges[activeExchangeIndex] || {};

                      renderBanner();
                      renderHeader(data);
                      renderTimeline(exchanges);
                      renderPayloads(activeExchange, data);
                      renderEventsAndEditor(data);
                    }

                    function renderBanner() {
                      const el = document.getElementById('status-banner');
                      if (!statusMessage) {
                        el.innerHTML = '';
                        return;
                      }
                      el.innerHTML = `<div class="banner ${statusMessage.type}">${escapeHtml(statusMessage.text)}</div>`;
                    }

                    function renderHeader(data) {
                      document.getElementById('session-header').innerHTML = `
                        <section class="header-card">
                          <div class="header-grid">
                            <div>
                              <div class="session-headline">
                                <div>
                                  <strong>${escapeHtml(data.routeId || 'default')} / ${escapeHtml(data.sessionId || '')}</strong>
                                  <div class="muted mono">${escapeHtml(data.clientAddress || '')} -> ${escapeHtml(data.targetAddress || '')}</div>
                                </div>
                                <span class="pill ${String(data.status || 'closed').toLowerCase()}">${escapeHtml(data.status || 'UNKNOWN')}</span>
                              </div>
                              <div class="session-meta-grid">
                                <div><span class="label">Client</span><span class="mono">${escapeHtml(data.clientAddress || '')}</span></div>
                                <div><span class="label">Listener</span><span class="mono">${escapeHtml(data.listenerAddress || '')}</span></div>
                                <div><span class="label">Target</span><span class="mono">${escapeHtml(data.targetAddress || '')}</span></div>
                                <div><span class="label">Counts</span>${escapeHtml((data.events || []).length)} events / ${escapeHtml((data.pendingPayloads || []).length)} pending</div>
                              </div>
                            </div>
                            <div class="tls-box">
                              <span class="label">TLS</span>
                              <pre>${escapeHtml(JSON.stringify({ inboundTls: data.inboundTls, outboundTls: data.outboundTls }, null, 2))}</pre>
                            </div>
                          </div>
                        </section>
                      `;
                    }

                    function renderTimeline(exchanges) {
                      const el = document.getElementById('exchange-timeline');
                      if (!exchanges.length) {
                        el.innerHTML = '';
                        return;
                      }
                      el.innerHTML = `
                        <section class="timeline-card">
                          <h3>Exchanges</h3>
                          <div class="timeline-list">
                            ${exchanges.map(exchange => `
                              <button class="timeline-item${exchange.index === activeExchangeIndex ? ' active' : ''}" onclick="selectExchange(${exchange.index})">
                                <strong>Exchange ${exchange.index + 1}</strong>
                                <div class="muted">${escapeHtml(exchange.request?.decoded?.startLine || 'No request')}</div>
                                <div class="muted">${escapeHtml(exchange.response?.decoded?.startLine || 'No response')}</div>
                              </button>
                            `).join('')}
                          </div>
                        </section>
                      `;
                    }

                    function renderPayloads(activeExchange, data) {
                      const request = activeExchange.request || data.latestRequest;
                      const response = activeExchange.response || data.latestResponse;
                      document.getElementById('payloads').innerHTML = `
                        <section class="payload-grid">
                          ${renderPayloadCard('Request', request, 'CLIENT_TO_TARGET')}
                          ${renderPayloadCard('Response', response, 'TARGET_TO_CLIENT')}
                        </section>
                      `;
                    }

                    function renderPayloadCard(title, payload, expectedDirection) {
                      if (!payload) {
                        return `
                          <article class="payload-card">
                            <div class="payload-header">
                              <h3>${title}</h3>
                              <span class="pill route">${expectedDirection}</span>
                            </div>
                            <div class="payload-section muted">No ${title.toLowerCase()} payload captured yet.</div>
                          </article>
                        `;
                      }
                      const decoded = payload.decoded || {};
                      const headersText = decoded.isHttp ? (decoded.headersText || '') : 'Non-HTTP payload';
                      const bodyText = decoded.bodyText || '';
                      const chunkText = payload.chunkCount ? ` / ${payload.chunkCount} chunks` : '';
                      return `
                        <article class="payload-card">
                          <div class="payload-header">
                            <div>
                              <h3>${title}</h3>
                              <div class="muted">${escapeHtml(payload.timestamp || '')} / ${escapeHtml(payload.size || 0)} bytes${escapeHtml(chunkText)}</div>
                            </div>
                            <span class="pill route">${escapeHtml(payload.direction || expectedDirection)}</span>
                          </div>
                          <div class="payload-section">
                            <span class="label">Start line</span>
                            <pre>${escapeHtml(decoded.startLine || 'No HTTP start line')}</pre>
                          </div>
                          <div class="payload-section">
                            <span class="label">Headers</span>
                            <pre>${escapeHtml(headersText)}</pre>
                          </div>
                          <div class="payload-body">
                            <span class="label">Body</span>
                            <pre class="scroll">${escapeHtml(bodyText)}</pre>
                          </div>
                        </article>
                      `;
                    }

                    function renderEventsAndEditor(data) {
                      const events = data.events || [];
                      document.getElementById('events-and-editor').innerHTML = `
                        <section class="events-card">
                          <h3>Events</h3>
                          <div class="events-list">
                            ${events.length ? events.map(event => renderEventRow(data.sessionId, event)).join('') : '<div class="empty">No events yet.</div>'}
                          </div>
                        </section>
                        <div id="editor"></div>
                      `;
                    }

                    function renderEventRow(sessionId, event) {
                      const replay = event.type === 'PAYLOAD' && event.direction === 'CLIENT_TO_TARGET'
                        ? `<div class="actions">
                             <button class="primary" onclick="replayEvent('${sessionId}','${event.eventId}','listener')">Recapture request</button>
                             <button class="ghost" onclick="replayEvent('${sessionId}','${event.eventId}','target')">Send direct</button>
                           </div>` : '';
                      const pending = event.pendingId
                        ? `<div class="actions">
                             <button class="secondary" onclick="releasePending('${event.pendingId}')">Forward original</button>
                             <button class="primary" onclick='showEdit("${event.pendingId}", ${JSON.stringify(event.decoded || null)}, "${event.details?.base64 || ''}")'>Edit and forward</button>
                           </div>` : '';
                      return `
                        <div class="event-row">
                          <div class="event-top">
                            <div><strong>${escapeHtml(event.type)}</strong> <span class="muted">${escapeHtml(event.direction || '')}</span></div>
                            <div class="muted">${escapeHtml(event.timestamp || '')}</div>
                          </div>
                          <div class="muted">${escapeHtml(event.size || 0)} bytes</div>
                          <pre>${escapeHtml(event.previewText || '')}\n${escapeHtml(event.previewHex || '')}</pre>
                          ${replay}
                          ${pending}
                        </div>
                      `;
                    }

                    function showEdit(pendingId, decodedPayload, base64Value) {
                      const editor = document.getElementById('editor');
                      if (decodedPayload?.isHttp) {
                        const request = decodedPayload.request || {};
                        editor.innerHTML = `
                          <section class="editor-card">
                            <h3>Edit pending HTTP payload</h3>
                            <div class="editor-grid">
                              <div class="request-grid">
                                <input id="http-method" value="${escapeAttr(request.method || '')}" placeholder="Method">
                                <input id="http-path" value="${escapeAttr(request.path || '')}" placeholder="Path">
                                <input id="http-query" value="${escapeAttr(request.query || '')}" placeholder="Query">
                                <input id="http-version" value="${escapeAttr(request.version || 'HTTP/1.1')}" placeholder="Version">
                              </div>
                              <textarea id="http-headers" rows="8" placeholder="Headers">${escapeHtml(decodedPayload.headersText || '')}</textarea>
                              <textarea id="http-body" rows="10" placeholder="Body">${escapeHtml(decodedPayload.bodyText || '')}</textarea>
                              <div class="actions">
                                <button class="primary" onclick="submitStructuredHttp('${pendingId}')">Forward edited HTTP</button>
                              </div>
                            </div>
                          </section>
                        `;
                        return;
                      }
                      editor.innerHTML = `
                        <section class="editor-card">
                          <h3>Edit pending payload</h3>
                          <div class="editor-grid">
                            <textarea id="payload-editor" rows="10">${escapeHtml(atob(base64Value || ''))}</textarea>
                            <div class="actions">
                              <button class="primary" onclick="submitEdited('${pendingId}')">Forward edited</button>
                            </div>
                          </div>
                        </section>
                      `;
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

                    function selectExchange(index) {
                      activeExchangeIndex = index;
                      if (activeSession) {
                        loadSession(activeSession);
                      }
                    }

                    function setStatus(type, text) {
                      statusMessage = { type, text };
                      renderBanner();
                      if (activeSession) {
                        loadSession(activeSession);
                      }
                    }

                    function renderEmptyState(message) {
                      document.getElementById('status-banner').innerHTML = '';
                      document.getElementById('session-header').innerHTML = `<div class="empty">${escapeHtml(message)}</div>`;
                      document.getElementById('exchange-timeline').innerHTML = '';
                      document.getElementById('payloads').innerHTML = '';
                      document.getElementById('events-and-editor').innerHTML = '';
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

                    refreshSessions(false);
                    setInterval(() => refreshSessions(true), 3000);
                  </script>
                </body>
                </html>
                """;
    }
}
