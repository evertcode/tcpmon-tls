function groupedRoutes() {
  const sessions = getState('allSessions');
  const routeStats = getState('routeStats') || {};
  const map = new Map();
  for (const session of sessions) {
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
    if (isSessionLive(session)) current.status = 'OPEN';
    if (String(session.status || '').toUpperCase() === 'ERROR') current.status = 'ERROR';
    map.set(routeId, current);
  }
  for (const route of map.values()) {
    const stats = routeStats[route.routeId] || {};
    route.requestCount = Number(stats.requestCount || 0);
    route.avgDurationMs = stats.avgDurationMs != null ? Number(stats.avgDurationMs) : null;
    route.errorCount = Number(stats.errorCount || 0);
  }
  return [...map.values()].sort((a, b) => a.routeId.localeCompare(b.routeId));
}

function filteredRoutes() {
  const query = document.getElementById('route-search').value.trim().toLowerCase();
  const sessionRoutes = groupedRoutes();
  const sessionRouteIds = new Set(sessionRoutes.map(r => r.routeId));
  const config = getState('proxyConfig');
  const configRoutes = config ? (config.routes || []) : [];
  const configOnly = configRoutes
    .filter(cr => !sessionRouteIds.has(cr.id))
    .map(cr => ({
      routeId: cr.id,
      sessions: [],
      targetAddress: cr.target.host + ':' + cr.target.port,
      clientAddress: '',
      status: 'CLOSED',
      requestCount: 0,
      avgDurationMs: null,
      errorCount: 0
    }));
  const all = [...sessionRoutes, ...configOnly].sort((a, b) => a.routeId.localeCompare(b.routeId));
  return all.filter(route => {
    if (!query) return true;
    return [route.routeId, route.targetAddress, route.clientAddress].join(' ').toLowerCase().includes(query);
  });
}

function renderRouteList() {
  const routes = filteredRoutes();
  const selectedRouteId = getState('activeRoute');
  const container = document.getElementById('routes');
  if (!routes.length) {
    const query = document.getElementById('route-search').value.trim();
    const empty = document.createElement('div');
    empty.className = 'empty';
    if (query) {
      empty.textContent = 'No routes match your search.';
    } else {
      const inner = document.createElement('div');
      inner.style.display = 'flex';
      inner.style.flexDirection = 'column';
      inner.style.alignItems = 'center';
      inner.style.gap = '10px';
      const msg = document.createElement('span');
      msg.textContent = 'No routes configured yet.';
      const addBtn = document.createElement('button');
      addBtn.className = 'primary';
      addBtn.style.fontSize = '13px';
      addBtn.textContent = '+ Add route';
      addBtn.addEventListener('click', () => openAddRouteModal());
      inner.append(msg, addBtn);
      empty.appendChild(inner);
    }
    container.replaceChildren(empty);
    return;
  }
  const items = routes.map(route => buildRouteListItem(route, selectedRouteId));
  container.replaceChildren(...items);
}

async function selectRoute(routeId) {
  patchState({
    activeRoute: routeId,
    activeSession: null,
    activeExchangeIndex: 0,
    requestSearchValue: '',
    requestMethodFilterValue: '',
    requestStatusCodeFilterValue: '',
    requestCurrentCursor: null,
    requestNextCursor: null,
    requestHasMore: false,
    requestCursorStack: []
  });
  await loadRequestsForRoute(routeId);
  const sessions = sessionsForActiveRoute();
  const requestRows = getState('requestRows');
  if (requestRows.length) {
    patchState({
      activeSession: requestRows[0].sessionId,
      activeExchangeIndex: Number(requestRows[0].exchangeIndex || 0)
    });
  } else {
    setState('activeSession', sessions[0] ? sessions[0].sessionId : null);
  }
  await renderRouteSelectionState();
}

