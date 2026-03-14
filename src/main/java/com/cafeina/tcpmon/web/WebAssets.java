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
                      --accent-strong: #0a5aa0;
                      --accent-soft: rgba(15, 108, 189, 0.1);
                      --route: #106c5a;
                      --ok: #13795b;
                      --warn: #a15c07;
                      --danger: #b42318;
                      --button-shadow: 0 1px 2px rgba(24, 33, 47, 0.08), 0 6px 14px rgba(24, 33, 47, 0.04);
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
                      display: inline-flex;
                      align-items: center;
                      justify-content: center;
                      min-height: 34px;
                      border: 1px solid transparent;
                      border-radius: 10px;
                      padding: 8px 12px;
                      font: inherit;
                      font-weight: 600;
                      line-height: 1.2;
                      letter-spacing: 0.01em;
                      cursor: pointer;
                      white-space: nowrap;
                      transition: background-color 120ms ease, border-color 120ms ease, color 120ms ease, transform 120ms ease, box-shadow 120ms ease;
                    }
                    button:hover:not(:disabled) {
                      transform: translateY(-1px);
                    }
                    button:active:not(:disabled) {
                      transform: translateY(0);
                    }
                    button:focus-visible {
                      outline: 2px solid rgba(15, 108, 189, 0.18);
                      outline-offset: 2px;
                    }
                    button:disabled {
                      opacity: 0.45;
                      cursor: not-allowed;
                      transform: none;
                      box-shadow: none;
                    }
                    button.primary {
                      background: var(--accent);
                      border-color: rgba(10, 90, 160, 0.55);
                      color: white;
                      box-shadow: var(--button-shadow);
                    }
                    button.primary:hover:not(:disabled) {
                      background: var(--accent-strong);
                    }
                    button.secondary {
                      background: var(--surface-2);
                      border-color: rgba(24, 33, 47, 0.16);
                      color: var(--text);
                      box-shadow: 0 1px 2px rgba(24, 33, 47, 0.04);
                    }
                    button.secondary:hover:not(:disabled) {
                      background: var(--surface-3);
                      border-color: rgba(24, 33, 47, 0.24);
                    }
                    button.ghost {
                      background: rgba(255, 255, 255, 0.55);
                      border-color: rgba(24, 33, 47, 0.1);
                      color: var(--text-muted);
                    }
                    button.ghost:hover:not(:disabled) {
                      background: var(--surface);
                      border-color: rgba(24, 33, 47, 0.18);
                      color: var(--text);
                    }
                    button.utility {
                      min-height: 28px;
                      padding: 5px 9px;
                      border-radius: 8px;
                      background: transparent;
                      border-color: rgba(24, 33, 47, 0.08);
                      color: var(--text-muted);
                      font-size: 12px;
                      font-weight: 500;
                      box-shadow: none;
                    }
                    button.utility:hover:not(:disabled) {
                      background: var(--surface-2);
                      border-color: rgba(24, 33, 47, 0.16);
                      color: var(--text);
                    }
                    button.nav {
                      min-height: 30px;
                      padding: 6px 10px;
                      border-radius: 8px;
                      font-size: 12px;
                      font-weight: 600;
                    }
                    button.action-main {
                      min-width: 172px;
                      min-height: 38px;
                      padding: 10px 14px;
                    }
                    button.action-alt {
                      min-height: 38px;
                      padding: 10px 14px;
                    }
                    button.action-edit {
                      min-width: 170px;
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
                    .banner.info {
                      color: var(--text-muted);
                      border-color: rgba(102, 102, 102, 0.18);
                      background: var(--surface);
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
                      grid-template-columns: 1fr 140px 140px auto;
                      gap: 8px;
                      margin-bottom: 10px;
                    }
                    .table-footer {
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      gap: 12px;
                      margin-top: 10px;
                    }
                    .pager {
                      display: flex;
                      gap: 8px;
                      align-items: center;
                    }
                    .pager button {
                      min-width: 92px;
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
                      grid-template-rows: auto auto 1fr auto;
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
                    .payload-details {
                      margin: 0 12px 12px;
                      border: 1px solid var(--border);
                      border-radius: 8px;
                      background: var(--surface-2);
                    }
                    .payload-details summary {
                      list-style: none;
                      cursor: pointer;
                      padding: 10px 12px;
                      color: var(--text-muted);
                      font-size: 12px;
                      text-transform: uppercase;
                      letter-spacing: 0.04em;
                      user-select: none;
                    }
                    .payload-details summary::-webkit-details-marker {
                      display: none;
                    }
                    .payload-details summary::after {
                      content: '+';
                      float: right;
                      color: var(--text-muted);
                      font: 14px/1 var(--mono);
                    }
                    .payload-details[open] summary::after {
                      content: '-';
                    }
                    .payload-details-body {
                      padding: 0 12px 12px;
                    }
                    .headers-table {
                      width: 100%;
                    }
                    .headers-table td {
                      padding: 7px 8px;
                      border-bottom: 1px solid var(--border);
                      font-size: 12px;
                      vertical-align: top;
                    }
                    .headers-table td:first-child {
                      width: 32%;
                      color: var(--text-muted);
                      font-family: var(--mono);
                    }
                    .payload-body {
                      padding: 0 12px 12px;
                      min-height: 0;
                    }
                    .payload-body-head {
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      gap: 8px;
                      margin-bottom: 4px;
                    }
                    .payload-actions {
                      display: flex;
                      gap: 10px;
                      flex-wrap: nowrap;
                      padding: 0 12px 12px;
                      border-top: 1px solid var(--border);
                      margin-top: 4px;
                      padding-top: 12px;
                      align-items: stretch;
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
                    .event-actions {
                      display: flex;
                      gap: 8px;
                    }
                    .editor-actions {
                      display: flex;
                      justify-content: flex-end;
                    }
                    .actions button,
                    .payload-actions button {
                      min-width: 0;
                    }
                    .request-toolbar button {
                      min-width: 88px;
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
                      .payload-actions {
                        flex-wrap: wrap;
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
                            <button class="utility" onclick="refreshSessions(true)">Refresh</button>
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
                    let requestPage = 1;
                    const requestPageSize = 10;
                    let requestSearchValue = '';
                    let requestMethodFilterValue = '';
                    let requestStatusCodeFilterValue = '';
                    let requestSearchDebounceTimer = null;
                    let statusMessage = null;
                    let streamMessage = { type: 'info', text: 'Connecting live updates...' };
                    let eventsExpanded = false;
                    let eventsScrollTop = 0;
                    let eventSource = null;
                    let scheduledDetailRefreshTimer = null;
                    let scheduledListRefreshTimer = null;
                    let pendingDetailRefresh = false;
                    let pendingListRefresh = false;
                    let detailRefreshInFlight = false;
                    let listRefreshInFlight = false;
                    const payloadHeadersExpanded = {
                      Request: false,
                      Response: false
                    };

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
                      return refreshSessionsView(preserveSelection, true);
                    }

                    async function refreshSessionsView(preserveSelection = true, refreshDetail = true) {
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
                      if (refreshDetail && activeSession) {
                        await loadSessionDetails(activeSession);
                      } else if (!activeSession) {
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
                      requestPage = 1;
                      requestSearchValue = '';
                      requestMethodFilterValue = '';
                      requestStatusCodeFilterValue = '';
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
                      const messages = [];
                      if (streamMessage) {
                        messages.push(streamMessage);
                      }
                      if (statusMessage) {
                        messages.push(statusMessage);
                      }
                      if (!messages.length) {
                        el.innerHTML = '';
                        return;
                      }
                      el.innerHTML = messages.map(message => `<div class="banner ${message.type}">${escapeHtml(message.text)}</div>`).join('');
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
                            <input id="request-search" type="search" value="${escapeAttr(requestSearchValue)}" placeholder="Filter requests in this route" oninput="debounceRequestSearch()">
                            <select id="request-method-filter" onchange="resetRequestPageAndRender()">
                              ${renderMethodOptions(sessions)}
                            </select>
                            <select id="request-status-code-filter" onchange="resetRequestPageAndRender()">
                              ${renderStatusCodeOptions(sessions)}
                            </select>
                            <button class="secondary" onclick="refreshSessions(true)">Refresh</button>
                          </div>
                          ${renderRequestTableRows(sessions)}
                        </section>
                      `;
                    }

                    function renderRequestTableRows(sessions) {
                      const query = requestSearchValue.trim().toLowerCase();
                      const methodFilter = requestMethodFilterValue;
                      const statusCodeFilter = requestStatusCodeFilterValue;
                      const filtered = sessions.filter(session => {
                        if (methodFilter && String(session.requestMethod || '') !== methodFilter) return false;
                        if (statusCodeFilter && String(session.responseStatusCode || '') !== statusCodeFilter) return false;
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
                        requestPage = 1;
                        return '<div class="empty">No requests match the current filter.</div>';
                      }
                      const totalPages = Math.max(1, Math.ceil(filtered.length / requestPageSize));
                      requestPage = Math.min(requestPage, totalPages);
                      const pageStart = (requestPage - 1) * requestPageSize;
                      const pageItems = filtered.slice(pageStart, pageStart + requestPageSize);
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
                            ${pageItems.map(session => `
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
                        <div class="table-footer">
                          <div class="muted">Showing ${pageStart + 1}-${pageStart + pageItems.length} of ${filtered.length} requests</div>
                          <div class="pager">
                            <button class="secondary nav" ${requestPage === 1 ? 'disabled' : ''} onclick="changeRequestPage(-1)">Previous</button>
                            <span class="muted">Page ${requestPage} / ${totalPages}</span>
                            <button class="secondary nav" ${requestPage >= totalPages ? 'disabled' : ''} onclick="changeRequestPage(1)">Next</button>
                          </div>
                        </div>
                      `;
                    }

                    function renderMethodOptions(sessions) {
                      const methods = [...new Set(sessions.map(session => String(session.requestMethod || '')).filter(Boolean))].sort();
                      return `<option value="">All methods</option>` + methods.map(method => `<option value="${escapeAttr(method)}" ${method === requestMethodFilterValue ? 'selected' : ''}>${escapeHtml(method)}</option>`).join('');
                    }

                    function renderStatusCodeOptions(sessions) {
                      const statusCodes = [...new Set(sessions.map(session => String(session.responseStatusCode || '')).filter(Boolean))].sort();
                      return `<option value="">All responses</option>` + statusCodes.map(code => `<option value="${escapeAttr(code)}" ${code === requestStatusCodeFilterValue ? 'selected' : ''}>${escapeHtml(code)}</option>`).join('');
                    }

                    function resetRequestPageAndRender() {
                      requestSearchValue = document.getElementById('request-search') ? document.getElementById('request-search').value : requestSearchValue;
                      requestMethodFilterValue = document.getElementById('request-method-filter') ? document.getElementById('request-method-filter').value : requestMethodFilterValue;
                      requestStatusCodeFilterValue = document.getElementById('request-status-code-filter') ? document.getElementById('request-status-code-filter').value : requestStatusCodeFilterValue;
                      requestPage = 1;
                      renderRequestTable();
                    }

                    function debounceRequestSearch() {
                      requestSearchValue = document.getElementById('request-search') ? document.getElementById('request-search').value : requestSearchValue;
                      requestPage = 1;
                      if (requestSearchDebounceTimer) {
                        clearTimeout(requestSearchDebounceTimer);
                      }
                      requestSearchDebounceTimer = setTimeout(() => {
                        requestSearchDebounceTimer = null;
                        renderRequestTable();
                      }, 250);
                    }

                    function changeRequestPage(delta) {
                      requestPage = Math.max(1, requestPage + delta);
                      renderRequestTable();
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
                          ${renderPayloadCard('Request', request, 'CLIENT_TO_TARGET', data)}
                          ${renderPayloadCard('Response', response, 'TARGET_TO_CLIENT', data)}
                        </section>
                      `;
                    }

                    function renderPayloadCard(title, payload, expectedDirection, data) {
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
                      const headers = Array.isArray(decoded.headers) ? decoded.headers : [];
                      const bodyText = formatBody(decoded);
                      const hasBody = Boolean(bodyText);
                      const chunkText = payload.chunkCount ? ` / ${payload.chunkCount} chunks` : '';
                      const actions = title === 'Request' ? renderRequestActions(data, payload) : '';
                      const copyAction = hasBody
                        ? `onclick='copyPayloadBody(${JSON.stringify(title)}, ${JSON.stringify(bodyText)})'`
                        : '';
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
                          <div class="payload-body">
                            <div class="payload-body-head">
                              <span class="label">Body</span>
                              ${hasBody ? `<button class="utility" ${copyAction}>Copy body</button>` : ''}
                            </div>
                            <pre class="scroll">${escapeHtml(bodyText || 'No body captured')}</pre>
                          </div>
                          ${actions}
                          <details class="payload-details" ${payloadHeadersExpanded[title] ? 'open' : ''} ontoggle="setPayloadHeadersExpanded('${title}', this.open)">
                            <summary>Headers</summary>
                            <div class="payload-details-body">
                              ${renderHeadersTable(headers, decoded)}
                            </div>
                          </details>
                        </article>
                      `;
                    }

                    function renderRequestActions(data, payload) {
                      if (!payload?.base64 || !data?.routeId) {
                        return '';
                      }
                      return `
                        <div class="payload-actions">
                          <button class="primary action-main" onclick='replayPayload(${JSON.stringify(data.routeId)}, ${JSON.stringify(payload.base64)}, "listener")'>Recapture request</button>
                          <button class="secondary action-alt" onclick='replayPayload(${JSON.stringify(data.routeId)}, ${JSON.stringify(payload.base64)}, "target")'>Send direct</button>
                        </div>
                      `;
                    }

                    function renderHeadersTable(headers, decoded) {
                      if (!decoded.isHttp) {
                        return `<pre>Non-HTTP payload</pre>`;
                      }
                      if (!headers.length) {
                        return `<pre>No headers</pre>`;
                      }
                      return `
                        <table class="headers-table">
                          <tbody>
                            ${headers.map(header => `
                              <tr>
                                <td>${escapeHtml(header.name || '')}</td>
                                <td>${escapeHtml(header.value || '')}</td>
                              </tr>
                            `).join('')}
                          </tbody>
                        </table>
                      `;
                    }

                    function formatBody(decoded) {
                      const bodyText = decoded.bodyText || '';
                      if (!decoded.isHttp || !bodyText) {
                        return bodyText;
                      }
                      const headers = Array.isArray(decoded.headers) ? decoded.headers : [];
                      const contentTypeHeader = headers.find(header => String(header.name || '').toLowerCase() === 'content-type');
                      const contentType = String(contentTypeHeader?.value || '').toLowerCase();
                      if (contentType.includes('json') || looksLikeJson(bodyText)) {
                        try {
                          return JSON.stringify(JSON.parse(bodyText), null, 2);
                        } catch (error) {
                          return bodyText;
                        }
                      }
                      if (contentType.includes('xml') || contentType.includes('soap') || looksLikeXml(bodyText)) {
                        return prettyPrintXml(bodyText);
                      }
                      return bodyText;
                    }

                    async function copyPayloadBody(title, bodyText) {
                      if (!bodyText) {
                        return;
                      }
                      try {
                        if (navigator.clipboard?.writeText) {
                          await navigator.clipboard.writeText(bodyText);
                        } else {
                          const helper = document.createElement('textarea');
                          helper.value = bodyText;
                          helper.setAttribute('readonly', 'true');
                          helper.style.position = 'absolute';
                          helper.style.left = '-9999px';
                          document.body.appendChild(helper);
                          helper.select();
                          document.execCommand('copy');
                          document.body.removeChild(helper);
                        }
                        setStatus('success', `${title} body copied to clipboard`);
                      } catch (error) {
                        setStatus('error', `Unable to copy ${title.toLowerCase()} body`);
                      }
                    }

                    function looksLikeJson(value) {
                      const text = String(value || '').trim();
                      return text.startsWith('{') || text.startsWith('[');
                    }

                    function looksLikeXml(value) {
                      return String(value || '').trim().startsWith('<');
                    }

                    function prettyPrintXml(value) {
                      const compact = String(value || '').replace(/>\s+</g, '><').trim();
                      if (!compact) {
                        return value;
                      }
                      const tokens = compact.replace(/></g, '>\\n<').split('\\n');
                      let indent = 0;
                      const lines = [];
                      for (const rawToken of tokens) {
                        const token = rawToken.trim();
                        if (!token) continue;
                        if (token.startsWith('</')) {
                          indent = Math.max(0, indent - 1);
                        }
                        lines.push('  '.repeat(indent) + token);
                        if (token.startsWith('<') && !token.startsWith('</') && !token.endsWith('/>') && !token.includes('</')) {
                          indent++;
                        }
                      }
                      return lines.join('\\n');
                    }

                    function renderEventsAndEditor(data) {
                      const exchanges = data.exchanges || [];
                      const events = data.events || [];
                      document.getElementById('events-and-editor').innerHTML = `
                        <details class="events-card" ${eventsExpanded ? 'open' : ''} ontoggle="setEventsExpanded(this.open)">
                          <summary style="display:flex;justify-content:space-between;align-items:center;gap:12px;cursor:pointer;">
                            <span><strong>Events</strong></span>
                            <span class="muted">${escapeHtml(exchanges.length)} exchanges / ${escapeHtml(events.length)} events</span>
                          </summary>
                          <div style="margin-top:10px;">
                            ${renderExchangeButtons(exchanges)}
                            <div id="events-list" class="events-list" onscroll="setEventsScroll(this.scrollTop)">
                              ${events.length ? events.map(event => renderEventRow(data.sessionId, event)).join('') : '<div class="empty">No events yet.</div>'}
                            </div>
                          </div>
                        </details>
                        <div id="editor"></div>
                      `;
                      restoreEventsScroll();
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
                      const pending = event.pendingId
                        ? `<div class="actions event-actions">
                             <button class="secondary" onclick="releasePending('${event.pendingId}')">Forward original</button>
                             <button class="primary action-edit" onclick='showEdit("${event.pendingId}", ${JSON.stringify(event.decoded || null)}, "${event.details?.base64 || ''}")'>Edit and forward</button>
                           </div>` : '';
                      return `
                        <div class="event-row">
                          <div class="event-top">
                            <div><strong>${escapeHtml(event.type)}</strong> <span class="muted">${escapeHtml(event.direction || '')}</span></div>
                          <div class="muted">${escapeHtml(event.timestamp || '')}</div>
                          </div>
                          <div class="muted">${escapeHtml(event.size || 0)} bytes</div>
                          <pre>${escapeHtml(event.previewText || '')}\n${escapeHtml(event.previewHex || '')}</pre>
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
                              <div class="actions editor-actions">
                                <button class="primary action-edit" onclick="submitStructuredHttp('${pendingId}')">Forward edited HTTP</button>
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
                            <div class="actions editor-actions">
                              <button class="primary action-edit" onclick="submitEdited('${pendingId}')">Forward edited</button>
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
                      activeExchangeIndex = index;
                      if (activeSession) {
                        await loadSessionDetails(activeSession);
                      }
                    }

                    function setPayloadHeadersExpanded(title, open) {
                      payloadHeadersExpanded[title] = open;
                    }

                    function setEventsExpanded(open) {
                      eventsExpanded = open;
                    }

                    function setEventsScroll(value) {
                      eventsScrollTop = value;
                    }

                    function restoreEventsScroll() {
                      if (!eventsExpanded) {
                        return;
                      }
                      requestAnimationFrame(() => {
                        const list = document.getElementById('events-list');
                        if (list) {
                          list.scrollTop = eventsScrollTop;
                        }
                      });
                    }

                    function setStatus(type, text) {
                      statusMessage = { type, text };
                      renderBanner();
                      if (activeSession) {
                        loadSessionDetails(activeSession);
                      }
                    }

                    function connectEventStream() {
                      if (eventSource) {
                        eventSource.close();
                      }
                      eventSource = new EventSource('/api/events');
                      eventSource.addEventListener('open', () => {
                        streamMessage = null;
                        renderBanner();
                      });
                      eventSource.addEventListener('error', () => {
                        streamMessage = { type: 'info', text: 'Live updates disconnected. Trying to reconnect...' };
                        renderBanner();
                      });
                      eventSource.addEventListener('session-created', handleSessionChange);
                      eventSource.addEventListener('session-updated', handleSessionChange);
                      eventSource.addEventListener('session-closed', handleSessionChange);
                      eventSource.addEventListener('pending-released', handleSessionChange);
                    }

                    async function handleSessionChange(event) {
                      let payload;
                      try {
                        payload = JSON.parse(event.data || '{}');
                      } catch (error) {
                        return;
                      }
                      const affectsActiveSession = Boolean(activeSession && payload.sessionId === activeSession);
                      const affectsActiveRoute = Boolean(activeRoute && payload.routeId === activeRoute);
                      if (payload.type === 'session-created' || payload.type === 'session-closed') {
                        scheduleListRefresh();
                        if (affectsActiveSession) {
                          scheduleDetailRefresh();
                        }
                        return;
                      }
                      if (payload.type === 'pending-released') {
                        if (affectsActiveSession) {
                          scheduleDetailRefresh();
                        }
                        return;
                      }
                      if (affectsActiveSession) {
                        scheduleDetailRefresh();
                        return;
                      }
                      if (affectsActiveRoute) {
                        scheduleListRefresh();
                      }
                    }

                    function scheduleDetailRefresh() {
                      pendingDetailRefresh = true;
                      if (scheduledDetailRefreshTimer) {
                        return;
                      }
                      scheduledDetailRefreshTimer = setTimeout(async () => {
                        scheduledDetailRefreshTimer = null;
                        if (detailRefreshInFlight) {
                          scheduleDetailRefresh();
                          return;
                        }
                        if (!pendingDetailRefresh || !activeSession) {
                          pendingDetailRefresh = false;
                          return;
                        }
                        pendingDetailRefresh = false;
                        detailRefreshInFlight = true;
                        try {
                          await loadSessionDetails(activeSession);
                        } catch (error) {
                          streamMessage = { type: 'info', text: 'Live update received, but refresh failed. Use Refresh to resync.' };
                          renderBanner();
                        } finally {
                          detailRefreshInFlight = false;
                        }
                        if (!streamMessage) {
                          renderBanner();
                        }
                        if (pendingDetailRefresh) {
                          scheduleDetailRefresh();
                        }
                      }, 150);
                    }

                    function scheduleListRefresh() {
                      pendingListRefresh = true;
                      if (scheduledListRefreshTimer) {
                        return;
                      }
                      scheduledListRefreshTimer = setTimeout(async () => {
                        scheduledListRefreshTimer = null;
                        if (listRefreshInFlight) {
                          scheduleListRefresh();
                          return;
                        }
                        if (!pendingListRefresh) {
                          return;
                        }
                        pendingListRefresh = false;
                        listRefreshInFlight = true;
                        try {
                          await refreshSessionsView(true, false);
                        } catch (error) {
                          streamMessage = { type: 'info', text: 'Live update received, but refresh failed. Use Refresh to resync.' };
                          renderBanner();
                        } finally {
                          listRefreshInFlight = false;
                        }
                        if (!streamMessage) {
                          renderBanner();
                        }
                        if (pendingListRefresh) {
                          scheduleListRefresh();
                        }
                      }, 800);
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
                    connectEventStream();
                  </script>
                </body>
                </html>
                """;
    }
}
