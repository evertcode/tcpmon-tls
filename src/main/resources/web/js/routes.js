function groupedRoutes() {
  const sessions = getState('allSessions');
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
      status: 'CLOSED'
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
    const empty = document.createElement('div');
    empty.className = 'empty';
    empty.textContent = 'No matching routes.';
    container.replaceChildren(empty);
    return;
  }
  const items = routes.map(route => buildRouteListItem(route, selectedRouteId));
  container.replaceChildren(...items);
}

function selectRoute(routeId) {
  patchState({
    activeRoute: routeId,
    activeSession: null,
    activeExchangeIndex: 0,
    requestPage: 1,
    requestSearchValue: '',
    requestMethodFilterValue: '',
    requestStatusCodeFilterValue: ''
  });
  const sessions = sessionsForActiveRoute();
  setState('activeSession', sessions[0] ? sessions[0].sessionId : null);
  renderRouteSelectionState();
}

function renderRouteHeader() {
  const selectedRouteId = getState('activeRoute');
  const selectedSessionId = getState('activeSession');
  const config = getState('proxyConfig');
  const sessions = sessionsForActiveRoute();
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
  const first = sessions[0] || {};
  const open = sessions.filter(session => isSessionLive(session)).length;
  const pending = sessions.reduce((sum, session) => sum + Number(session.pendingCount || 0), 0);
  const activeSessionObj = resolveActiveSessionSummary(sessions, selectedSessionId, getState('lastLoadedSession'));
  const selectedLabel = buildSelectedSessionLabel(activeSessionObj, selectedSessionId);
  const pendingStatClass = pending >= 3 ? 'stat-danger' : pending > 0 ? 'stat-warn' : '';
  header.replaceChildren(buildRouteHeaderCard({
    routeId: selectedRouteId,
    listenerAddress: first.listenerAddress || '',
    targetAddress: first.targetAddress || '',
    total: sessions.length,
    open,
    pending,
    pendingStatClass,
    clientAddress: first.clientAddress || 'Unknown',
    selectedLabel
  }));
  updateTopbarSubtitle();
}

function resolveActiveSessionSummary(sessions, selectedSessionId, lastLoadedSession) {
  if (!selectedSessionId) {
    return null;
  }
  const sessionSummary = sessions.find(session => session.sessionId === selectedSessionId) || null;
  if (lastLoadedSession && lastLoadedSession.sessionId === selectedSessionId) {
    return {
      ...sessionSummary,
      sessionId: selectedSessionId,
      requestMethod: sessionSummary && sessionSummary.requestMethod
        ? sessionSummary.requestMethod
        : ((lastLoadedSession.latestRequest || {}).request || {}).method,
      requestPath: sessionSummary && sessionSummary.requestPath
        ? sessionSummary.requestPath
        : buildSelectedSessionPath(lastLoadedSession)
    };
  }
  return sessionSummary;
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
      if (routes.length) selectRoute(routes[0].routeId);
    }
  } catch (e) {
    // Config panel unavailable.
  }
}

function renderConfigButton() {
  const el = document.getElementById('topbar-config');
  if (!el) return;
  const button = document.createElement('button');
  button.className = 'utility';
  button.dataset.action = 'toggle-config-panel';
  button.textContent = 'Config';
  el.replaceChildren(button);
}

function buildRouteListItem(route, selectedRouteId) {
  const pending = route.sessions.reduce((sum, session) => sum + Number(session.pendingCount || 0), 0);
  const statusClass = String(route.status || 'closed').toLowerCase();
  const isOpen = statusClass === 'open';
  const isError = statusClass === 'error';
  const latest = route.sessions.slice().sort((a, b) => String(b.startedAt || '').localeCompare(String(a.startedAt || '')))[0];
  const withDuration = route.sessions.filter(s => s.durationMs != null);
  const avgDuration = withDuration.length
    ? Math.round(withDuration.reduce((sum, s) => sum + Number(s.durationMs), 0) / withDuration.length)
    : null;
  const errors = route.sessions.filter(s => String(s.responseStatusCode || '').startsWith('5') || String(s.status || '') === 'ERROR').length;

  const row = document.createElement('div');
  row.className = `route-row${route.routeId === selectedRouteId ? ' active' : ''}${isOpen ? ' status-open' : isError ? ' status-error' : ''}`;
  row.dataset.action = 'select-route';
  row.dataset.routeId = route.routeId;

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
  reqPill.textContent = `${route.sessions.length} req`;
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
  button.className = 'route-action-btn';
  button.dataset.action = action;
  button.dataset.routeId = routeId;
  button.title = title;
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
      stateLabel: data.open > 0 ? 'Live' : 'Closed',
      stateClass: data.open > 0 ? 'state-live' : 'state-closed',
      pending: data.pending,
      showExport: true
    }),
    buildRouteStats(data.total, data.open, data.pending, data.pendingStatClass),
    buildRouteMetaGrid(data.clientAddress, data.selectedLabel)
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

function buildRouteStats(total, open, pending, pendingStatClass) {
  const stats = document.createElement('div');
  stats.className = 'route-stats';
  stats.append(
    buildRouteStatBlock(total, 'Total'),
    buildRouteStatBlock(open, 'Open'),
    buildRouteStatBlock(pending, 'Pending', pendingStatClass)
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

function buildRouteMetaGrid(clientAddress, selectedLabel) {
  const grid = document.createElement('div');
  grid.className = 'route-meta-grid';
  grid.append(
    buildRouteMetaItem('Client', clientAddress, 'mono route-meta-value'),
    buildRouteMetaItem('Selected request', selectedLabel, 'mono route-meta-value route-meta-selected')
  );
  return grid;
}

function buildRouteMetaItem(label, value, valueClass = '') {
  const item = document.createElement('div');
  const key = document.createElement('span');
  key.className = 'label';
  key.textContent = label;
  const content = document.createElement('span');
  content.className = valueClass;
  content.textContent = value;
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
