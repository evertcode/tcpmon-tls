const OVERVIEW_WINDOW_STORAGE_KEY = 'tcpmon-overview-window';

const OVERVIEW_WINDOWS = [
  [15, 'Last 15 minutes'],
  [60, 'Last hour'],
  [1440, 'Last 24 hours']
];

const OVERVIEW_DEGRADED_STATUSES = new Set(['degraded', 'failing']);

function initializeOverviewWindow() {
  let stored = null;
  try {
    stored = localStorage.getItem(OVERVIEW_WINDOW_STORAGE_KEY);
  } catch (_) {
    stored = null;
  }
  const parsed = Number(stored);
  const allowed = OVERVIEW_WINDOWS.some(option => option[0] === parsed);
  setState('overviewWindowMinutes', allowed ? parsed : 60);
}

function setOverviewWindow(windowMinutes) {
  const parsed = Number(windowMinutes);
  if (!OVERVIEW_WINDOWS.some(option => option[0] === parsed)) return;
  setState('overviewWindowMinutes', parsed);
  try {
    localStorage.setItem(OVERVIEW_WINDOW_STORAGE_KEY, String(parsed));
  } catch (_) {
    // A browser that blocks storage still shows the chosen window for this page load.
  }
  setState('overviewData', null);
  return loadOverview();
}

async function loadOverview() {
  if (getState('overviewRefreshInFlight')) return;
  const container = document.getElementById('overview');
  if (container && !container.children.length) {
    container.replaceChildren(buildSkeleton('table', 5));
  }
  setState('overviewRefreshInFlight', true);
  try {
    const payload = await fetchJson('/api/overview?windowMinutes=' + getState('overviewWindowMinutes'));
    setState('overviewData', payload);
    renderOverview();
  } catch (error) {
    if (container) {
      container.replaceChildren(buildErrorState('Could not load this data.', 'Retry', () => loadOverview()));
    }
    throw error;
  } finally {
    setState('overviewRefreshInFlight', false);
  }
}

function buildOverviewViewModel(payload) {
  const totalsSource = payload?.totals || {};
  const routesSource = Array.isArray(payload?.routes) ? payload.routes : [];
  const pathsSource = Array.isArray(payload?.slowestPaths) ? payload.slowestPaths : [];
  const totalRequests = Number(totalsSource.requests || 0);

  const routes = routesSource.map(route => {
    const requests = Number(route.requests || 0);
    const errors = Number(route.errors || 0);
    const status = String(route.status || 'idle');
    return {
      routeId: route.routeId,
      listener: route.listener || null,
      target: route.target || null,
      status,
      degraded: OVERVIEW_DEGRADED_STATUSES.has(status),
      requests,
      errors,
      clientErrors: Number(route.clientErrors || 0),
      errorRate: requests > 0 ? errors / requests : 0,
      p50Ms: route.p50Ms ?? null,
      p95Ms: route.p95Ms ?? null,
      sparkline: Array.isArray(route.sparkline) ? route.sparkline.map(Number) : []
    };
  });

  return {
    empty: totalRequests === 0,
    windowMinutes: Number(payload?.windowMinutes || 60),
    generatedAt: payload?.generatedAt || null,
    totals: {
      requests: totalRequests,
      errors: Number(totalsSource.errors || 0),
      clientErrors: Number(totalsSource.clientErrors || 0),
      errorRate: totalRequests > 0 ? Number(totalsSource.errors || 0) / totalRequests : 0,
      p50Ms: totalsSource.p50Ms ?? null,
      p95Ms: totalsSource.p95Ms ?? null
    },
    routes,
    slowestPaths: pathsSource.map(path => ({
      method: path.method,
      path: path.path,
      routeId: path.routeId,
      p95Ms: path.p95Ms ?? null,
      count: Number(path.count || 0)
    }))
  };
}

