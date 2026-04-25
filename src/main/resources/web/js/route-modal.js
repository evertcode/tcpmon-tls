let routeModalMode = 'add';
let routeModalEditId = null;
let routeModalOpenerEl = null;
let confirmModalOpenerEl = null;
let pendingDeleteRouteId = null;

function toggleListenerTls(val) {
  document.getElementById('listener-tls-fields').style.display = val === 'TLS' ? '' : 'none';
}

function toggleTargetTls(val) {
  document.getElementById('target-tls-fields').style.display = val === 'TLS' ? '' : 'none';
}

function routeModalFieldValue(id, fallback = '') {
  const field = document.getElementById(id);
  if (!field) return fallback;
  return String(field.value || '').trim() || fallback;
}

function buildRouteEndpointSummary(hostId, portId, fallbackHost, fallbackPort) {
  const host = routeModalFieldValue(hostId, fallbackHost);
  const port = routeModalFieldValue(portId, fallbackPort);
  return host && port ? `${host}:${port}` : host || port || '';
}

function updateRouteModalSummary() {
  const summary = document.getElementById('route-modal-summary');
  if (!summary) return;

  const routeId = routeModalFieldValue('rm-id');
  const listener = buildRouteEndpointSummary('rm-listener-host', 'rm-listener-port', '0.0.0.0', '...');
  const target = buildRouteEndpointSummary('rm-target-host', 'rm-target-port', 'target host', '...');
  const listenerTransport = routeModalFieldValue('rm-listener-transport', 'PLAIN');
  const targetTransport = routeModalFieldValue('rm-target-transport', 'PLAIN');

  const pills = [];
  if (routeId) pills.push(routeId);
  pills.push(`Listener ${listener}`);
  pills.push(`Target ${target}`);
  pills.push(`${listenerTransport} → ${targetTransport}`);

  summary.replaceChildren();
  for (const label of pills) {
    const pill = document.createElement('span');
    pill.className = 'pill route';
    pill.textContent = label;
    summary.appendChild(pill);
  }
}

function openAddRouteModal() {
  routeModalMode = 'add';
  routeModalEditId = null;
  document.getElementById('route-modal-title').textContent = 'Add Route';
  document.getElementById('rm-id').value = '';
  document.getElementById('rm-id').disabled = false;
  document.getElementById('rm-listener-host').value = '0.0.0.0';
  document.getElementById('rm-listener-port').value = '';
  document.getElementById('rm-listener-transport').value = 'PLAIN';
  document.getElementById('listener-tls-fields').style.display = 'none';
  document.getElementById('rm-listener-tls-cert').value = '';
  document.getElementById('rm-listener-tls-key').value = '';
  document.getElementById('rm-listener-tls-keystore').value = '';
  document.getElementById('rm-listener-tls-keystore-pwd').value = '';
  document.getElementById('rm-listener-tls-keystore-type').value = 'PKCS12';
  document.getElementById('rm-listener-tls-truststore').value = '';
  document.getElementById('rm-listener-tls-truststore-pwd').value = '';
  document.getElementById('rm-listener-tls-truststore-type').value = 'PKCS12';
  document.getElementById('rm-listener-client-auth').value = 'NONE';
  document.getElementById('rm-target-host').value = '';
  document.getElementById('rm-target-port').value = '';
  document.getElementById('rm-target-transport').value = 'PLAIN';
  document.getElementById('target-tls-fields').style.display = 'none';
  document.getElementById('rm-target-tls-cert').value = '';
  document.getElementById('rm-target-tls-key').value = '';
  document.getElementById('rm-target-tls-keystore').value = '';
  document.getElementById('rm-target-tls-keystore-pwd').value = '';
  document.getElementById('rm-target-tls-keystore-type').value = 'PKCS12';
  document.getElementById('rm-target-tls-truststore').value = '';
  document.getElementById('rm-target-tls-truststore-pwd').value = '';
  document.getElementById('rm-target-tls-truststore-type').value = 'PKCS12';
  document.getElementById('rm-target-sni').value = '';
  document.getElementById('rm-target-insecure').checked = false;
  document.getElementById('rm-target-verify').checked = true;
  document.getElementById('rm-target-rewrite').checked = false;
  clearRouteModalErrors();
  updateRouteModalSummary();
  routeModalOpenerEl = document.activeElement;
  showRouteModal();
  setTimeout(() => document.getElementById('rm-id').focus(), 50);
}

