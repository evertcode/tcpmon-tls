async function refreshSessions(preserveSelection = true) {
  return refreshSessionsView(preserveSelection, true);
}

async function refreshSessionsView(preserveSelection = true, refreshDetail = true) {
  const data = await fetchJson('/api/sessions');
  setState('allSessions', Array.isArray(data.sessions) ? data.sessions : []);
  setState('allRequests', Array.isArray(data.requests) ? data.requests : []);
  const sessions = getState('allSessions');
  const requestRows = getState('allRequests');
  const selectedRouteId = getState('activeRoute');
  const selectedSessionId = getState('activeSession');
  const selectedExchangeIndex = getState('activeExchangeIndex');

  if (!sessions.length) {
    renderApp({
      detail: true,
      detailEmptyMessage: 'No sessions yet.'
    });
    return;
  }

  const routes = groupedRoutes();
  if (!preserveSelection || !selectedRouteId || !routes.some(route => route.routeId === selectedRouteId)) {
    setState('activeRoute', routes[0].routeId);
  }

  const routeSessions = sessionsForActiveRoute();
  const routeRequests = requestRowsForActiveRoute();
  const hasSelectedRequest = preserveSelection
    && selectedSessionId
    && routeRequests.some(request => request.sessionId === selectedSessionId && Number(request.exchangeIndex || 0) === selectedExchangeIndex);
  if (routeRequests.length) {
    if (!hasSelectedRequest) {
      patchState({
        activeSession: routeRequests[0].sessionId,
        activeExchangeIndex: Number(routeRequests[0].exchangeIndex || 0)
      });
    }
  } else if (!preserveSelection || !selectedSessionId || !routeSessions.some(session => session.sessionId === selectedSessionId)) {
    patchState({
      activeSession: routeSessions[0] ? routeSessions[0].sessionId : null,
      activeExchangeIndex: 0
    });
  }

  await renderApp({
    detail: refreshDetail || !getState('activeSession')
  });
}

function sessionsForActiveRoute() {
  const sessions = getState('allSessions');
  const selectedRouteId = getState('activeRoute');
  return sessions
    .filter(session => (session.routeId || 'default') === selectedRouteId)
    .sort((a, b) => String(b.startedAt || '').localeCompare(String(a.startedAt || '')));
}

function requestRowsForActiveRoute() {
  const requestRows = getState('allRequests');
  const selectedRouteId = getState('activeRoute');
  return requestRows
    .filter(request => (request.routeId || 'default') === selectedRouteId)
    .sort((a, b) => String(b.startedAt || '').localeCompare(String(a.startedAt || '')));
}

function renderRequestTable() {
  const requestRows = requestRowsForActiveRoute();
  const requestSearchValue = getState('requestSearchValue');
  const container = document.getElementById('request-table');
  if (!requestRows.length) {
    const empty = document.createElement('div');
    empty.className = 'empty';
    empty.textContent = 'No HTTP requests captured for this route yet.';
    container.replaceChildren(empty);
    return;
  }
  const card = document.createElement('section');
  card.className = 'table-card';

  const toolbar = document.createElement('div');
  toolbar.className = 'request-toolbar';

  const searchInput = document.createElement('input');
  searchInput.id = 'request-search';
  searchInput.type = 'search';
  searchInput.value = requestSearchValue;
  searchInput.placeholder = 'Filter requests in this route';
  toolbar.appendChild(searchInput);

  const methodFilter = buildSelectElement('request-method-filter', renderMethodOptions(requestRows));
  const statusCodeFilter = buildSelectElement('request-status-code-filter', renderStatusCodeOptions(requestRows));
  toolbar.append(methodFilter, statusCodeFilter);

  card.appendChild(toolbar);
  card.appendChild(renderRequestTableContent(requestRows));
  container.replaceChildren(card);
}

function buildSelectElement(id, options) {
  const select = document.createElement('select');
  select.id = id;
  for (const option of options) {
    const optionEl = document.createElement('option');
    optionEl.value = option.value;
    optionEl.textContent = option.label;
    optionEl.selected = option.selected;
    select.appendChild(optionEl);
  }
  return select;
}

