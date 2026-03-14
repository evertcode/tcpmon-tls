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
                    .layout {
                      display: grid;
                      grid-template-columns: 280px minmax(0, 1fr);
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
                    .route-list {
                      overflow: auto;
                      padding: 8px;
                    }
                    .route-row {
                      border: 1px solid var(--border);
                      border-radius: 10px;
                      padding: 10px;
                      margin-bottom: 8px;
                      background: var(--surface);
                      cursor: pointer;
                    }
                    .route-row:hover {
                      border-color: var(--border-strong);
                      background: var(--surface-2);
                    }
                    .route-row.active {
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
                    .route-line,
                    .mono {
                      font-family: var(--mono);
                    }
                    .route-line {
                      font-size: 11px;
                      color: var(--text-muted);
                      white-space: nowrap;
                      overflow: hidden;
                      text-overflow: ellipsis;
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
                    .route-card,
                    .table-card,
                    .payload-card,
                    .events-card,
                    .editor-card {
                      background: var(--surface);
                      border: 1px solid var(--border);
                      border-radius: 12px;
                      box-shadow: var(--shadow);
                    }
                    .route-card,
                    .table-card,
                    .events-card,
                    .editor-card {
                      padding: 12px;
                    }
                    .route-title {
                      display: flex;
                      justify-content: space-between;
                      align-items: start;
                      gap: 12px;
                    }
                    .route-title strong {
                      display: block;
                      font-size: 15px;
                    }
                    .route-meta-grid {
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
                    .request-toolbar {
                      display: grid;
                      grid-template-columns: 1fr auto;
                      gap: 8px;
                      margin-bottom: 10px;
                    }
                    table {
                      width: 100%;
                      border-collapse: collapse;
                      font-size: 12px;
                    }
                    th,
                    td {
                      text-align: left;
                      padding: 9px 8px;
                      border-bottom: 1px solid var(--border);
                      vertical-align: top;
                    }
                    th {
                      color: var(--text-muted);
                      font-size: 11px;
                      text-transform: uppercase;
                      letter-spacing: 0.04em;
                    }
                    tr.session-entry {
                      cursor: pointer;
                    }
                    tr.session-entry:hover {
                      background: var(--surface-2);
                    }
                    tr.session-entry.active {
                      background: var(--accent-soft);
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
                      min-height: 180px;
                      color: var(--text-muted);
                      border: 1px dashed var(--border);
                      border-radius: 12px;
                      background: var(--surface);
                    }
                    @media (max-width: 1180px) {
                      .layout,
                      .payload-grid {
                        grid-template-columns: 1fr;
                      }
                    }
                    @media (max-width: 760px) {
                      .request-toolbar,
                      .request-grid,
                      .route-meta-grid {
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
                        <span class="muted">Select route, inspect recorded requests, open one to view request and response.</span>
                      </div>
                    </header>

                    <div class="layout">
                      <aside class="sidebar">
                        <div class="sidebar-section">
                          <h2>Routes</h2>
                        </div>
                        <div class="sidebar-section">
                          <div class="toolbar">
                            <input id="route-search" type="search" placeholder="Search route or target" oninput="renderRouteList()">
                            <button class="secondary" onclick="refreshSessions(true)">Refresh</button>
                          </div>
                        </div>
                        <div id="routes" class="route-list"></div>
                      </aside>

                      <main class="content">
                        <div id="status-banner"></div>
                        <div id="route-header"></div>
                        <div id="request-table"></div>
                        <div id="payloads"></div>
                        <div id="events-and-editor"></div>
                      </main>
                    </div>
                  </div>

                  <script>
                    let allSessions = [];
                    let activeRoute = null;
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
                      renderRouteList();

                      if (!allSessions.length) {
                        activeRoute = null;
                        activeSession = null;
                        renderEmptyState('No sessions yet.');
                        return;
                      }

                      const routes = groupedRoutes();
                      if (!preserveSelection || !activeRoute || !routes.some(route => route.routeId === activeRoute)) {
                        activeRoute = routes[0].routeId;
                      }

                      const routeSessions = sessionsForActiveRoute();
                      if (!preserveSelection || !activeSession || !routeSessions.some(session => session.sessionId === activeSession)) {
                        activeSession = routeSessions[0] ? routeSessions[0].sessionId : null;
                      }

                      renderRouteList();
                      renderBanner();
                      renderRouteHeader();
                      renderRequestTable();
                      if (activeSession) {
                        await loadSessionDetails(activeSession);
                      } else {
                        renderDetailEmpty('No requests for the selected route.');
                      }
                    }

                    function groupedRoutes() {
                      const map = new Map();
                      for (const session of allSessions) {
                        const routeId = session.routeId || 'default';
                        const current = map.get(routeId) || {
                          routeId,
                          sessions: [],
                          targetAddress: session.targetAddress || '',
                          clientAddress: session.clientAddress || '',
                          status: 'CLOSED'
                        };
                        current.sessions.push(session);
                        if (!current.targetAddress && session.targetAddress) current.targetAddress = session.targetAddress;
                        if (!current.clientAddress && session.clientAddress) current.clientAddress = session.clientAddress;
                        if (String(session.status || '').toUpperCase() === 'OPEN') current.status = 'OPEN';
                        if (String(session.status || '').toUpperCase() === 'ERROR') current.status = 'ERROR';
                        map.set(routeId, current);
                      }
                      return [...map.values()].sort((a, b) => a.routeId.localeCompare(b.routeId));
                    }

                    function filteredRoutes() {
                      const query = document.getElementById('route-search').value.trim().toLowerCase();
                      return groupedRoutes().filter(route => {
                        if (!query) return true;
                        return [route.routeId, route.targetAddress, route.clientAddress].join(' ').toLowerCase().includes(query);
                      });
                    }

                    function sessionsForActiveRoute() {
                      return allSessions
                        .filter(session => (session.routeId || 'default') === activeRoute)
                        .sort((a, b) => String(b.startedAt || '').localeCompare(String(a.startedAt || '')));
                    }

                    function renderRouteList() {
                      const routes = filteredRoutes();
                      const container = document.getElementById('routes');
                      if (!routes.length) {
                        container.innerHTML = '<div class="empty">No matching routes.</div>';
                        return;
                      }
                      container.innerHTML = routes.map(route => {
                        const pending = route.sessions.reduce((sum, session) => sum + Number(session.pendingCount || 0), 0);
                        const statusClass = String(route.status || 'closed').toLowerCase();
                        return `<div class="route-row${route.routeId === activeRoute ? ' active' : ''}" onclick="selectRoute('${route.routeId}')">
                          <div class="row-top">
                            <strong>${escapeHtml(route.routeId)}</strong>
                            <span class="pill route">${escapeHtml(route.sessions.length)} req</span>
                          </div>
                          <div class="row-bottom">
                            <span class="pill ${escapeHtml(statusClass)}">${escapeHtml(route.status)}</span>
                            <span class="muted">${escapeHtml(pending)} pending</span>
                          </div>
                          <div class="route-line">${escapeHtml(route.targetAddress || '')}</div>
                        </div>`;
                      }).join('');
                    }

                    function selectRoute(routeId) {
                      activeRoute = routeId;
                      activeSession = null;
                      activeExchangeIndex = 0;
                      const sessions = sessionsForActiveRoute();
                      activeSession = sessions[0] ? sessions[0].sessionId : null;
                      renderRouteList();
                      renderBanner();
                      renderRouteHeader();
                      renderRequestTable();
                      if (activeSession) {
                        loadSessionDetails(activeSession);
                      } else {
                        renderDetailEmpty('No requests for the selected route.');
                      }
                    }

                    function renderBanner() {
                      const el = document.getElementById('status-banner');
                      if (!statusMessage) {
                        el.innerHTML = '';
                        return;
                      }
                      el.innerHTML = `<div class="banner ${statusMessage.type}">${escapeHtml(statusMessage.text)}</div>`;
                    }

                    function renderRouteHeader() {
                      const sessions = sessionsForActiveRoute();
                      if (!activeRoute) {
                        document.getElementById('route-header').innerHTML = '<div class="empty">Select a route.</div>';
                        return;
                      }
                      const first = sessions[0] || {};
                      const open = sessions.filter(session => String(session.status || '').toUpperCase() === 'OPEN').length;
                      const pending = sessions.reduce((sum, session) => sum + Number(session.pendingCount || 0), 0);
                      document.getElementById('route-header').innerHTML = `
                        <section class="route-card">
                          <div class="route-title">
                            <div>
                              <strong>${escapeHtml(activeRoute)}</strong>
                              <div class="muted">Route overview and recorded requests</div>
                            </div>
                            <span class="pill route">${escapeHtml(sessions.length)} requests</span>
                          </div>
                          <div class="route-meta-grid">
                            <div><span class="label">Listener</span><span class="mono">${escapeHtml(first.listenerAddress || 'Unknown')}</span></div>
                            <div><span class="label">Target</span><span class="mono">${escapeHtml(first.targetAddress || 'Unknown')}</span></div>
                            <div><span class="label">Latest client</span><span class="mono">${escapeHtml(first.clientAddress || 'Unknown')}</span></div>
                            <div><span class="label">Selected request</span><span class="mono">${escapeHtml(activeSession || 'None')}</span></div>
                            <div><span class="label">Open</span>${escapeHtml(open)}</div>
                            <div><span class="label">Pending</span>${escapeHtml(pending)}</div>
                          </div>
                        </section>
                      `;
                    }

                    function renderRequestTable() {
                      const sessions = sessionsForActiveRoute();
                      document.getElementById('request-table').innerHTML = `
                        <section class="table-card">
                          <div class="request-toolbar">
                            <input id="request-search" type="search" placeholder="Filter requests in this route" oninput="renderRequestTable()">
                            <button class="secondary" onclick="refreshSessions(true)">Refresh</button>
                          </div>
                          ${renderRequestTableRows(sessions)}
                        </section>
                      `;
                    }

                    function renderRequestTableRows(sessions) {
                      const query = document.getElementById('request-search') ? document.getElementById('request-search').value.trim().toLowerCase() : '';
                      const filtered = sessions.filter(session => {
                        if (!query) return true;
                        return [
                          session.sessionId,
                          session.requestMethod,
                          session.responseStatusCode,
                          session.clientAddress,
                          session.targetAddress,
                          session.startedAt,
                          session.status
                        ].join(' ').toLowerCase().includes(query);
                      });
                      if (!filtered.length) {
                        return '<div class="empty">No requests match the current filter.</div>';
                      }
                      return `
                        <table>
                          <thead>
                            <tr>
                              <th>Request</th>
                              <th>Method</th>
                              <th>Response</th>
                              <th>Client</th>
                              <th>Started</th>
                            </tr>
                          </thead>
                          <tbody>
                            ${filtered.map(session => `
                              <tr class="session-entry${session.sessionId === activeSession ? ' active' : ''}" onclick="selectSession('${session.sessionId}')">
                                <td class="mono">${escapeHtml(session.sessionId || '')}</td>
                                <td>${escapeHtml(session.requestMethod || '')}</td>
                                <td>${escapeHtml(session.responseStatusCode || '')}</td>
                                <td class="mono">${escapeHtml(session.clientAddress || '')}</td>
                                <td>${escapeHtml(formatTime(session.startedAt))}</td>
                              </tr>
                            `).join('')}
                          </tbody>
                        </table>
                      `;
                    }

                    async function selectSession(sessionId) {
                      activeSession = sessionId;
                      activeExchangeIndex = 0;
                      renderRouteHeader();
                      renderRequestTable();
                      await loadSessionDetails(sessionId);
                    }

                    async function loadSessionDetails(sessionId) {
                      const data = await fetchJson('/api/sessions/' + sessionId);
                      const exchanges = data.exchanges || [];
                      if (activeExchangeIndex >= exchanges.length) activeExchangeIndex = 0;
                      const activeExchange = exchanges[activeExchangeIndex] || {};
                      renderPayloads(activeExchange, data);
                      renderEventsAndEditor(data);
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
                      const exchanges = data.exchanges || [];
                      const events = data.events || [];
                      document.getElementById('events-and-editor').innerHTML = `
                        <section class="events-card">
                          <div style="display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:10px;">
                            <h3>Events</h3>
                            <div class="muted">${escapeHtml(exchanges.length)} exchanges / ${escapeHtml(events.length)} events</div>
                          </div>
                          ${renderExchangeButtons(exchanges)}
                          <div class="events-list">
                            ${events.length ? events.map(event => renderEventRow(data.sessionId, event)).join('') : '<div class="empty">No events yet.</div>'}
                          </div>
                        </section>
                        <div id="editor"></div>
                      `;
                    }

                    function renderExchangeButtons(exchanges) {
                      if (exchanges.length <= 1) {
                        return '';
                      }
                      return `
                        <div class="actions" style="margin:0 0 10px;">
                          ${exchanges.map(exchange => `
                            <button class="${exchange.index === activeExchangeIndex ? 'primary' : 'secondary'}" onclick="selectExchange(${exchange.index})">Exchange ${exchange.index + 1}</button>
                          `).join('')}
                        </div>
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

                    async function selectExchange(index) {
                      activeExchangeIndex = index;
                      if (activeSession) {
                        await loadSessionDetails(activeSession);
                      }
                    }

                    function setStatus(type, text) {
                      statusMessage = { type, text };
                      renderBanner();
                      if (activeSession) {
                        loadSessionDetails(activeSession);
                      }
                    }

                    function renderDetailEmpty(message) {
                      document.getElementById('payloads').innerHTML = `<div class="empty">${escapeHtml(message)}</div>`;
                      document.getElementById('events-and-editor').innerHTML = '';
                    }

                    function renderEmptyState(message) {
                      document.getElementById('status-banner').innerHTML = '';
                      document.getElementById('route-header').innerHTML = `<div class="empty">${escapeHtml(message)}</div>`;
                      document.getElementById('request-table').innerHTML = '';
                      document.getElementById('payloads').innerHTML = '';
                      document.getElementById('events-and-editor').innerHTML = '';
                    }

                    function formatTime(value) {
                      if (!value) return '';
                      const date = new Date(value);
                      return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
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
