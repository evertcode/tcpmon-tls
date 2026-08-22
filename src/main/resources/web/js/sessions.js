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
  const params = new URLSearchParams({ limit });
  if (routeId) params.set('routeId', routeId);
  if (method) params.set('method', method);
  if (statusCode) params.set('statusCode', statusCode);
  if (q) params.set('q', q);

  const facetsParams = new URLSearchParams();
  if (routeId) facetsParams.set('routeId', routeId);

  const [requestsData, facetsData] = await Promise.all([
    fetchJson('/api/requests?' + params),
    fetchJson('/api/request-facets?' + facetsParams)
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

function showRequestTableSkeleton() {
  const container = document.getElementById('request-table');
  if (!container) return;
  container.replaceChildren(buildSkeleton('table', Number(getState('requestPageSize')) > 10 ? 6 : 4));
}

async function loadRequestsPage(routeId, cursor, resetStack) {
  showRequestTableSkeleton();
  const method = getState('requestMethodFilterValue');
  const statusCode = getState('requestStatusCodeFilterValue');
  const q = getState('requestSearchValue').trim();
  const limit = String(getState('requestPageSize') || 10);
  const params = new URLSearchParams({ limit });
  if (routeId) params.set('routeId', routeId);
  if (cursor) params.set('cursor', cursor);
  if (method) params.set('method', method);
  if (statusCode) params.set('statusCode', statusCode);
  if (q) params.set('q', q);

  let requestsData;
  try {
    requestsData = await fetchJson('/api/requests?' + params);
  } catch (error) {
    const container = document.getElementById('request-table');
    if (container) {
      container.replaceChildren(buildErrorState(
        'Could not load this data.',
        'Retry',
        () => loadRequestsPage(routeId, cursor, resetStack)
      ));
    }
    throw error;
  }

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

async function toggleSearchAllRoutes(checked) {
  setState('searchAllRoutes', checked);
  const routeId = checked ? null : getState('activeRoute');
  if (checked || routeId) {
    await loadRequestsForRoute(routeId);
  }
  await renderApp({ detail: false });
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

function buildAllRoutesToggleRow(checked) {
  const row = document.createElement('label');
  row.className = 'request-all-routes-toggle';
  const checkbox = document.createElement('input');
  checkbox.type = 'checkbox';
  checkbox.id = 'request-search-all-routes';
  checkbox.checked = Boolean(checked);
  row.append(checkbox, document.createTextNode(' Search all routes'));
  return row;
}

function renderRequestTable() {
  const requestRows = requestRowsForActiveRoute();
  const container = document.getElementById('request-table');
  const searchVal = getState('requestSearchValue');
  const methodVal = getState('requestMethodFilterValue');
  const statusVal = getState('requestStatusCodeFilterValue');
  const hasActiveFilters = Boolean(searchVal || methodVal || statusVal);
  const searchAllRoutes = getState('searchAllRoutes');
  const toggleRow = buildAllRoutesToggleRow(searchAllRoutes);

  if (!requestRows.length) {
    const facets = getState('requestFacets');
    const total = facets ? Number(facets.totalRequests || 0) : 0;
    let emptyContent;
    if (total > 0) {
      const clearBtn = document.createElement('button');
      clearBtn.className = 'utility';
      clearBtn.textContent = 'Clear filters';
      clearBtn.dataset.action = 'clear-request-filters';
      const filterSummary = activeFilterSummary();
      emptyContent = buildEmptyState(
        filterSummary ? `No requests match ${filterSummary}.` : 'No requests match the current filters.',
        'Clear filters or broaden the search query.',
        clearBtn
      );
    } else if (searchAllRoutes) {
      emptyContent = buildEmptyState('No traffic captured on any route yet.', 'Send a request through one of your routes to see it here.');
    } else {
      const listener = routeEndpointLabel(getState('activeRoute'), 'listener');
      emptyContent = buildEmptyState(
        listener ? `No traffic captured on ${listener}.` : 'No traffic captured for this route yet.',
        activeRouteCaptureHint()
      );
    }
    container.replaceChildren(toggleRow, emptyContent);
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
  searchInput.placeholder = 'Filter path, host, header, or client';
  toolbar.appendChild(searchInput);

  const methodFilter = buildSelectElement('request-method-filter', renderMethodOptions());
  const statusCodeFilter = buildSelectElement('request-status-code-filter', renderStatusCodeOptions());
  const pageSizeFilter = buildSelectElement('request-page-size', renderPageSizeOptions());
  const densityFilter = buildSelectElement('request-density', renderDensityOptions());
  densityFilter.setAttribute('aria-label', 'Row density');
  toolbar.append(methodFilter, statusCodeFilter, pageSizeFilter, densityFilter);

  if (hasActiveFilters) {
    const clearBtn = document.createElement('button');
    clearBtn.className = 'utility danger';
    clearBtn.textContent = 'Clear filters';
    clearBtn.dataset.action = 'clear-request-filters';
    toolbar.appendChild(clearBtn);
  }

  card.appendChild(toolbar);
  card.appendChild(renderRequestTableContent(requestRows, searchAllRoutes));
  container.replaceChildren(toggleRow, card);
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

function renderRequestTableContent(requestRows, showRoute = false) {
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
  const scroller = document.createElement('div');
  scroller.className = 'request-table-scroll';
  scroller.appendChild(buildRequestTableElement(requestRows, activeSession, activeExchangeIndex, showRoute, {
    sortKey: getState('requestSortKey'),
    sortDirection: getState('requestSortDirection'),
    density: getState('tableDensity')
  }));
  fragment.appendChild(scroller);
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

function renderDensityOptions() {
  const current = getState('tableDensity') === 'compact' ? 'compact' : 'comfortable';
  return [
    { value: 'comfortable', label: 'Comfortable', selected: current === 'comfortable' },
    { value: 'compact', label: 'Compact', selected: current === 'compact' }
  ];
}

function toggleRequestSort(sortKey) {
  if (!REQUEST_SORT_ACCESSORS[sortKey]) return;
  const currentKey = getState('requestSortKey');
  const currentDirection = getState('requestSortDirection');
  if (currentKey === sortKey && currentDirection === 'desc') {
    patchState({ requestSortDirection: 'asc' });
  } else if (currentKey === sortKey && currentDirection === 'asc') {
    patchState({ requestSortKey: null, requestSortDirection: 'desc' });
  } else {
    patchState({ requestSortKey: sortKey, requestSortDirection: 'desc' });
  }
  renderRequestTable();
}

const TABLE_DENSITY_STORAGE_KEY = 'tcpmon-table-density';

function initializeTableDensity() {
  let stored = null;
  try {
    stored = localStorage.getItem(TABLE_DENSITY_STORAGE_KEY);
  } catch (_) {
    stored = null;
  }
  setState('tableDensity', stored === 'compact' ? 'compact' : 'comfortable');
}

function setTableDensity(density) {
  const next = density === 'compact' ? 'compact' : 'comfortable';
  setState('tableDensity', next);
  try {
    localStorage.setItem(TABLE_DENSITY_STORAGE_KEY, next);
  } catch (_) {
    // A browser that blocks storage still applies the density for this page load.
  }
  renderRequestTable();
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
  const searchAllRoutes = getState('searchAllRoutes');
  if (!activeRoute && !searchAllRoutes) return;
  await loadRequestsForRoute(searchAllRoutes ? null : activeRoute);
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
    const searchAllRoutes = getState('searchAllRoutes');
    if (!activeRoute && !searchAllRoutes) return;
    await loadRequestsForRoute(searchAllRoutes ? null : activeRoute);
    renderRequestTable();
  }, 300));
}

async function changeRequestPage(delta) {
  const activeRouteId = getState('activeRoute');
  const searchAllRoutes = getState('searchAllRoutes');
  if (!activeRouteId && !searchAllRoutes) return;
  const effectiveRouteId = searchAllRoutes ? null : activeRouteId;
  if (delta > 0) {
    const nextCursor = getState('requestNextCursor');
    if (!nextCursor) return;
    const currentCursor = getState('requestCurrentCursor');
    setState('requestCursorStack', [...getState('requestCursorStack'), currentCursor]);
    await loadRequestsPage(effectiveRouteId, nextCursor, false);
  } else {
    const stack = [...getState('requestCursorStack')];
    if (!stack.length) return;
    const prevCursor = stack.pop();
    setState('requestCursorStack', stack);
    await loadRequestsPage(effectiveRouteId, prevCursor, false);
  }
  renderRequestTable();
}

const REQUEST_SORT_ACCESSORS = {
  route: row => String(row.routeId || ''),
  method: row => String(row.requestMethod || ''),
  path: row => String(row.requestPath || ''),
  response: row => Number(row.responseStatusCode) || 0,
  duration: row => (row.durationMs == null ? -1 : Number(row.durationMs)),
  size: row => Number(row.responseSizeBytes || 0),
  client: row => String(row.clientAddress || ''),
  started: row => String(row.startedAt || '')
};

const REQUEST_TABLE_COLUMNS = [
  ['route', 'Route'],
  ['method', 'Method'],
  ['path', 'Path'],
  ['response', 'Response'],
  ['duration', 'Duration'],
  ['size', 'Size'],
  ['client', 'Client'],
  ['started', 'Started']
];

/**
 * Sorts the rows of the current page. The sort is stable: rows that compare
 * equal keep the order the server sent.
 */
function sortRequestRows(rows, sortKey, sortDirection) {
  const list = Array.isArray(rows) ? rows.slice() : [];
  const accessor = REQUEST_SORT_ACCESSORS[sortKey];
  if (!accessor) return list;
  const factor = sortDirection === 'asc' ? 1 : -1;
  return list
    .map((row, index) => ({ row, index }))
    .sort((left, right) => {
      const a = accessor(left.row);
      const b = accessor(right.row);
      if (a < b) return -factor;
      if (a > b) return factor;
      return left.index - right.index;
    })
    .map(entry => entry.row);
}

function buildRequestSortHeader(key, label, sortKey, sortDirection) {
  const th = document.createElement('th');
  th.dataset.action = 'sort-requests';
  th.dataset.sortKey = key;
  th.tabIndex = 0;
  th.className = 'sortable-header';
  th.title = 'Sort the requests on this page';
  const isActive = key === sortKey;
  th.setAttribute('aria-sort', isActive ? (sortDirection === 'asc' ? 'ascending' : 'descending') : 'none');
  if (isActive) th.classList.add('is-sorted');

  const text = document.createElement('span');
  text.textContent = label;
  th.appendChild(text);
  if (isActive) {
    th.appendChild(buildIcon(sortDirection === 'asc' ? 'chevron-up' : 'chevron-down'));
  }
  return th;
}

function buildRequestTableElement(pageItems, activeSession, activeExchangeIndex, showRoute = false, viewOptions = {}) {
  const sortKey = viewOptions.sortKey || null;
  const sortDirection = viewOptions.sortDirection === 'asc' ? 'asc' : 'desc';
  const density = viewOptions.density === 'compact' ? 'compact' : 'comfortable';

  const table = document.createElement('table');
  table.className = 'request-table' + (density === 'compact' ? ' request-table-compact' : '');
  table.dataset.density = density;

  const thead = document.createElement('thead');
  const headerRow = document.createElement('tr');
  const columns = showRoute
    ? REQUEST_TABLE_COLUMNS
    : REQUEST_TABLE_COLUMNS.filter(column => column[0] !== 'route');
  for (const [key, label] of columns) {
    headerRow.appendChild(buildRequestSortHeader(key, label, sortKey, sortDirection));
  }
  thead.appendChild(headerRow);

  const tbody = document.createElement('tbody');
  for (const request of sortRequestRows(pageItems, sortKey, sortDirection)) {
    const row = document.createElement('tr');
    const exchangeIndex = Number(request.exchangeIndex || 0);
    row.className = `session-entry${request.sessionId === activeSession && exchangeIndex === activeExchangeIndex ? ' active' : ''}`;
    row.dataset.action = 'select-session';
    row.dataset.sessionId = request.sessionId;
    row.dataset.exchangeIndex = String(exchangeIndex);
    row.dataset.routeId = request.routeId || '';
    row.tabIndex = 0;
    row.setAttribute('aria-selected', String(request.sessionId === activeSession && exchangeIndex === activeExchangeIndex));

    if (showRoute) {
      row.appendChild(buildTextCell(request.routeId || '', 'mono'));
    }
    row.appendChild(buildMethodCell(request.requestMethod || ''));
    row.appendChild(buildPathCell(request));
    row.appendChild(buildStatusCell(request));
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

function methodBadgeClass(method) {
  switch ((method || '').toUpperCase()) {
    case 'GET':     return 'method-get';
    case 'POST':    return 'method-post';
    case 'PUT':     return 'method-put';
    case 'DELETE':  return 'method-delete';
    case 'PATCH':   return 'method-patch';
    case 'HEAD':    return 'method-head';
    case 'OPTIONS': return 'method-options';
    default:        return 'method-other';
  }
}

function buildMethodCell(method) {
  const cell = document.createElement('td');
  if (!method) return cell;
  const badge = document.createElement('span');
  badge.className = `method-badge ${methodBadgeClass(method)}`;
  badge.textContent = method;
  cell.appendChild(badge);
  return cell;
}

function buildPathCell(session) {
  const cell = document.createElement('td');
  cell.className = 'mono url-cell';
  cell.title = session.requestPath || session.sessionId || '';
  cell.textContent = session.requestPath || session.sessionId.slice(0, 8) + '\u2026';
  return cell;
}

function buildStatusCell(request) {
  const cell = document.createElement('td');
  const value = String(request.responseStatusCode ?? '');
  const badge = document.createElement('span');
  if (value) {
    const first = value.charAt(0);
    const cls = first === '2' ? 'status-2xx' : first === '3' ? 'status-3xx' : first === '4' ? 'status-4xx' : first === '5' ? 'status-5xx' : 'status-other';
    badge.className = `status-badge ${cls}`;
    badge.textContent = value;
  } else if (request.live) {
    badge.className = 'status-badge status-pending';
    badge.textContent = 'Pending';
  } else if (request.durationMs == null && request.responseSizeBytes == null) {
    badge.className = 'status-badge status-no-response';
    badge.textContent = 'No response';
  } else {
    return cell;
  }
  cell.appendChild(badge);
  if (request.mocked) {
    const mockedBadge = document.createElement('span');
    mockedBadge.className = 'status-badge status-mocked';
    mockedBadge.textContent = 'Mocked';
    mockedBadge.title = "This response was served by the route's mock, not the target";
    cell.appendChild(mockedBadge);
  }
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
    span.className = latencyClass(value);
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

async function selectSessionRow(sessionId, exchangeIndex, routeId) {
  if (routeId && (getState('searchAllRoutes') || routeId !== getState('activeRoute'))) {
    setState('searchAllRoutes', false);
    await selectRoute(routeId);
  }
  await selectSession(sessionId, exchangeIndex);
}