function renderRequestTableContent(requestRows) {
  const requestSearchValue = getState('requestSearchValue');
  const requestMethodFilterValue = getState('requestMethodFilterValue');
  const requestStatusCodeFilterValue = getState('requestStatusCodeFilterValue');
  const requestPageSize = getState('requestPageSize');
  const query = requestSearchValue.trim().toLowerCase();
  const methodFilter = requestMethodFilterValue;
  const statusCodeFilter = requestStatusCodeFilterValue;
  const filtered = requestRows.filter(request => {
    if (methodFilter && String(request.requestMethod || '') !== methodFilter) return false;
    if (statusCodeFilter && String(request.responseStatusCode || '') !== statusCodeFilter) return false;
    if (!query) return true;
    return [
      request.sessionId,
      request.requestMethod,
      request.requestPath,
      request.responseStatusCode,
      request.clientAddress,
      request.targetAddress,
      request.startedAt,
      request.status
    ].join(' ').toLowerCase().includes(query);
  });
  if (!filtered.length) {
    setState('requestPage', 1);
    const empty = document.createElement('div');
    empty.className = 'empty';
    empty.textContent = 'No requests match the current filter.';
    return empty;
  }
  const totalPages = Math.max(1, Math.ceil(filtered.length / requestPageSize));
  setState('requestPage', Math.min(getState('requestPage'), totalPages));
  const requestPage = getState('requestPage');
  const activeSession = getState('activeSession');
  const activeExchangeIndex = getState('activeExchangeIndex');
  const pageStart = (requestPage - 1) * requestPageSize;
  const pageItems = filtered.slice(pageStart, pageStart + requestPageSize);
  const fragment = document.createDocumentFragment();
  fragment.appendChild(buildRequestTableElement(pageItems, activeSession, activeExchangeIndex));
  fragment.appendChild(buildRequestTableFooter(pageStart, pageItems.length, filtered.length, requestPage, totalPages));
  return fragment;
}

function renderMethodOptions(requestRows) {
  const requestMethodFilterValue = getState('requestMethodFilterValue');
  const methods = [...new Set(requestRows.map(request => String(request.requestMethod || '')).filter(Boolean))].sort();
  return [{ value: '', label: 'All methods', selected: requestMethodFilterValue === '' }]
    .concat(methods.map(method => ({
      value: method,
      label: method,
      selected: method === requestMethodFilterValue
    })));
}

function renderStatusCodeOptions(requestRows) {
  const requestStatusCodeFilterValue = getState('requestStatusCodeFilterValue');
  const statusCodes = [...new Set(requestRows.map(request => String(request.responseStatusCode || '')).filter(Boolean))].sort();
  return [{ value: '', label: 'All responses', selected: requestStatusCodeFilterValue === '' }]
    .concat(statusCodes.map(code => ({
      value: code,
      label: code,
      selected: code === requestStatusCodeFilterValue
    })));
}

function resetRequestPageAndRender() {
  const currentSearchValue = getState('requestSearchValue');
  const currentMethodFilterValue = getState('requestMethodFilterValue');
  const currentStatusCodeFilterValue = getState('requestStatusCodeFilterValue');
  patchState({
    requestSearchValue: document.getElementById('request-search') ? document.getElementById('request-search').value : currentSearchValue,
    requestMethodFilterValue: document.getElementById('request-method-filter') ? document.getElementById('request-method-filter').value : currentMethodFilterValue,
    requestStatusCodeFilterValue: document.getElementById('request-status-code-filter') ? document.getElementById('request-status-code-filter').value : currentStatusCodeFilterValue,
    requestPage: 1
  });
  renderRequestTable();
}

function debounceRequestSearch() {
  const currentSearchValue = getState('requestSearchValue');
  const requestSearchDebounceTimer = getState('requestSearchDebounceTimer');
  patchState({
    requestSearchValue: document.getElementById('request-search') ? document.getElementById('request-search').value : currentSearchValue,
    requestPage: 1
  });
  if (requestSearchDebounceTimer) {
    clearTimeout(requestSearchDebounceTimer);
  }
  setState('requestSearchDebounceTimer', setTimeout(() => {
    setState('requestSearchDebounceTimer', null);
    renderRequestTable();
  }, 250));
}

function changeRequestPage(delta) {
  setState('requestPage', Math.max(1, getState('requestPage') + delta));
  renderRequestTable();
}

