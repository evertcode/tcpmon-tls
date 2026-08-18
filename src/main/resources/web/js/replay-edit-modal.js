let replayEditModalOpenerEl = null;

async function openReplayEditModal() {
  const payload = resolvePayload(true);
  if (!payload) return;
  const decoded = payload.decoded || {};
  if (!decoded.isHttp) {
    setStatus('error', 'Only HTTP requests can be edited before resending');
    return;
  }
  const lastLoadedSession = getState('lastLoadedSession');
  const routeId = lastLoadedSession?.routeId;
  if (!routeId) return;

  replayEditModalOpenerEl = document.activeElement;

  let bodyText = decoded.bodyText || '';
  if (decoded.bodyTruncated) {
    bodyText = await resolveFullBody(payload, true);
  }

  const request = decoded.request || {};
  document.getElementById('replay-edit-method').value = request.method || '';
  document.getElementById('replay-edit-path').value = request.path || '';
  document.getElementById('replay-edit-query').value = request.query || '';
  document.getElementById('replay-edit-version').value = request.version || 'HTTP/1.1';
  document.getElementById('replay-edit-headers').value = decoded.headersText || '';
  document.getElementById('replay-edit-body').value = bodyText;

  const modal = document.getElementById('replay-edit-modal');
  modal.dataset.routeId = routeId;
  modal.style.display = 'flex';
  modal.removeAttribute('aria-hidden');
  setTimeout(() => document.getElementById('replay-edit-method').focus(), 50);
}

async function submitReplayEdit(destination) {
  const modal = document.getElementById('replay-edit-modal');
  const routeId = modal.dataset.routeId;
  const buttons = modal.querySelectorAll('[data-action="submit-replay-edit"]');
  buttons.forEach(btn => { btn.disabled = true; });
  try {
    const result = await fetchJson('/api/replay', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        routeId,
        destination,
        http: {
          method: document.getElementById('replay-edit-method').value,
          path: document.getElementById('replay-edit-path').value,
          query: document.getElementById('replay-edit-query').value,
          version: document.getElementById('replay-edit-version').value,
          headersText: document.getElementById('replay-edit-headers').value,
          bodyText: document.getElementById('replay-edit-body').value
        }
      })
    });
    setStatus('success', `Replay ${destination} completed: sent ${result.bytesSent} bytes, received ${result.bytesReceived ?? 0} bytes from ${result.target}`);
    closeReplayEditModal();
  } catch (error) {
    setStatus('error', error.message);
  } finally {
    buttons.forEach(btn => { btn.disabled = false; });
  }
}

function closeReplayEditModal() {
  const modal = document.getElementById('replay-edit-modal');
  modal.style.display = 'none';
  modal.setAttribute('aria-hidden', 'true');
  if (replayEditModalOpenerEl && typeof replayEditModalOpenerEl.focus === 'function') {
    replayEditModalOpenerEl.focus();
  }
  replayEditModalOpenerEl = null;
}