function renderRouteHeader() {
  const selectedRouteId = getState('activeRoute');
  const selectedSessionId = getState('activeSession');
  const config = getState('proxyConfig');
  const sessions = sessionsForActiveRoute();
  const requestRows = requestRowsForActiveRoute();
  const header = document.getElementById('route-header');
  if (!selectedRouteId) {
    header.replaceChildren();
    updateTopbarSubtitle();
    return;
  }
  if (!sessions.length) {
    const configRoute = config && (config.routes || []).find(r => r.id === selectedRouteId);
    const listenerAddr = configRoute ? configRoute.listener.host + ':' + configRoute.listener.port : '';
    const targetAddr = configRoute ? configRoute.target.host + ':' + configRoute.target.port : '';
    header.replaceChildren(buildRouteHeaderEmptyCard(selectedRouteId, listenerAddr, targetAddr));
    updateTopbarSubtitle();
    return;
  }
  const routeHeaderData = buildRouteHeaderViewModel(
    selectedRouteId,
    sessions,
    requestRows,
    selectedSessionId,
    getState('activeExchangeIndex'),
    getState('lastLoadedSession')
  );
  header.replaceChildren(buildRouteHeaderCard(routeHeaderData));
  updateTopbarSubtitle();
}

function buildRouteHeaderViewModel(routeId, sessions, requestRows, selectedSessionId, selectedExchangeIndex, lastLoadedSession) {
  const first = sessions[0] || {};
  const facets = getState('requestFacets') || {};
  const liveCount = sessions.filter(session => isSessionLive(session)).length;
  const pendingCount = sessions.reduce((sum, session) => sum + Number(session.pendingCount || 0), 0);
  const activeSession = resolveActiveSessionSummary(sessions, requestRows, selectedSessionId, selectedExchangeIndex, lastLoadedSession);
  const total = facets.totalRequests != null ? Number(facets.totalRequests) : requestRows.length;
  const avgDurationMs = facets.avgDurationMs != null ? Number(facets.avgDurationMs) : calculateAverageDuration(requestRows);
  return {
    routeId,
    listenerAddress: first.listenerAddress || '',
    targetAddress: first.targetAddress || '',
    total,
    liveCount,
    pendingCount,
    avgDurationMs,
    pendingStatClass: pendingCount >= 3 ? 'stat-danger' : pendingCount > 0 ? 'stat-warn' : '',
    stateLabel: liveCount > 0 ? 'Live' : 'Closed',
    stateClass: liveCount > 0 ? 'state-live' : 'state-closed',
    activeSelection: buildActiveSelectionViewModel(activeSession, selectedSessionId)
  };
}

function calculateAverageDuration(sessions) {
  const withDuration = sessions.filter(session => session.durationMs != null && !Number.isNaN(Number(session.durationMs)));
  if (!withDuration.length) {
    return null;
  }
  return Math.round(withDuration.reduce((sum, session) => sum + Number(session.durationMs), 0) / withDuration.length);
}

function buildActiveSelectionViewModel(activeSession, selectedSessionId) {
  if (!activeSession) {
    return {
      empty: true,
      clientAddress: 'Select a captured request to inspect client, status, duration and timing.',
      statusCode: '',
      durationMs: null,
      startedAt: ''
    };
  }
  return {
    empty: false,
    clientAddress: activeSession.clientAddress || 'Unknown client',
    statusCode: String(activeSession.responseStatusCode || ''),
    durationMs: activeSession.durationMs == null ? null : Number(activeSession.durationMs),
    startedAt: activeSession.startedAt || ''
  };
}