function openEditRouteModal(routeId) {
  const proxyConfig = getState('proxyConfig');
  const route = proxyConfig && (proxyConfig.routes || []).find(r => r.id === routeId);
  if (!route) {
    alert('Route config not loaded yet. Try clicking Config first.');
    return;
  }
  routeModalMode = 'edit';
  routeModalEditId = routeId;
  document.getElementById('route-modal-title').textContent = 'Edit Route';
  document.getElementById('rm-id').value = route.id;
  document.getElementById('rm-id').disabled = true;
  document.getElementById('rm-listener-host').value = route.listener.host || '0.0.0.0';
  document.getElementById('rm-listener-port').value = route.listener.port || '';
  const listenerTransport = route.listener.transport || 'PLAIN';
  document.getElementById('rm-listener-transport').value = listenerTransport;
  toggleListenerTls(listenerTransport);
  document.getElementById('rm-listener-tls-cert').value = route.listener.tlsCert || '';
  document.getElementById('rm-listener-tls-key').value = route.listener.tlsKey || '';
  document.getElementById('rm-listener-tls-keystore').value = route.listener.tlsKeystore || '';
  document.getElementById('rm-listener-tls-keystore-pwd').value = '';
  document.getElementById('rm-listener-tls-keystore-pwd').placeholder = route.listener.tlsKeystorePasswordConfigured ? 'Stored password preserved unless replaced' : '';
  document.getElementById('rm-listener-tls-keystore-type').value = route.listener.tlsKeystoreType || 'PKCS12';
  document.getElementById('rm-listener-tls-truststore').value = route.listener.tlsTruststore || '';
  document.getElementById('rm-listener-tls-truststore-pwd').value = '';
  document.getElementById('rm-listener-tls-truststore-pwd').placeholder = route.listener.tlsTruststorePasswordConfigured ? 'Stored password preserved unless replaced' : '';
  document.getElementById('rm-listener-tls-truststore-type').value = route.listener.tlsTruststoreType || 'PKCS12';
  document.getElementById('rm-listener-client-auth').value = route.listener.clientAuth || 'NONE';
  document.getElementById('rm-target-host').value = route.target.host || '';
  document.getElementById('rm-target-port').value = route.target.port || '';
  const targetTransport = route.target.transport || 'PLAIN';
  document.getElementById('rm-target-transport').value = targetTransport;
  toggleTargetTls(targetTransport);
  document.getElementById('rm-target-tls-cert').value = route.target.tlsCert || '';
  document.getElementById('rm-target-tls-key').value = route.target.tlsKey || '';
  document.getElementById('rm-target-tls-keystore').value = route.target.tlsKeystore || '';
  document.getElementById('rm-target-tls-keystore-pwd').value = '';
  document.getElementById('rm-target-tls-keystore-pwd').placeholder = route.target.tlsKeystorePasswordConfigured ? 'Stored password preserved unless replaced' : '';
  document.getElementById('rm-target-tls-keystore-type').value = route.target.tlsKeystoreType || 'PKCS12';
  document.getElementById('rm-target-tls-truststore').value = route.target.tlsTruststore || '';
  document.getElementById('rm-target-tls-truststore-pwd').value = '';
  document.getElementById('rm-target-tls-truststore-pwd').placeholder = route.target.tlsTruststorePasswordConfigured ? 'Stored password preserved unless replaced' : '';
  document.getElementById('rm-target-tls-truststore-type').value = route.target.tlsTruststoreType || 'PKCS12';
  document.getElementById('rm-target-sni').value = route.target.sniHost || '';
  document.getElementById('rm-target-insecure').checked = !!route.target.insecureTrustAll;
  document.getElementById('rm-target-verify').checked = route.target.verifyHostname !== false;
  document.getElementById('rm-target-rewrite').checked = !!route.target.rewriteHostHeader;
  clearRouteModalErrors();
  updateRouteModalSummary();
  routeModalOpenerEl = document.activeElement;
  showRouteModal();
  setTimeout(() => document.getElementById('rm-id').focus(), 50);
}

