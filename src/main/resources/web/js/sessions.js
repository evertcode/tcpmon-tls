async function refreshSessions(preserveSelection = true) {
  return refreshSessionsView(preserveSelection, true);
}

async function refreshSessionsView(preserveSelection = true, refreshDetail = true) {
  const data = await fetchJson('/api/sessions');
  setState('allSessions', Array.isArray(data.sessions) ? data.sessions : []);
  setState('routeStats', data.routeStats || {});
  const sessions = getState('allSessions');
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
  const needsRouteChange = !preserveSelection || !selectedRouteId || !routes.some(r => r.routeId === selectedRouteId);
  const targetRouteId = needsRouteChange ? routes[0].routeId : selectedRouteId;

  if (needsRouteChange) {
    setState('activeRoute', targetRouteId);
    patchState({
      requestSearchValue: '',
      requestMethodFilterValue: '',
      requestStatusCodeFilterValue: ''
    });
  }

  await loadRequestsForRoute(targetRouteId);

  const routeRequests = getState('requestRows');
  const routeSessions = sessionsForActiveRoute();
  const hasSelectedRequest = preserveSelection
    && selectedSessionId
    && routeRequests.some(r => r.sessionId === selectedSessionId && Number(r.exchangeIndex || 0) === selectedExchangeIndex);
  let autoSelected = false;
  if (routeRequests.length) {
    if (!hasSelectedRequest) {
      patchState({
        activeSession: routeRequests[0].sessionId,
        activeExchangeIndex: Number(routeRequests[0].exchangeIndex || 0)
      });
      autoSelected = true;
    }
  } else if (!preserveSelection || !selectedSessionId || !routeSessions.some(s => s.sessionId === selectedSessionId)) {
    patchState({
      activeSession: routeSessions[0] ? routeSessions[0].sessionId : null,
      activeExchangeIndex: 0
    });
    autoSelected = true;
  }

  await renderApp({
    detail: refreshDetail || autoSelected || !getState('activeSession')
  });
}

async function loadRequestsForRoute(routeId) {
  const method = getState('requestMethodFilterValue');
  const statusCode = getState('requestStatusCodeFilterValue');
  const q = getState('requestSearchValue').trim();
  const limit = String(getState('requestPageSize') || 10);
  const params = new URLSearchParams({ routeId, limit });
  if (method) params.set('method', method);
  if (statusCode) params.set('statusCode', statusCode);
  if (q) params.set('q', q);

  const [requestsData, facetsData] = await Promise.all([
    fetchJson('/api/requests?' + params),
    fetchJson('/api/request-facets?routeId=' + encodeURIComponent(routeId))
  ]);

  patchState({
    requestCurrentCursor: null,
    requestNextCursor: requestsData.nextCursor || null,
    requestHasMore: Boolean(requestsData.hasMore),
    requestCursorStack: [],
    requestRows: Array.isArray(requestsData.requests) ? requestsData.requests : [],
    requestFacets: facetsData || null
  });
}