function resolveActiveSessionSummary(sessions, requestRows, selectedSessionId, selectedExchangeIndex, lastLoadedSession) {
  if (!selectedSessionId) {
    return null;
  }
  const requestSummary = requestRows.find(request =>
    request.sessionId === selectedSessionId && Number(request.exchangeIndex || 0) === Number(selectedExchangeIndex || 0)
  ) || null;
  const sessionSummary = sessions.find(session => session.sessionId === selectedSessionId) || null;
  if (requestSummary) {
    return { ...sessionSummary, ...requestSummary };
  }
  // Session now includes latest exchange info from backend — use it as first fallback
  if (sessionSummary && (sessionSummary.requestMethod || sessionSummary.requestPath)) {
    return sessionSummary;
  }
  if (lastLoadedSession && lastLoadedSession.sessionId === selectedSessionId) {
    return {
      ...sessionSummary,
      sessionId: selectedSessionId,
      clientAddress: sessionSummary?.clientAddress || lastLoadedSession.clientAddress || '',
      responseStatusCode: sessionSummary?.responseStatusCode || extractSelectedResponseStatusCode(lastLoadedSession),
      durationMs: sessionSummary?.durationMs ?? lastLoadedSession.durationMs ?? null,
      startedAt: sessionSummary?.startedAt || lastLoadedSession.startedAt || '',
      requestMethod: sessionSummary?.requestMethod || ((lastLoadedSession.latestRequest || {}).request || {}).method,
      requestPath: sessionSummary?.requestPath || buildSelectedSessionPath(lastLoadedSession)
    };
  }
  return sessionSummary;
}

function extractSelectedResponseStatusCode(session) {
  const response = (session && session.latestResponse) || {};
  const value = ((response.response || {}).statusCode);
  return value == null ? '' : String(value);
}

function buildSelectedSessionPath(session) {
  if (!session) {
    return '';
  }
  const request = (session.latestRequest || {}).request || {};
  const path = request.path || '';
  const query = request.query || '';
  if (path && query) {
    return `${path}?${query}`;
  }
  return path;
}

function buildSelectedSessionLabel(session, selectedSessionId) {
  if (!session) {
    return 'None';
  }
  const method = session.requestMethod ? `${session.requestMethod} ` : '';
  const path = session.requestPath || (selectedSessionId ? `${selectedSessionId.slice(0, 8)}\u2026` : '');
  return `${method}${path}`.trim() || 'None';
}

async function loadConfig() {
  try {
    setState('proxyConfig', await fetchJson('/api/config'));
    renderConfigButton();
    renderApp({ detail: false });
    if (!getState('activeRoute')) {
      const routes = filteredRoutes();
      if (routes.length) await selectRoute(routes[0].routeId);
    }
  } catch (e) {
    // Config panel unavailable.
  }
}