function buildSparkline(points, options = {}) {
  const values = Array.isArray(points) ? points.map(Number) : [];
  const width = Number(options.width || 96);
  const height = Number(options.height || 22);
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('class', 'sparkline');
  svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
  svg.setAttribute('width', String(width));
  svg.setAttribute('height', String(height));
  svg.setAttribute('aria-hidden', 'true');
  svg.setAttribute('focusable', 'false');
  if (!values.length) return svg;

  const peak = Math.max(1, ...values);
  const slot = width / values.length;
  const barWidth = Math.max(1, slot - 1);
  values.forEach((value, index) => {
    const barHeight = Math.max(1, Math.round((value / peak) * height));
    const bar = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
    bar.setAttribute('class', value > 0 ? 'sparkline-bar' : 'sparkline-bar sparkline-bar-empty');
    bar.setAttribute('x', String(Math.round(index * slot)));
    bar.setAttribute('y', String(height - barHeight));
    bar.setAttribute('width', String(Math.round(barWidth)));
    bar.setAttribute('height', String(barHeight));
    svg.appendChild(bar);
  });
  return svg;
}

function formatOverviewRate(rate) {
  const value = Number(rate || 0) * 100;
  if (value === 0) return '0%';
  return (value < 10 ? value.toFixed(1) : value.toFixed(0)) + '%';
}

function buildMetricCard(label, value, tone = '') {
  const card = document.createElement('div');
  card.className = 'metric-card' + (tone ? ' metric-card-' + tone : '');
  const valueEl = document.createElement('strong');
  valueEl.className = 'metric-value';
  valueEl.textContent = value;
  const labelEl = document.createElement('span');
  labelEl.className = 'metric-label';
  labelEl.textContent = label;
  card.append(valueEl, labelEl);
  return card;
}

function buildOverviewToolbar(viewModel) {
  const toolbar = document.createElement('header');
  toolbar.className = 'overview-toolbar';

  const heading = document.createElement('h2');
  heading.textContent = 'Overview';
  toolbar.appendChild(heading);

  const controls = document.createElement('div');
  controls.className = 'overview-controls';

  const label = document.createElement('label');
  label.className = 'sr-only';
  label.setAttribute('for', 'overview-window');
  label.textContent = 'Time window';

  const select = document.createElement('select');
  select.id = 'overview-window';
  select.className = 'theme-select';
  select.setAttribute('aria-label', 'Time window');
  for (const [value, text] of OVERVIEW_WINDOWS) {
    const option = document.createElement('option');
    option.value = String(value);
    option.textContent = text;
    select.appendChild(option);
  }
  select.value = String(viewModel.windowMinutes);

  const refresh = document.createElement('button');
  refresh.className = 'utility';
  refresh.dataset.action = 'refresh-overview';
  setButtonContent(refresh, 'Refresh', 'refresh');

  controls.append(label, select, refresh);
  toolbar.appendChild(controls);
  return toolbar;
}

function buildRouteHealthTable(viewModel) {
  const card = document.createElement('section');
  card.className = 'table-card';

  const title = document.createElement('h3');
  title.textContent = 'Route health';
  card.appendChild(title);

  const table = document.createElement('table');
  table.className = 'overview-table';
  const head = document.createElement('thead');
  const headRow = document.createElement('tr');
  for (const column of ['Route', 'Status', 'Requests', 'Error rate', 'p50 latency', 'p95 latency', 'Traffic']) {
    const cell = document.createElement('th');
    cell.textContent = column;
    headRow.appendChild(cell);
  }
  head.appendChild(headRow);
  table.appendChild(head);

  const body = document.createElement('tbody');
  for (const route of viewModel.routes) {
    const row = document.createElement('tr');
    row.className = 'overview-row';
    row.dataset.action = 'open-route';
    row.dataset.routeId = route.routeId;
    row.tabIndex = 0;

    const routeCell = document.createElement('td');
    const routeName = document.createElement('strong');
    routeName.textContent = route.routeId;
    routeCell.appendChild(routeName);
    if (route.listener && route.target) {
      const flow = document.createElement('span');
      flow.className = 'muted overview-flow';
      flow.textContent = route.listener + ' → ' + route.target;
      routeCell.appendChild(flow);
    }
    row.appendChild(routeCell);

    const statusCell = document.createElement('td');
    const badge = document.createElement('span');
    badge.className = 'pill overview-status overview-status-' + route.status;
    badge.textContent = route.status;
    statusCell.appendChild(badge);
    row.appendChild(statusCell);

    const requestsCell = document.createElement('td');
    requestsCell.textContent = String(route.requests);
    row.appendChild(requestsCell);

    const errorCell = document.createElement('td');
    errorCell.className = route.degraded ? 'overview-error-rate is-degraded' : 'overview-error-rate';
    errorCell.textContent = formatOverviewRate(route.errorRate);
    row.appendChild(errorCell);

    const p50Cell = document.createElement('td');
    p50Cell.innerHTML = formatDuration(route.p50Ms);
    row.appendChild(p50Cell);

    const p95Cell = document.createElement('td');
    p95Cell.innerHTML = formatDuration(route.p95Ms);
    row.appendChild(p95Cell);

    const sparkCell = document.createElement('td');
    sparkCell.appendChild(buildSparkline(route.sparkline));
    row.appendChild(sparkCell);

    body.appendChild(row);
  }
  table.appendChild(body);
  card.appendChild(table);
  return card;
}

