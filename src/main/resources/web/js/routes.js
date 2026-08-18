function groupedRoutes() {
  const sessions = getState('allSessions');
  const routeStats = getState('routeStats') || {};
  const config = getState('proxyConfig');
  const configRouteIds = config ? new Set((config.routes || []).map(r => r.id)) : null;
  const map = new Map();
  for (const session of sessions) {
    const routeId = session.routeId || 'default';
    if (configRouteIds && !configRouteIds.has(routeId)) continue;
    const current = map.get(routeId) || {
      routeId,
      sessions: [],
      targetAddress: session.targetAddress || '',
      listenerAddress: session.listenerAddress || '',
      clientAddress: session.clientAddress || '',
      status: 'CLOSED'
    };
    current.sessions.push(session);
    if (!current.targetAddress && session.targetAddress) current.targetAddress = session.targetAddress;
    if (!current.listenerAddress && session.listenerAddress) current.listenerAddress = session.listenerAddress;
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

const ROUTE_ORDER_STORAGE_KEY = 'tcpmon-route-order';

function getStoredRouteOrder() {
  try {
    const raw = localStorage.getItem(ROUTE_ORDER_STORAGE_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.filter(id => typeof id === 'string') : [];
  } catch (error) {
    return [];
  }
}

function initializeRouteOrder() {
  setState('routeOrder', getStoredRouteOrder());
}

function persistRouteOrder(order) {
  setState('routeOrder', order);
  try {
    localStorage.setItem(ROUTE_ORDER_STORAGE_KEY, JSON.stringify(order));
  } catch (error) {
    // Ignore storage failures; in-memory order still applies for this session.
  }
}

function applyRouteOrder(routes) {
  const storedOrder = getState('routeOrder') || [];
  const orderIndex = new Map(storedOrder.map((id, index) => [id, index]));
  const known = routes
    .filter(r => orderIndex.has(r.routeId))
    .sort((a, b) => orderIndex.get(a.routeId) - orderIndex.get(b.routeId));
  const unknown = routes
    .filter(r => !orderIndex.has(r.routeId))
    .sort((a, b) => a.routeId.localeCompare(b.routeId));
  const merged = [...known, ...unknown];

  // Only reconcile (prune stale ids / append new ones) once proxyConfig has
  // loaded — before that, `routes` is an incomplete transient snapshot and
  // must not be treated as authoritative, or the stored order gets wiped.
  if (getState('proxyConfig')) {
    const reconciledIds = merged.map(r => r.routeId);
    const changed = reconciledIds.length !== storedOrder.length
      || reconciledIds.some((id, i) => id !== storedOrder[i]);
    if (changed) {
      persistRouteOrder(reconciledIds);
    }
  }
  return merged;
}

let draggedRouteId = null;

function reorderRoutes(draggedId, targetRouteId, placeAfter) {
  if (!draggedId || draggedId === targetRouteId) return;
  const order = (getState('routeOrder') || []).slice();
  const fromIndex = order.indexOf(draggedId);
  if (fromIndex !== -1) order.splice(fromIndex, 1);
  const targetIndex = order.indexOf(targetRouteId);
  if (targetIndex === -1) {
    order.push(draggedId);
  } else {
    order.splice(targetIndex + (placeAfter ? 1 : 0), 0, draggedId);
  }
  persistRouteOrder(order);
  renderRouteList();
}

function clearRouteDragIndicators() {
  document.querySelectorAll('.route-row.drag-over-before, .route-row.drag-over-after')
    .forEach(el => el.classList.remove('drag-over-before', 'drag-over-after'));
}

function handleRouteDragStart(event) {
  const handle = event.target.closest('.route-drag-handle');
  if (!handle) return;
  const row = handle.closest('.route-row');
  if (!row) return;
  draggedRouteId = row.dataset.routeId;
  event.dataTransfer.effectAllowed = 'move';
  event.dataTransfer.setData('text/plain', draggedRouteId);
  try {
    event.dataTransfer.setDragImage(row, 16, 16);
  } catch (error) {
    // Some browsers may reject a custom drag image; fall back to default.
  }
  row.classList.add('dragging');
}

function handleRouteDragOver(event) {
  if (!draggedRouteId) return;
  const row = event.target.closest('.route-row');
  if (!row || row.dataset.routeId === draggedRouteId) return;
  event.preventDefault();
  event.dataTransfer.dropEffect = 'move';
  clearRouteDragIndicators();
  const rect = row.getBoundingClientRect();
  const placeAfter = event.clientY - rect.top > rect.height / 2;
  row.classList.add(placeAfter ? 'drag-over-after' : 'drag-over-before');
}

function handleRouteDrop(event) {
  if (!draggedRouteId) return;
  const row = event.target.closest('.route-row');
  clearRouteDragIndicators();
  if (row && row.dataset.routeId !== draggedRouteId) {
    event.preventDefault();
    const rect = row.getBoundingClientRect();
    const placeAfter = event.clientY - rect.top > rect.height / 2;
    reorderRoutes(draggedRouteId, row.dataset.routeId, placeAfter);
  }
  draggedRouteId = null;
}

function handleRouteDragEnd() {
  draggedRouteId = null;
  clearRouteDragIndicators();
  const draggingRow = document.querySelector('.route-row.dragging');
  if (draggingRow) draggingRow.classList.remove('dragging');
}

function moveRouteOrderByKeyboard(routeId, moveDown) {
  const rows = [...document.querySelectorAll('.route-row')];
  const index = rows.findIndex(r => r.dataset.routeId === routeId);
  const targetRow = rows[index + (moveDown ? 1 : -1)];
  if (!targetRow) return;
  reorderRoutes(routeId, targetRow.dataset.routeId, moveDown);
  requestAnimationFrame(() => {
    document.querySelector(`.route-row-select[data-route-id="${CSS.escape(routeId)}"]`)?.focus();
  });
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
      listenerAddress: cr.listener.host + ':' + cr.listener.port,
      clientAddress: '',
      status: 'CLOSED',
      requestCount: 0,
      avgDurationMs: null,
      errorCount: 0
    }));
  const all = applyRouteOrder([...sessionRoutes, ...configOnly]);
  return all.filter(route => {
    if (!query) return true;
    return [route.routeId, route.targetAddress, route.clientAddress].join(' ').toLowerCase().includes(query);
  });
}