function renderConfigButton() {
  const el = document.getElementById('topbar-config');
  if (!el) return;
  const wrap = document.createElement('div');
  wrap.className = 'topbar-tools';

  const themeCluster = document.createElement('div');
  themeCluster.className = 'theme-cluster';

  const currentPreference = getState('themePreference');
  const effectiveTheme = document.documentElement.dataset.theme || 'light';

  const themeToggle = document.createElement('button');
  themeToggle.className = `utility theme-toggle${effectiveTheme === 'dark' ? ' dark' : ' light'}`;
  themeToggle.dataset.action = 'toggle-theme';
  themeToggle.setAttribute('aria-label', effectiveTheme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode');
  themeToggle.title = currentPreference === 'system'
    ? `Theme: System (${effectiveTheme === 'dark' ? 'Dark' : 'Light'})`
    : `Theme: ${effectiveTheme === 'dark' ? 'Dark' : 'Light'}`;
  themeToggle.textContent = effectiveTheme === 'dark' ? '☀' : '☾';

  const themeAuto = document.createElement('button');
  themeAuto.className = `utility theme-auto${currentPreference === 'system' ? ' active' : ''}`;
  themeAuto.dataset.action = 'set-theme-system';
  themeAuto.textContent = 'Auto';
  themeAuto.setAttribute('aria-pressed', String(currentPreference === 'system'));
  themeAuto.title = 'Use system theme';

  const button = document.createElement('button');
  button.className = 'utility';
  button.dataset.action = 'toggle-config-panel';
  button.textContent = 'Config';
  themeCluster.append(themeToggle, themeAuto);
  wrap.append(themeCluster, button);
  el.replaceChildren(wrap);
}

function buildRouteListItem(route, selectedRouteId) {
  const pending = route.sessions.reduce((sum, session) => sum + Number(session.pendingCount || 0), 0);
  const statusClass = String(route.status || 'closed').toLowerCase();
  const isOpen = statusClass === 'open';
  const isError = statusClass === 'error';
  const latest = route.sessions.slice().sort((a, b) => String(b.startedAt || '').localeCompare(String(a.startedAt || '')))[0];
  const avgDuration = route.avgDurationMs;
  const errors = route.errorCount || 0;

  const row = document.createElement('div');
  row.className = `route-row${route.routeId === selectedRouteId ? ' active' : ''}${isOpen ? ' status-open' : isError ? ' status-error' : ''}`;
  row.dataset.action = 'select-route';
  row.dataset.routeId = route.routeId;
  row.setAttribute('role', 'button');
  row.setAttribute('tabindex', '0');
  row.setAttribute('aria-pressed', String(route.routeId === selectedRouteId));
  if (route.routeId === selectedRouteId) {
    row.setAttribute('aria-current', 'true');
  }

  const top = document.createElement('div');
  top.className = 'row-top';

  const title = document.createElement('strong');
  title.style.fontSize = '13px';
  title.textContent = route.routeId;

  const actionsWrap = document.createElement('div');
  actionsWrap.style.display = 'flex';
  actionsWrap.style.gap = '4px';
  actionsWrap.style.alignItems = 'center';
  actionsWrap.style.flexShrink = '0';

  if (pending > 0) {
    const pendingPill = document.createElement('span');
    pendingPill.className = `pill ${pending >= 3 ? 'pending-alarm' : 'pending'}`;
    pendingPill.textContent = String(pending);
    actionsWrap.appendChild(pendingPill);
  }

  const statusPill = document.createElement('span');
  statusPill.className = `pill ${statusClass}`;
  statusPill.textContent = isOpen ? 'Live' : String(route.status || '');
  actionsWrap.appendChild(statusPill);

  actionsWrap.appendChild(buildRouteActions(route.routeId));
  top.append(title, actionsWrap);

  const bottom = document.createElement('div');
  bottom.className = 'row-bottom';

  const targetLine = document.createElement('span');
  targetLine.className = 'route-line';
  targetLine.textContent = route.targetAddress || '';

  const reqPill = document.createElement('span');
  reqPill.className = 'pill route';
  reqPill.style.flexShrink = '0';
  reqPill.textContent = `${route.requestCount || 0} req`;
  bottom.append(targetLine, reqPill);

  row.append(top, bottom);

  if (latest && (latest.requestMethod || latest.requestPath)) {
    row.appendChild(buildLatestPreview(latest));
  }

  const perfLine = buildRoutePerfLine(avgDuration, errors);
  if (perfLine) {
    row.appendChild(perfLine);
  }

  return row;
}

function buildRouteActions(routeId) {
  const actions = document.createElement('span');
  actions.className = 'route-actions';
  actions.append(
    buildRouteActionButton('edit-route', routeId, 'Edit', '✏'),
    buildRouteActionButton('delete-route', routeId, 'Delete', '🗑')
  );
  return actions;
}

function buildRouteActionButton(action, routeId, title, label) {
  const button = document.createElement('button');
  button.className = 'utility route-action-btn';
  button.dataset.action = action;
  button.dataset.routeId = routeId;
  button.title = title;
  button.setAttribute('aria-label', `${title} route "${routeId}"`);
  button.textContent = label;
  return button;
}

function buildLatestPreview(latest) {
  const preview = document.createElement('div');
  preview.className = 'route-preview';

  const method = document.createElement('span');
  method.className = 'method-tag';
  method.textContent = latest.requestMethod || '';

  preview.append(method, document.createTextNode(latest.requestPath || latest.sessionId.slice(0, 12) + '\u2026'));
  return preview;
}

function buildRoutePerfLine(avgDuration, errors) {
  if (avgDuration == null && errors <= 0) {
    return null;
  }
  const line = document.createElement('div');
  line.className = 'route-preview';
  line.style.fontFamily = 'var(--sans)';

  const parts = [];
  if (avgDuration != null) {
    const duration = document.createElement('span');
    duration.className = avgDuration < 200 ? 'timing-fast' : avgDuration < 1000 ? 'timing-medium' : 'timing-slow';
    duration.textContent = `avg ${avgDuration < 1000 ? avgDuration + ' ms' : (avgDuration / 1000).toFixed(1) + ' s'}`;
    parts.push(duration);
  }
  if (errors > 0) {
    const errorPart = document.createElement('span');
    errorPart.style.color = 'var(--danger)';
    errorPart.textContent = `${errors} error${errors !== 1 ? 's' : ''}`;
    parts.push(errorPart);
  }

  parts.forEach((part, index) => {
    if (index > 0) {
      line.appendChild(document.createTextNode(' · '));
    }
    line.appendChild(part);
  });
  return line;
}

function toggleConfigPanel() {
  setState('configPanelOpen', !getState('configPanelOpen'));
  renderConfigPanel();
}

function renderConfigPanel() {
  const el = document.getElementById('config-panel-container');
  if (!el) return;
  const configPanelOpen = getState('configPanelOpen');
  const config = getState('proxyConfig');
  if (!configPanelOpen || !config) {
    el.replaceChildren();
    return;
  }
  el.replaceChildren(buildConfigPanel(config));
}

function buildRouteHeaderEmptyCard(routeId, listenerAddr, targetAddr) {
  const card = document.createElement('section');
  card.className = 'route-card route-header-card route-header-card-empty';
  card.append(
    buildRouteHeaderTop({
      routeId,
      listenerAddress: listenerAddr,
      targetAddress: targetAddr,
      stateLabel: 'No traffic',
      stateClass: 'state-idle',
      showExport: false
    }),
    buildRouteHeaderEmptyHint('This route is configured, but no sessions have been captured yet.')
  );
  return card;
}

function buildRouteHeaderCard(data) {
  const card = document.createElement('section');
  card.className = 'route-card route-header-card';
  card.append(
    buildRouteHeaderTop({
      routeId: data.routeId,
      listenerAddress: data.listenerAddress,
      targetAddress: data.targetAddress,
      stateLabel: data.stateLabel,
      stateClass: data.stateClass,
      pending: data.pendingCount,
      showExport: true
    }),
    buildRouteStats(data.total, data.liveCount, data.pendingCount, data.avgDurationMs, data.pendingStatClass),
    buildActiveSelectionPanel(data.activeSelection)
  );
  return card;
}

function buildRouteHeaderTop(data) {
  const top = document.createElement('div');
  top.className = 'route-header-top';
  top.append(
    buildRouteHeaderIdentity(data.routeId, data.listenerAddress, data.targetAddress),
    buildRouteHeaderActions(data.stateLabel, data.stateClass, data.pending || 0, data.showExport)
  );
  return top;
}

function buildRouteHeaderIdentity(routeId, listenerAddress, targetAddress) {
  const identity = document.createElement('div');
  identity.className = 'route-header-identity';

  const titleRow = document.createElement('div');
  titleRow.className = 'route-header-title-row';

  const eyebrow = document.createElement('span');
  eyebrow.className = 'route-header-eyebrow';
  eyebrow.textContent = 'Active route';

  const title = document.createElement('strong');
  title.className = 'route-header-name';
  title.textContent = routeId;

  const flow = document.createElement('div');
  flow.className = 'route-header-flow';

  const listener = document.createElement('span');
  listener.className = 'route-endpoint mono';
  listener.textContent = listenerAddress || 'Listener unavailable';

  const arrow = document.createElement('span');
  arrow.className = 'route-flow-arrow';
  arrow.textContent = '→';

  const target = document.createElement('span');
  target.className = 'route-endpoint mono';
  target.textContent = targetAddress || 'Target unavailable';

  flow.append(listener, arrow, target);
  titleRow.append(eyebrow, title);
  identity.append(titleRow, flow);
  return identity;
}

function buildRouteHeaderActions(stateLabel, stateClass, pending, showExport) {
  const actions = document.createElement('div');
  actions.className = 'route-header-actions';

  const statusCluster = document.createElement('div');
  statusCluster.className = 'route-status-cluster';
  statusCluster.appendChild(buildRouteStateBadge(stateLabel, stateClass));
  if (pending > 0) {
    statusCluster.appendChild(buildRoutePendingBadge(pending));
  }

  actions.appendChild(statusCluster);

  if (showExport) {
    const exportButton = document.createElement('button');
    exportButton.className = 'utility';
    exportButton.dataset.action = 'export-har';
    exportButton.textContent = 'Export HAR';
    actions.appendChild(exportButton);
  }

  return actions;
}

function buildRouteStateBadge(label, stateClass) {
  const badge = document.createElement('div');
  badge.className = `route-state-badge ${stateClass}`;

  const labelEl = document.createElement('span');
  labelEl.className = 'route-state-label';
  labelEl.textContent = 'State';

  const valueEl = document.createElement('strong');
  valueEl.className = 'route-state-value';
  valueEl.textContent = label;

  badge.append(labelEl, valueEl);
  return badge;
}

function buildRoutePendingBadge(pending) {
  const badge = document.createElement('div');
  badge.className = `route-pending-badge${pending >= 3 ? ' high' : ''}`;

  const label = document.createElement('span');
  label.className = 'route-pending-label';
  label.textContent = 'Pending';

  const value = document.createElement('strong');
  value.className = 'route-pending-value';
  value.textContent = String(pending);

  badge.append(label, value);
  return badge;
}

function buildRouteHeaderEmptyHint(text) {
  const hint = document.createElement('div');
  hint.className = 'route-header-empty-hint';
  hint.textContent = text;
  return hint;
}

function buildRouteStats(total, liveCount, pendingCount, avgDurationMs, pendingStatClass) {
  const stats = document.createElement('div');
  stats.className = 'route-stats';
  stats.append(
    buildRouteStatBlock(String(total), 'Captured'),
    buildRouteStatBlock(String(liveCount), 'Live'),
    buildRouteStatBlock(String(pendingCount), 'Pending', pendingStatClass),
    buildRouteStatBlock(formatMetricDuration(avgDurationMs), 'Avg duration', avgDurationMs == null ? '' : durationMetricClass(avgDurationMs))
  );
  return stats;
}

function buildRouteStatBlock(value, label, extraClass = '') {
  const block = document.createElement('div');
  block.className = `stat-block${extraClass ? ' ' + extraClass : ''}`;

  const statValue = document.createElement('span');
  statValue.className = 'stat-value';
  statValue.textContent = String(value);

  const statLabel = document.createElement('span');
  statLabel.className = 'stat-label';
  statLabel.textContent = label;

  block.append(statValue, statLabel);
  return block;
}

function formatMetricDuration(ms) {
  if (ms == null || Number.isNaN(Number(ms))) {
    return '—';
  }
  const value = Number(ms);
  return value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(1)} s`;
}

function durationMetricClass(ms) {
  if (ms == null || Number.isNaN(Number(ms))) {
    return '';
  }
  const value = Number(ms);
  return value < 200 ? 'stat-good' : value < 1000 ? 'stat-warn' : 'stat-danger';
}

function buildActiveSelectionPanel(selection) {
  const panel = document.createElement('section');
  panel.className = `route-selection-panel${selection.empty ? ' empty' : ''}`;

  if (selection.empty) {
    const eyebrow = document.createElement('span');
    eyebrow.className = 'label';
    eyebrow.textContent = 'Request context';
    const emptyCopy = document.createElement('div');
    emptyCopy.className = 'route-selection-empty';
    emptyCopy.textContent = selection.clientAddress;
    panel.append(eyebrow, emptyCopy);
    return panel;
  }

  const grid = document.createElement('div');
  grid.className = 'route-selection-grid';
  grid.append(
    buildSelectionMetaItem('Client', selection.clientAddress, 'mono route-selection-value'),
    buildSelectionStatusItem(selection.statusCode),
    buildSelectionMetaItem('Duration', formatMetricDuration(selection.durationMs), `route-selection-value ${durationMetricClass(selection.durationMs)}`),
    buildSelectionMetaItem('Started', formatTime(selection.startedAt) || '—', 'route-selection-value')
  );

  panel.append(grid);
  return panel;
}

function buildSelectionMetaItem(label, value, valueClass = 'route-selection-value') {
  const item = document.createElement('div');
  item.className = 'route-selection-item';

  const key = document.createElement('span');
  key.className = 'label';
  key.textContent = label;

  const content = document.createElement('span');
  content.className = valueClass;
  content.textContent = value;

  item.append(key, content);
  return item;
}

function buildSelectionStatusItem(statusCode) {
  const item = document.createElement('div');
  item.className = 'route-selection-item';

  const key = document.createElement('span');
  key.className = 'label';
  key.textContent = 'Status';

  const content = document.createElement('span');
  content.className = 'route-selection-value';
  if (!statusCode) {
    content.className += ' muted';
    content.textContent = '—';
  } else {
    const badge = document.createElement('span');
    const first = statusCode.charAt(0);
    const cls = first === '2' ? 'status-2xx' : first === '3' ? 'status-3xx' : first === '4' ? 'status-4xx' : first === '5' ? 'status-5xx' : 'status-other';
    badge.className = `status-badge ${cls}`;
    badge.textContent = statusCode;
    content.appendChild(badge);
  }

  item.append(key, content);
  return item;
}

function buildConfigPanel(config) {
  const panelWrap = document.createElement('div');
  panelWrap.style.background = 'var(--surface)';
  panelWrap.style.borderBottom = '1px solid var(--border)';
  panelWrap.style.padding = '12px 16px';

  const header = document.createElement('div');
  header.style.display = 'flex';
  header.style.justifyContent = 'space-between';
  header.style.alignItems = 'center';
  header.style.marginBottom = '8px';

  const title = document.createElement('strong');
  title.style.fontSize = '13px';
  title.textContent = 'Proxy Configuration';

  const closeButton = document.createElement('button');
  closeButton.className = 'utility';
  closeButton.dataset.action = 'toggle-config-panel';
  closeButton.textContent = 'Close';

  header.append(title, closeButton);

  const details = document.createElement('div');
  details.className = 'config-panel';
  for (const route of config.routes || []) {
    details.appendChild(buildConfigRouteBlock(route));
  }

  panelWrap.append(
    header,
    buildConfigRow('Intercept Mode', config.interceptMode || ''),
    details
  );
  return panelWrap;
}

function buildConfigRouteBlock(route) {
  const block = document.createElement('div');
  block.style.marginBottom = '10px';
  block.append(
    buildConfigRow('Route', route.id || ''),
    buildConfigRow('Listener', `${route.listener.host}:${route.listener.port} (${route.listener.transport})`),
    buildConfigRow('Target', `${route.target.host}:${route.target.port} (${route.target.transport})`),
    buildConfigRow('Client Auth', route.listener.clientAuth || ''),
    buildConfigRow('Trust All', String(route.target.insecureTrustAll || false))
  );
  return block;
}

function buildConfigRow(key, value) {
  const row = document.createElement('div');
  row.className = 'config-row';

  const keyEl = document.createElement('span');
  keyEl.className = 'config-key';
  keyEl.textContent = key;

  const valueEl = document.createElement('span');
  valueEl.className = 'config-val';
  valueEl.textContent = value;

  row.append(keyEl, valueEl);
  return row;
}