function buildSlowestPathsTable(viewModel) {
  const card = document.createElement('section');
  card.className = 'table-card';

  const title = document.createElement('h3');
  title.textContent = 'Slowest paths';
  card.appendChild(title);

  if (!viewModel.slowestPaths.length) {
    card.appendChild(buildEmptyState('No timed requests in this window', 'A request needs a completed response before it can be ranked.'));
    return card;
  }

  const table = document.createElement('table');
  table.className = 'overview-table';
  const head = document.createElement('thead');
  const headRow = document.createElement('tr');
  for (const column of ['Method', 'Path', 'Route', 'p95 latency', 'Requests']) {
    const cell = document.createElement('th');
    cell.textContent = column;
    headRow.appendChild(cell);
  }
  head.appendChild(headRow);
  table.appendChild(head);

  const body = document.createElement('tbody');
  for (const path of viewModel.slowestPaths) {
    const row = document.createElement('tr');

    const methodCell = document.createElement('td');
    const methodBadge = document.createElement('span');
    methodBadge.className = 'method-badge';
    methodBadge.textContent = path.method;
    methodCell.appendChild(methodBadge);
    row.appendChild(methodCell);

    const pathCell = document.createElement('td');
    pathCell.className = 'mono';
    pathCell.textContent = path.path;
    row.appendChild(pathCell);

    const routeCell = document.createElement('td');
    routeCell.textContent = path.routeId;
    row.appendChild(routeCell);

    const p95Cell = document.createElement('td');
    p95Cell.innerHTML = formatDuration(path.p95Ms);
    row.appendChild(p95Cell);

    const countCell = document.createElement('td');
    countCell.textContent = String(path.count);
    row.appendChild(countCell);

    body.appendChild(row);
  }
  table.appendChild(body);
  card.appendChild(table);
  return card;
}

function renderOverview() {
  const container = document.getElementById('overview');
  if (!container) return;
  const payload = getState('overviewData');
  if (!payload) {
    container.replaceChildren(buildSkeleton('table', 5));
    return;
  }
  const viewModel = buildOverviewViewModel(payload);
  const children = [buildOverviewToolbar(viewModel)];

  if (viewModel.empty) {
    children.push(buildEmptyState(
      'No traffic in this window',
      'Widen the time window or send a request through a route.'
    ));
    container.replaceChildren(...children);
    return;
  }

  const metrics = document.createElement('div');
  metrics.className = 'metric-row';
  metrics.append(
    buildMetricCard('Requests', String(viewModel.totals.requests)),
    buildMetricCard('Error rate', formatOverviewRate(viewModel.totals.errorRate),
      viewModel.totals.errorRate > 0 ? 'danger' : ''),
    buildMetricCard('p50 latency', viewModel.totals.p50Ms === null ? '—' : viewModel.totals.p50Ms + ' ms'),
    buildMetricCard('p95 latency', viewModel.totals.p95Ms === null ? '—' : viewModel.totals.p95Ms + ' ms')
  );
  children.push(metrics, buildRouteHealthTable(viewModel), buildSlowestPathsTable(viewModel));
  container.replaceChildren(...children);
}