function renderRouteList() {
  const routes = filteredRoutes();
  const selectedRouteId = getState('activeRoute');
  const container = document.getElementById('routes');
  container.setAttribute('role', 'list');
  container.setAttribute('aria-label', 'Routes');
  if (!routes.length) {
    const query = document.getElementById('route-search').value.trim();
    if (query) {
      container.replaceChildren(buildEmptyState(`No route matches "${query}".`, 'Search checks route ID, target address, and client address.'));
    } else {
      const addBtn = document.createElement('button');
      addBtn.className = 'primary';
      addBtn.textContent = '+ Add route';
      addBtn.addEventListener('click', () => openAddRouteModal());
      container.replaceChildren(buildEmptyState('No listeners configured.', 'Add a route to bind a local listener and forward traffic to a target.', addBtn));
    }
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
    summary: buildRouteActivitySummary(total, liveCount, pendingCount, avgDurationMs),
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
      clientAddress: 'Select a request below to inspect payloads and timing.',
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
  const themeLabel = document.createElement('label');
  themeLabel.className = 'theme-label';
  themeLabel.setAttribute('for', 'theme-select');
  themeLabel.textContent = 'Theme';

  const themeSelect = document.createElement('select');
  themeSelect.id = 'theme-select';
  themeSelect.className = 'theme-select';
  themeSelect.value = currentPreference;
  themeSelect.setAttribute('aria-label', 'Theme preference');
  for (const option of [
    ['system', 'Auto'],
    ['light', 'Light'],
    ['dark', 'Dark']
  ]) {
    const optionEl = document.createElement('option');
    optionEl.value = option[0];
    optionEl.textContent = option[1];
    themeSelect.appendChild(optionEl);
  }
  themeSelect.value = currentPreference;
  themeCluster.append(themeLabel, themeSelect);

  const button = document.createElement('button');
  button.className = 'utility';
  button.dataset.action = 'toggle-config-panel';
  setButtonContent(button, 'Config', 'settings');
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

  const isActive = route.routeId === selectedRouteId;

  const row = document.createElement('div');
  row.className = `route-row${isActive ? ' active' : ''}${isOpen ? ' status-open' : isError ? ' status-error' : ''}`;
  row.dataset.action = 'select-route';
  row.dataset.routeId = route.routeId;
  row.setAttribute('role', 'listitem');

  const select = document.createElement('button');
  select.type = 'button';
  select.className = 'route-row-select';
  select.dataset.action = 'select-route';
  select.dataset.routeId = route.routeId;
  if (isActive) {
    select.setAttribute('aria-current', 'true');
  }

  const title = document.createElement('strong');
  title.className = 'route-row-title';
  title.textContent = route.routeId;

  const bottom = document.createElement('div');
  bottom.className = 'row-bottom';

  const flowLine = document.createElement('span');
  flowLine.className = 'route-line';
  const listenerAddress = route.listenerAddress || routeEndpointLabel(route.routeId, 'listener');
  const flowText = listenerAddress && route.targetAddress
    ? `${listenerAddress} → ${route.targetAddress}`
    : (route.targetAddress || listenerAddress || '');
  flowLine.textContent = flowText;
  if (flowText) flowLine.title = flowText;

  const reqCount = document.createElement('span');
  reqCount.className = 'route-line route-req-count';
  reqCount.textContent = `${route.requestCount || 0} req`;
  bottom.append(flowLine, reqCount);

  select.append(title, bottom);

  if (latest && (latest.requestMethod || latest.requestPath)) {
    select.appendChild(buildLatestPreview(latest));
  }

  const perfLine = buildRoutePerfLine(avgDuration, errors);
  if (perfLine) {
    select.appendChild(perfLine);
  }

  const side = document.createElement('div');
  side.className = 'route-row-side';

  if (pending > 0) {
    const pendingPill = document.createElement('span');
    pendingPill.className = `pill ${pending >= 3 ? 'pending-alarm' : 'pending'}`;
    pendingPill.textContent = String(pending);
    const pendingLabel = `${pending} intercepted payload${pending === 1 ? '' : 's'} pending`;
    pendingPill.title = pendingLabel;
    pendingPill.setAttribute('aria-label', pendingLabel);
    side.appendChild(pendingPill);
  }

  if (isOpen || isError) {
    const statusPill = document.createElement('span');
    statusPill.className = `pill ${statusClass}`;
    statusPill.textContent = isOpen ? 'Live' : 'Error';
    side.appendChild(statusPill);
  }

  side.appendChild(buildRouteActions(route.routeId));

  row.append(buildRouteDragHandle(route.routeId), select, side);
  return row;
}

function buildRouteDragHandle(routeId) {
  const handle = document.createElement('button');
  handle.type = 'button';
  handle.className = 'utility icon-only route-drag-handle';
  handle.draggable = true;
  handle.dataset.routeId = routeId;
  setButtonContent(handle, '', 'grip', {
    title: 'Drag to reorder',
    ariaLabel: `Reorder route "${routeId}"`
  });
  handle.addEventListener('click', event => event.stopPropagation());
  return handle;
}

function buildRouteActions(routeId) {
  const actions = document.createElement('span');
  actions.className = 'route-actions';
  actions.append(
    buildRouteActionButton('edit-route', routeId, 'Edit'),
    buildRouteActionButton('delete-route', routeId, 'Delete')
  );
  return actions;
}

function buildRouteActionButton(action, routeId, title) {
  const button = document.createElement('button');
  button.className = `utility route-action-btn icon-only${action === 'delete-route' ? ' route-action-delete' : ''}`;
  button.dataset.action = action;
  button.dataset.routeId = routeId;
  setButtonContent(button, '', action === 'delete-route' ? 'trash' : 'edit', {
    title,
    ariaLabel: `${title} route "${routeId}"`
  });
  return button;
}

function buildLatestPreview(latest) {
  const preview = document.createElement('div');
  preview.className = 'route-preview';

  const method = document.createElement('span');
  const methodLower = (latest.requestMethod || '').toLowerCase();
  method.className = `method-tag${methodLower ? ' method-' + methodLower : ''}`;
  method.textContent = latest.requestMethod || '';

  const pathText = latest.requestPath || latest.sessionId.slice(0, 12) + '\u2026';
  preview.title = `${latest.requestMethod ? latest.requestMethod + ' ' : ''}${pathText}`;
  preview.append(method, document.createTextNode(pathText));
  return preview;
}

function buildRoutePerfLine(avgDuration, errors) {
  if (avgDuration == null && errors <= 0) {
    return null;
  }
  const line = document.createElement('div');
  line.className = 'route-preview route-perf-line';

  const parts = [];
  if (avgDuration != null) {
    const duration = document.createElement('span');
    duration.className = avgDuration < 200 ? 'timing-fast' : avgDuration < 1000 ? 'timing-medium' : 'timing-slow';
    duration.textContent = `avg ${avgDuration < 1000 ? avgDuration + ' ms' : (avgDuration / 1000).toFixed(1) + ' s'}`;
    parts.push(duration);
  }
  if (errors > 0) {
    const errorPart = document.createElement('span');
    errorPart.className = 'route-perf-errors';
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
      showExport: false
    }),
    buildRouteHeaderEmptyHint(listenerAddr
      ? `Waiting for traffic on ${listenerAddr}${targetAddr ? ` -> ${targetAddr}` : ''}.`
      : 'This route is configured, but the listener address is not available yet.')
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
      pending: data.pendingCount,
      showExport: true
    }),
    buildRouteStats(data.total, data.liveCount, data.pendingCount, data.avgDurationMs, data.pendingStatClass, data.summary),
    buildActiveSelectionPanel(data.activeSelection)
  );
  return card;
}