function buildRequestTableElement(pageItems, activeSession, activeExchangeIndex) {
  const table = document.createElement('table');
  const thead = document.createElement('thead');
  const headerRow = document.createElement('tr');
  for (const label of ['Method', 'Path', 'Response', 'Duration', 'Size', 'Client', 'Started']) {
    const th = document.createElement('th');
    th.textContent = label;
    headerRow.appendChild(th);
  }
  thead.appendChild(headerRow);

  const tbody = document.createElement('tbody');
  for (const request of pageItems) {
    const row = document.createElement('tr');
    const exchangeIndex = Number(request.exchangeIndex || 0);
    row.className = `session-entry${request.sessionId === activeSession && exchangeIndex === activeExchangeIndex ? ' active' : ''}`;
    row.dataset.action = 'select-session';
    row.dataset.sessionId = request.sessionId;
    row.dataset.exchangeIndex = String(exchangeIndex);

    row.appendChild(buildTextCell(request.requestMethod || ''));
    row.appendChild(buildPathCell(request));
    row.appendChild(buildStatusCell(request.responseStatusCode));
    row.appendChild(buildDurationCell(request.durationMs));
    row.appendChild(buildBytesCell(request.responseSizeBytes));
    row.appendChild(buildTextCell(request.clientAddress || '', 'mono'));
    row.appendChild(buildTextCell(formatTime(request.startedAt)));
    tbody.appendChild(row);
  }

  table.append(thead, tbody);
  return table;
}

function buildRequestTableFooter(pageStart, pageItemCount, filteredCount, requestPage, totalPages) {
  const footer = document.createElement('div');
  footer.className = 'table-footer';

  const summary = document.createElement('div');
  summary.className = 'muted';
  summary.textContent = `Showing ${pageStart + 1}-${pageStart + pageItemCount} of ${filteredCount} requests`;

  const pager = document.createElement('div');
  pager.className = 'pager';
  pager.append(
    buildPagerButton('Previous', -1, requestPage === 1),
    buildMutedSpan(`Page ${requestPage} / ${totalPages}`),
    buildPagerButton('Next', 1, requestPage >= totalPages)
  );

  footer.append(summary, pager);
  return footer;
}

function buildPagerButton(label, delta, disabled) {
  const button = document.createElement('button');
  button.className = 'secondary nav';
  button.textContent = label;
  button.disabled = disabled;
  button.dataset.action = 'change-request-page';
  button.dataset.delta = String(delta);
  return button;
}

function buildMutedSpan(text) {
  const span = document.createElement('span');
  span.className = 'muted';
  span.textContent = text;
  return span;
}

function buildTextCell(text, className = '') {
  const cell = document.createElement('td');
  if (className) {
    cell.className = className;
  }
  cell.textContent = text;
  return cell;
}

function buildPathCell(session) {
  const cell = document.createElement('td');
  cell.className = 'mono url-cell';
  cell.title = session.requestPath || session.sessionId || '';
  cell.textContent = session.requestPath || session.sessionId.slice(0, 8) + '\u2026';
  return cell;
}

function buildStatusCell(code) {
  const cell = document.createElement('td');
  const value = String(code ?? '');
  if (!value) {
    return cell;
  }
  const badge = document.createElement('span');
  const first = value.charAt(0);
  const cls = first === '2' ? 'status-2xx' : first === '3' ? 'status-3xx' : first === '4' ? 'status-4xx' : first === '5' ? 'status-5xx' : 'status-other';
  badge.className = `status-badge ${cls}`;
  badge.textContent = value;
  cell.appendChild(badge);
  return cell;
}

function buildDurationCell(ms) {
  const cell = document.createElement('td');
  const value = Number(ms);
  const span = document.createElement('span');
  if (ms == null || Number.isNaN(value)) {
    span.className = 'muted';
    span.textContent = '—';
  } else {
    span.className = value < 200 ? 'timing-fast' : value < 1000 ? 'timing-medium' : 'timing-slow';
    span.textContent = value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(1)} s`;
  }
  cell.appendChild(span);
  return cell;
}

function buildBytesCell(bytes) {
  const cell = document.createElement('td');
  const span = document.createElement('span');
  if (bytes == null) {
    span.className = 'muted';
    span.textContent = '—';
  } else {
    const value = Number(bytes);
    if (Number.isNaN(value) || value === 0) {
      span.className = 'muted';
      span.textContent = '0 B';
    } else if (value < 1024) {
      span.textContent = `${value} B`;
    } else if (value < 1048576) {
      span.textContent = `${(value / 1024).toFixed(1)} KB`;
    } else {
      span.textContent = `${(value / 1048576).toFixed(1)} MB`;
    }
  }
  cell.appendChild(span);
  return cell;
}

async function selectSession(sessionId, exchangeIndex = 0) {
  patchState({
    activeSession: sessionId,
    activeExchangeIndex: Number(exchangeIndex || 0),
    diffMode: false
  });
  await renderApp({
    banner: false,
    detail: true
  });
}