function showRouteModal() {
  const modal = document.getElementById('route-modal');
  modal.style.display = 'flex';
  modal.removeAttribute('aria-hidden');
}

function closeRouteModal() {
  const modal = document.getElementById('route-modal');
  modal.style.display = 'none';
  modal.setAttribute('aria-hidden', 'true');
  if (routeModalOpenerEl && typeof routeModalOpenerEl.focus === 'function') {
    routeModalOpenerEl.focus();
    routeModalOpenerEl = null;
  }
}

function clearRouteModalErrors() {
  const error = document.getElementById('route-modal-error');
  error.textContent = '';
  error.style.display = 'none';
  for (const id of [
    'rm-id',
    'rm-listener-host',
    'rm-listener-port',
    'rm-target-host',
    'rm-target-port',
    'rm-listener-tls-cert',
    'rm-listener-tls-key',
    'rm-listener-tls-keystore',
    'rm-target-tls-cert',
    'rm-target-tls-key',
    'rm-target-tls-keystore'
  ]) {
    clearFieldInvalid(document.getElementById(id));
  }
}

function tlsFieldVal(id) {
  const v = document.getElementById(id).value.trim();
  return v || undefined;
}

function secretFieldVal(id, preserveExisting) {
  const field = document.getElementById(id);
  const value = field.value.trim();
  if (value) {
    return value;
  }
  if (routeModalMode === 'edit' && preserveExisting) {
    return '__PRESERVE_SECRET__';
  }
  return undefined;
}

