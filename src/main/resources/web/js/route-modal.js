let routeModalMode = 'add';
let routeModalEditId = null;
let routeModalOpenerEl = null;

function toggleListenerTls(val) {
  document.getElementById('listener-tls-fields').style.display = val === 'TLS' ? '' : 'none';
}

function toggleTargetTls(val) {
  document.getElementById('target-tls-fields').style.display = val === 'TLS' ? '' : 'none';
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
  document.getElementById('route-modal-error').style.display = 'none';
  routeModalOpenerEl = document.activeElement;
  document.getElementById('route-modal').style.display = 'flex';
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
  document.getElementById('route-modal-error').style.display = 'none';
  routeModalOpenerEl = document.activeElement;
  document.getElementById('route-modal').style.display = 'flex';
  setTimeout(() => document.getElementById('rm-id').focus(), 50);
}

function closeRouteModal() {
  document.getElementById('route-modal').style.display = 'none';
  if (routeModalOpenerEl && typeof routeModalOpenerEl.focus === 'function') {
    routeModalOpenerEl.focus();
    routeModalOpenerEl = null;
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

async function submitRouteForm() {
  document.getElementById('route-modal-error').style.display = 'none';
  const payload = buildRoutePayload();
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
  }
}

async function confirmDeleteRoute(routeId) {
  if (!confirm('Delete route "' + routeId + '"? This will stop the listener immediately.')) return;
  try {
    await fetchJson('/api/routes/' + encodeURIComponent(routeId), { method: 'DELETE' });
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
    setStatus('error', err.message || 'Failed to delete route.');
  }
}
