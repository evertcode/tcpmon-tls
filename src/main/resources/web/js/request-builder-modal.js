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
}

function openRequestBuilderModal() {
  populateRequestBuilderRoutes();
  const select = document.getElementById('rb-route-select');
  if (!select.options.length) {
    setStatus('error', 'Add a route before composing a request');
    return;
  }

  requestBuilderModalOpenerEl = document.activeElement;

  document.getElementById('rb-method').value = 'GET';
  document.getElementById('rb-path').value = '';
  document.getElementById('rb-query').value = '';
  document.getElementById('rb-version').value = 'HTTP/1.1';
  document.getElementById('rb-headers').value = '';
  document.getElementById('rb-body').value = '';

  const modal = document.getElementById('request-builder-modal');
  modal.style.display = 'flex';
  modal.removeAttribute('aria-hidden');
  setTimeout(() => select.focus(), 50);
}

async function submitRequestBuilder(destination) {
  const modal = document.getElementById('request-builder-modal');
  const routeId = document.getElementById('rb-route-select').value;
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
          method: document.getElementById('rb-method').value,
          path: document.getElementById('rb-path').value,
          query: document.getElementById('rb-query').value,
          version: document.getElementById('rb-version').value,
          headersText: document.getElementById('rb-headers').value,
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
