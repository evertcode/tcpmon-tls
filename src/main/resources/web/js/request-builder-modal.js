let requestBuilderModalOpenerEl = null;

function populateRequestBuilderRoutes() {
  const select = document.getElementById('rb-route-select');
  const config = getState('proxyConfig');
  const routes = config ? (config.routes || []) : [];
  select.replaceChildren(...routes.map(route => {
    const option = document.createElement('option');
    option.value = route.id;
    option.textContent = route.id;
    return option;
  }));
  if (!select.dataset.summaryBound) {
    select.addEventListener('change', updateRequestBuilderSummary);
    select.dataset.summaryBound = 'true';
  }
}

function updateRequestBuilderSummary() {
  const select = document.getElementById('rb-route-select');
  const config = getState('proxyConfig');
  const route = config ? (config.routes || []).find(r => r.id === select.value) : null;
  if (!route) {
    buildComposerSummaryPills('rb-summary', '', '', '');
    return;
  }
  buildComposerSummaryPills(
    'rb-summary',
    route.id,
    `${route.listener.host}:${route.listener.port}`,
    `${route.target.host}:${route.target.port}`
  );
}

function openRequestBuilderModal(prefill) {
  populateRequestBuilderRoutes();
  const select = document.getElementById('rb-route-select');
  if (!select.options.length) {
    setStatus('error', 'Add a route before composing a request');
    return;
  }

  requestBuilderModalOpenerEl = document.activeElement;

  setMethodSelectValue('rb-method', prefill?.method);
  document.getElementById('rb-path').value = prefill?.path || '';
  document.getElementById('rb-query').value = prefill?.query || '';
  document.getElementById('rb-version').value = prefill?.version || 'HTTP/1.1';
  populateHeaderPairs('rb-headers-list', prefill?.headers);
  document.getElementById('rb-body').value = prefill?.bodyText || '';
  updateRequestBuilderSummary();
  clearFieldInvalid(document.getElementById('rb-method'));
  clearFieldInvalid(document.getElementById('rb-path'));

  const modal = document.getElementById('request-builder-modal');
  modal.style.display = 'flex';
  modal.removeAttribute('aria-hidden');
  setTimeout(() => select.focus(), 50);
}

async function submitRequestBuilder(destination) {
  const modal = document.getElementById('request-builder-modal');
  const routeId = document.getElementById('rb-route-select').value;
  const methodField = document.getElementById('rb-method');
  const pathField = document.getElementById('rb-path');
  clearFieldInvalid(methodField);
  clearFieldInvalid(pathField);
  let hasError = false;
  if (!methodField.value.trim()) {
    setFieldInvalid(methodField, 'Method is required.');
    hasError = true;
  }
  if (!pathField.value.trim()) {
    setFieldInvalid(pathField, 'Path is required.');
    hasError = true;
  }
  if (hasError) {
    setStatus('error', 'Review the highlighted fields before sending.');
    return;
  }
  const buttons = modal.querySelectorAll('[data-action="submit-request-builder"]');
  buttons.forEach(btn => { btn.disabled = true; });
  try {
    const result = await fetchJson('/api/replay', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        routeId,
        destination,
        http: {
          method: methodField.value,
          path: pathField.value,
          query: document.getElementById('rb-query').value,
          version: document.getElementById('rb-version').value,
          headersText: serializeHeaderPairs('rb-headers-list'),
          bodyText: document.getElementById('rb-body').value
        }
      })
    });
    setStatus('success', `Replay ${destination} completed: sent ${result.bytesSent} bytes, received ${result.bytesReceived ?? 0} bytes from ${result.target}`);
    closeRequestBuilderModal();
  } catch (error) {
    setStatus('error', error.message);
  } finally {
    buttons.forEach(btn => { btn.disabled = false; });
  }
}

function closeRequestBuilderModal() {
  const modal = document.getElementById('request-builder-modal');
  modal.style.display = 'none';
  modal.setAttribute('aria-hidden', 'true');
  if (requestBuilderModalOpenerEl && typeof requestBuilderModalOpenerEl.focus === 'function') {
    requestBuilderModalOpenerEl.focus();
  }
  requestBuilderModalOpenerEl = null;
}