function buildRouteHeaderTop(data) {
  const top = document.createElement('div');
  top.className = 'route-header-top';
  top.append(
    buildRouteHeaderIdentity(data.routeId, data.listenerAddress, data.targetAddress),
    buildRouteHeaderActions(data.pending || 0, data.showExport)
  );
  return top;
}

function buildRouteHeaderIdentity(routeId, listenerAddress, targetAddress) {
  const identity = document.createElement('div');
  identity.className = 'route-header-identity';

  const titleRow = document.createElement('div');
  titleRow.className = 'route-header-title-row';

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
  titleRow.append(title);
  identity.append(titleRow, flow);
  return identity;
}

function buildRouteHeaderActions(pending, showExport) {
  const actions = document.createElement('div');
  actions.className = 'route-header-actions';

  const statusCluster = document.createElement('div');
  statusCluster.className = 'route-status-cluster';
  if (pending > 0) {
    statusCluster.appendChild(buildRoutePendingBadge(pending));
  }

  if (statusCluster.children.length) {
    actions.appendChild(statusCluster);
  }

  if (showExport) {
    const exportButton = document.createElement('button');
    exportButton.className = 'utility';
    exportButton.dataset.action = 'export-har';
    setButtonContent(exportButton, 'Export HAR', 'download');
    actions.appendChild(exportButton);
  }

  return actions;
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

function buildRouteStats(total, liveCount, pendingCount, avgDurationMs, pendingStatClass, summary = '') {
  const stats = document.createElement('div');
  stats.className = 'route-stats';
  if (summary) {
    const summaryEl = document.createElement('div');
    summaryEl.className = 'route-stats-summary';
    summaryEl.textContent = summary;
    stats.appendChild(summaryEl);
  }
  stats.append(
    buildRouteStatBlock(String(total), 'Captured'),
    buildRouteStatBlock(String(liveCount), 'Live'),
    buildRouteStatBlock(String(pendingCount), 'Pending', pendingStatClass),
    buildRouteStatBlock(formatMetricDuration(avgDurationMs), 'Avg duration', avgDurationMs == null ? '' : durationMetricClass(avgDurationMs))
  );
  return stats;
}

function buildRouteActivitySummary(total, liveCount, pendingCount, avgDurationMs) {
  const parts = [`${total} captured request${total === 1 ? '' : 's'}`];
  if (liveCount > 0) parts.push(`${liveCount} live session${liveCount === 1 ? '' : 's'}`);
  if (pendingCount > 0) parts.push(`${pendingCount} intercepted payload${pendingCount === 1 ? '' : 's'}`);
  if (avgDurationMs != null && !Number.isNaN(Number(avgDurationMs))) {
    parts.push(`${formatMetricDuration(avgDurationMs)} avg`);
  }
  return parts.join(' · ');
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
  panelWrap.className = 'config-panel-wrap';

  const header = document.createElement('div');
  header.className = 'config-panel-header';

  const title = document.createElement('strong');
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
  block.className = 'config-route-block';
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
