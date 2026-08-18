let bodyModalOpenerEl = null;
let bodyModalIsRequest = true;

function renderBodyViewModalViewer(decoded, bodyText) {
  const merged = { ...(decoded || {}), bodyText: bodyText || '' };
  const formatted = formatBody(merged) || 'No body captured';
  const viewer = buildBodyViewer(formatted, detectBodyViewerMode(merged));
  document.getElementById('body-view-modal-viewer-container').replaceChildren(viewer);
}

async function openBodyViewModal(isRequest) {
  const payload = resolvePayload(isRequest);
  if (!payload) return;

  bodyModalIsRequest = isRequest;
  bodyModalOpenerEl = document.activeElement;

  const decoded = payload.decoded || {};
  const modal = document.getElementById('body-view-modal');

  document.getElementById('body-view-modal-title').textContent = isRequest ? 'Request body' : 'Response body';

  const copyHeadersBtn = document.getElementById('body-view-modal-copy-headers-btn');
  const copyBodyBtn = document.getElementById('body-view-modal-copy-body-btn');
  copyHeadersBtn.dataset.isRequest = String(isRequest);
  copyBodyBtn.dataset.isRequest = String(isRequest);
  copyHeadersBtn.style.display = Array.isArray(decoded.headers) && decoded.headers.length ? '' : 'none';

  renderBodyViewModalViewer(decoded, decoded.bodyText || '');

  modal.style.display = 'flex';
  modal.removeAttribute('aria-hidden');
  setTimeout(() => document.getElementById('body-view-modal-close-btn').focus(), 50);

  if (decoded.bodyTruncated) {
    const fullBodyText = await resolveFullBody(payload, isRequest);
    if (bodyModalIsRequest !== isRequest || modal.style.display === 'none') return;
    renderBodyViewModalViewer(decoded, fullBodyText);
  }
}

function closeBodyViewModal() {
  const modal = document.getElementById('body-view-modal');
  modal.style.display = 'none';
  modal.setAttribute('aria-hidden', 'true');
  if (bodyModalOpenerEl && typeof bodyModalOpenerEl.focus === 'function') {
    bodyModalOpenerEl.focus();
  }
  bodyModalOpenerEl = null;
}