function buildRoutePayload() {
  const proxyConfig = getState('proxyConfig');
  const listenerTransport = document.getElementById('rm-listener-transport').value;
  const targetTransport = document.getElementById('rm-target-transport').value;
  const payload = {
    id: document.getElementById('rm-id').value.trim(),
    listener: {
      host: document.getElementById('rm-listener-host').value.trim() || '0.0.0.0',
      port: parseInt(document.getElementById('rm-listener-port').value, 10),
      transport: listenerTransport
    },
    target: {
      host: document.getElementById('rm-target-host').value.trim(),
      port: parseInt(document.getElementById('rm-target-port').value, 10),
      transport: targetTransport,
      sniHost: document.getElementById('rm-target-sni').value.trim() || null,
      insecureTrustAll: document.getElementById('rm-target-insecure').checked,
      verifyHostname: document.getElementById('rm-target-verify').checked,
      rewriteHostHeader: document.getElementById('rm-target-rewrite').checked
    }
  };
  if (listenerTransport === 'TLS') {
    const cert = tlsFieldVal('rm-listener-tls-cert');
    const key = tlsFieldVal('rm-listener-tls-key');
    const ks = tlsFieldVal('rm-listener-tls-keystore');
    const ksPwd = secretFieldVal('rm-listener-tls-keystore-pwd', !!(proxyConfig && (proxyConfig.routes || []).find(r => r.id === routeModalEditId)?.listener?.tlsKeystorePasswordConfigured));
    const ksType = tlsFieldVal('rm-listener-tls-keystore-type');
    const ts = tlsFieldVal('rm-listener-tls-truststore');
    const tsPwd = secretFieldVal('rm-listener-tls-truststore-pwd', !!(proxyConfig && (proxyConfig.routes || []).find(r => r.id === routeModalEditId)?.listener?.tlsTruststorePasswordConfigured));
    const tsType = tlsFieldVal('rm-listener-tls-truststore-type');
    if (cert) payload.listener.tlsCert = cert;
    if (key) payload.listener.tlsKey = key;
    if (ks) payload.listener.tlsKeystore = ks;
    if (ksPwd) payload.listener.tlsKeystorePassword = ksPwd;
    if (ksType) payload.listener.tlsKeystoreType = ksType;
    if (ts) payload.listener.tlsTruststore = ts;
    if (tsPwd) payload.listener.tlsTruststorePassword = tsPwd;
    if (tsType) payload.listener.tlsTruststoreType = tsType;
    payload.listener.clientAuth = document.getElementById('rm-listener-client-auth').value;
  }
  if (targetTransport === 'TLS') {
    const cert = tlsFieldVal('rm-target-tls-cert');
    const key = tlsFieldVal('rm-target-tls-key');
    const ks = tlsFieldVal('rm-target-tls-keystore');
    const ksPwd = secretFieldVal('rm-target-tls-keystore-pwd', !!(proxyConfig && (proxyConfig.routes || []).find(r => r.id === routeModalEditId)?.target?.tlsKeystorePasswordConfigured));
    const ksType = tlsFieldVal('rm-target-tls-keystore-type');
    const ts = tlsFieldVal('rm-target-tls-truststore');
    const tsPwd = secretFieldVal('rm-target-tls-truststore-pwd', !!(proxyConfig && (proxyConfig.routes || []).find(r => r.id === routeModalEditId)?.target?.tlsTruststorePasswordConfigured));
    const tsType = tlsFieldVal('rm-target-tls-truststore-type');
    if (cert) payload.target.tlsCert = cert;
    if (key) payload.target.tlsKey = key;
    if (ks) payload.target.tlsKeystore = ks;
    if (ksPwd) payload.target.tlsKeystorePassword = ksPwd;
    if (ksType) payload.target.tlsKeystoreType = ksType;
    if (ts) payload.target.tlsTruststore = ts;
    if (tsPwd) payload.target.tlsTruststorePassword = tsPwd;
    if (tsType) payload.target.tlsTruststoreType = tsType;
  }
  return payload;
}

function showRouteModalError(msg) {
  const el = document.getElementById('route-modal-error');
  el.textContent = msg;
  el.style.display = 'block';
}

function validateRouteForm(payload) {
  clearRouteModalErrors();
  const errors = [];
  function requireField(id, message) {
    const field = document.getElementById(id);
    if (!String(field.value || '').trim()) {
      setFieldInvalid(field, message);
      errors.push(field);
    }
  }
  function requirePort(id, message) {
    const field = document.getElementById(id);
    const value = Number(field.value);
    if (!Number.isInteger(value) || value < 1 || value > 65535) {
      setFieldInvalid(field, message);
      errors.push(field);
    }
  }

  requireField('rm-id', 'Route ID is required.');
  requireField('rm-listener-host', 'Listener host is required.');
  requirePort('rm-listener-port', 'Use a port from 1 to 65535.');
  requireField('rm-target-host', 'Target host is required.');
  requirePort('rm-target-port', 'Use a port from 1 to 65535.');

  if (payload.listener.transport === 'TLS') {
    const hasCertPair = payload.listener.tlsCert && payload.listener.tlsKey;
    const hasKeystore = payload.listener.tlsKeystore;
    if (!hasCertPair && !hasKeystore) {
      setFieldInvalid(document.getElementById('rm-listener-tls-cert'), 'Provide certificate plus key, or a keystore.');
      setFieldInvalid(document.getElementById('rm-listener-tls-keystore'), 'Provide a keystore, or certificate plus key.');
      errors.push(document.getElementById('rm-listener-tls-cert'));
    }
    if ((payload.listener.tlsCert && !payload.listener.tlsKey) || (!payload.listener.tlsCert && payload.listener.tlsKey)) {
      setFieldInvalid(document.getElementById('rm-listener-tls-key'), 'Certificate and private key must be provided together.');
      errors.push(document.getElementById('rm-listener-tls-key'));
    }
  }

  if (payload.target.transport === 'TLS') {
    if ((payload.target.tlsCert && !payload.target.tlsKey) || (!payload.target.tlsCert && payload.target.tlsKey)) {
      setFieldInvalid(document.getElementById('rm-target-tls-key'), 'Certificate and private key must be provided together.');
      errors.push(document.getElementById('rm-target-tls-key'));
    }
  }

  if (errors.length) {
    showRouteModalError('Review the highlighted fields before saving.');
    errors[0].focus();
    return false;
  }
  return true;
}