async function loadRequestsPage(routeId, cursor, resetStack) {
  const method = getState('requestMethodFilterValue');
  const statusCode = getState('requestStatusCodeFilterValue');
  const q = getState('requestSearchValue').trim();
  const limit = String(getState('requestPageSize') || 10);
  const params = new URLSearchParams({ routeId, limit });
  if (cursor) params.set('cursor', cursor);
  if (method) params.set('method', method);
  if (statusCode) params.set('statusCode', statusCode);
  if (q) params.set('q', q);

  const requestsData = await fetchJson('/api/requests?' + params);

  if (resetStack) {
    patchState({ requestCursorStack: [] });
  }
  patchState({
    requestCurrentCursor: cursor || null,
    requestNextCursor: requestsData.nextCursor || null,
    requestHasMore: Boolean(requestsData.hasMore),
    requestRows: Array.isArray(requestsData.requests) ? requestsData.requests : []
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
  return getState('requestRows');
}

function renderRequestTable() {
  const requestRows = requestRowsForActiveRoute();
  const container = document.getElementById('request-table');
  const searchVal = getState('requestSearchValue');
  const methodVal = getState('requestMethodFilterValue');
  const statusVal = getState('requestStatusCodeFilterValue');
  const hasActiveFilters = Boolean(searchVal || methodVal || statusVal);

  if (!requestRows.length) {
    const facets = getState('requestFacets');
    const total = facets ? Number(facets.totalRequests || 0) : 0;
    const empty = document.createElement('div');
    empty.className = 'empty';
    if (total > 0) {
      const inner = document.createElement('div');
      inner.style.display = 'flex';
      inner.style.flexDirection = 'column';
      inner.style.alignItems = 'center';
      inner.style.gap = '10px';
      const msg = document.createElement('span');
      msg.textContent = 'No requests match the current filters.';
      const clearBtn = document.createElement('button');
      clearBtn.className = 'utility';
      clearBtn.textContent = 'Clear filters';
      clearBtn.dataset.action = 'clear-request-filters';
      inner.append(msg, clearBtn);
      empty.appendChild(inner);
    } else {
      empty.textContent = 'No HTTP requests captured for this route yet.';
    }
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
  searchInput.value = searchVal;
  searchInput.placeholder = 'Filter requests in this route';
  toolbar.appendChild(searchInput);

  const methodFilter = buildSelectElement('request-method-filter', renderMethodOptions());
  const statusCodeFilter = buildSelectElement('request-status-code-filter', renderStatusCodeOptions());
  const pageSizeFilter = buildSelectElement('request-page-size', renderPageSizeOptions());
  toolbar.append(methodFilter, statusCodeFilter, pageSizeFilter);

  if (hasActiveFilters) {
    const clearBtn = document.createElement('button');
    clearBtn.className = 'utility danger';
    clearBtn.textContent = 'Clear filters';
    clearBtn.dataset.action = 'clear-request-filters';
    toolbar.appendChild(clearBtn);
  }

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
  const hasMore = getState('requestHasMore');
  const cursorStack = getState('requestCursorStack');
  const hasPrev = cursorStack.length > 0;
  const facets = getState('requestFacets') || {};
  const totalRequests = facets.totalRequests != null ? Number(facets.totalRequests) : null;
  const pageSize = getState('requestPageSize') || 10;
  const rangeStart = cursorStack.length * pageSize + 1;
  const rangeEnd = rangeStart + requestRows.length - 1;
  const activeSession = getState('activeSession');
  const activeExchangeIndex = getState('activeExchangeIndex');
  const fragment = document.createDocumentFragment();
  fragment.appendChild(buildRequestTableElement(requestRows, activeSession, activeExchangeIndex));
  fragment.appendChild(buildRequestTableFooter(rangeStart, rangeEnd, totalRequests, hasPrev, hasMore));
  return fragment;
}

function renderMethodOptions() {
  const requestMethodFilterValue = getState('requestMethodFilterValue');
  const facets = getState('requestFacets') || {};
  const methods = facets.methods || [];
  return [{ value: '', label: 'All methods', selected: requestMethodFilterValue === '' }]
    .concat(methods.map(method => ({
      value: method,
      label: method,
      selected: method === requestMethodFilterValue
    })));
}

function renderPageSizeOptions() {
  const current = getState('requestPageSize') || 10;
  return [10, 25, 50, 100].map(n => ({ value: String(n), label: `${n} / page`, selected: n === current }));
}

function renderStatusCodeOptions() {
  const requestStatusCodeFilterValue = getState('requestStatusCodeFilterValue');
  const facets = getState('requestFacets') || {};
  const codes = facets.statusCodes || [];
  return [{ value: '', label: 'All responses', selected: requestStatusCodeFilterValue === '' }]
    .concat(codes.map(code => ({
      value: code,
      label: code,
      selected: code === requestStatusCodeFilterValue
    })));
}

async function resetRequestPageAndRender() {
  const pageSizeEl = document.getElementById('request-page-size');
  patchState({
    requestSearchValue: document.getElementById('request-search')?.value ?? getState('requestSearchValue'),
    requestMethodFilterValue: document.getElementById('request-method-filter')?.value ?? getState('requestMethodFilterValue'),
    requestStatusCodeFilterValue: document.getElementById('request-status-code-filter')?.value ?? getState('requestStatusCodeFilterValue'),
    requestPageSize: pageSizeEl ? Number(pageSizeEl.value) : getState('requestPageSize')
  });
  const activeRoute = getState('activeRoute');
  if (!activeRoute) return;
  await loadRequestsForRoute(activeRoute);
  renderRequestTable();
}

function debounceRequestSearch() {
  patchState({
    requestSearchValue: document.getElementById('request-search')?.value ?? getState('requestSearchValue')
  });
  const timer = getState('requestSearchDebounceTimer');
  if (timer) clearTimeout(timer);
  setState('requestSearchDebounceTimer', setTimeout(async () => {
    setState('requestSearchDebounceTimer', null);
    const activeRoute = getState('activeRoute');
    if (!activeRoute) return;
    await loadRequestsForRoute(activeRoute);
    renderRequestTable();
  }, 300));
}

async function changeRequestPage(delta) {
  const activeRouteId = getState('activeRoute');
  if (!activeRouteId) return;
  if (delta > 0) {
    const nextCursor = getState('requestNextCursor');
    if (!nextCursor) return;
    const currentCursor = getState('requestCurrentCursor');
    setState('requestCursorStack', [...getState('requestCursorStack'), currentCursor]);
    await loadRequestsPage(activeRouteId, nextCursor, false);
  } else {
    const stack = [...getState('requestCursorStack')];
    if (!stack.length) return;
    const prevCursor = stack.pop();
    setState('requestCursorStack', stack);
    await loadRequestsPage(activeRouteId, prevCursor, false);
  }
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

function buildRequestTableFooter(rangeStart, rangeEnd, totalRequests, hasPrev, hasMore) {
  const footer = document.createElement('div');
  footer.className = 'table-footer';

  const summary = document.createElement('div');
  summary.className = 'muted';
  summary.textContent = totalRequests != null
    ? `Showing ${rangeStart}–${rangeEnd} of ${totalRequests} requests`
    : `Showing ${rangeStart}–${rangeEnd} requests`;

  const pager = document.createElement('div');
  pager.className = 'pager';
  pager.append(
    buildPagerButton('Previous', -1, !hasPrev),
    buildPagerButton('Next', 1, !hasMore)
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