async function submitRouteForm() {
  clearRouteModalErrors();
  const payload = buildRoutePayload();
  if (!validateRouteForm(payload)) return;
  const saveBtn = document.getElementById('route-modal-save-btn');
  saveBtn.disabled = true;
  saveBtn.textContent = 'Saving...';
  try {
    if (routeModalMode === 'add') {
      await fetchJson('/api/routes', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
    } else {
      await fetchJson('/api/routes/' + encodeURIComponent(routeModalEditId), {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
    }
    closeRouteModal();
    await loadConfig();
    const activeSession = getState('activeSession');
    await renderApp({
      detail: Boolean(activeSession)
    });
    setStatus('success', routeModalMode === 'add' ? 'Route created.' : 'Route updated.');
  } catch (err) {
    showRouteModalError(err.message || 'Error saving route.');
  } finally {
    saveBtn.disabled = false;
    saveBtn.textContent = 'Save';
  }
}

async function confirmDeleteRoute(routeId) {
  pendingDeleteRouteId = routeId;
  confirmModalOpenerEl = document.activeElement;
  document.getElementById('confirm-modal-title').textContent = 'Delete route';
  document.getElementById('confirm-modal-message').textContent =
    `Delete route "${routeId}"? This stops the listener immediately. Captured sessions remain available.`;
  const confirmBtn = document.getElementById('confirm-modal-confirm-btn');
  confirmBtn.disabled = false;
  confirmBtn.textContent = 'Delete route';
  const modal = document.getElementById('confirm-modal');
  modal.style.display = 'flex';
  modal.removeAttribute('aria-hidden');
  setTimeout(() => confirmBtn.focus(), 50);
}

function closeConfirmModal() {
  const modal = document.getElementById('confirm-modal');
  modal.style.display = 'none';
  modal.setAttribute('aria-hidden', 'true');
  pendingDeleteRouteId = null;
  if (confirmModalOpenerEl && typeof confirmModalOpenerEl.focus === 'function') {
    confirmModalOpenerEl.focus();
    confirmModalOpenerEl = null;
  }
}

async function deleteConfirmedRoute() {
  const routeId = pendingDeleteRouteId;
  if (!routeId) return;
  const confirmBtn = document.getElementById('confirm-modal-confirm-btn');
  confirmBtn.disabled = true;
  confirmBtn.textContent = 'Deleting...';
  try {
    await fetchJson('/api/routes/' + encodeURIComponent(routeId), { method: 'DELETE' });
    closeConfirmModal();
    await loadConfig();
    const activeRoute = getState('activeRoute');
    if (activeRoute === routeId) {
      patchState({
        activeRoute: null,
        activeSession: null
      });
    }
    if (getState('activeRoute')) {
      await renderRouteSelectionState();
    } else {
      await renderApp({
        detail: true,
        detailEmptyMessage: 'Route deleted.'
      });
    }
    setStatus('success', 'Route "' + routeId + '" deleted.');
  } catch (err) {
    confirmBtn.disabled = false;
    confirmBtn.textContent = 'Delete route';
    setStatus('error', err.message || 'Failed to delete route.');
  }
}
