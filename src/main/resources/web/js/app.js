function renderBanner() {
  const el = document.getElementById('status-banner');
  const streamMessage = getState('streamMessage');
  const statusMessage = getState('statusMessage');
  const messages = [];
  if (streamMessage) {
    messages.push(streamMessage);
  }
  if (statusMessage) {
    messages.push(statusMessage);
  }
  if (!messages.length) {
    el.replaceChildren();
    return;
  }
  const fragments = messages.map(message => {
    const banner = document.createElement('div');
    banner.className = `banner ${message.type}`;
    banner.textContent = message.text;
    return banner;
  });
  el.replaceChildren(...fragments);
}

function renderApp(options = {}) {
  const activeSession = getState('activeSession');
  const settings = {
    banner: true,
    list: true,
    subtitle: true,
    detail: true,
    detailEmptyMessage: 'No requests for the selected route.',
    ...options
  };
  if (settings.banner) {
    renderBanner();
  }
  if (settings.list) {
    renderRouteList();
    renderRouteHeader();
    renderRequestTable();
  }
  if (settings.subtitle) {
    updateTopbarSubtitle();
  }
  if (!settings.detail) {
    return Promise.resolve();
  }
  if (activeSession) {
    return loadSessionDetails(activeSession);
  }
  renderDetailEmpty(settings.detailEmptyMessage);
  return Promise.resolve();
}

function renderListSection() {
  return renderApp({ detail: false });
}

function renderSelectedSessionDetail() {
  return renderApp({
    banner: false,
    list: false,
    subtitle: false,
    detail: true
  });
}

function renderRouteSelectionState() {
  return renderApp();
}

function parseBooleanAttr(value) {
  return String(value) === 'true';
}

function bindUiEvents() {
  const addRouteBtn = document.getElementById('add-route-btn');
  if (addRouteBtn) addRouteBtn.addEventListener('click', () => openAddRouteModal());

  const routeSearch = document.getElementById('route-search');
  if (routeSearch) routeSearch.addEventListener('input', () => renderRouteList());

  const refreshRoutesBtn = document.getElementById('refresh-routes-btn');
  if (refreshRoutesBtn) refreshRoutesBtn.addEventListener('click', () => refreshSessions(true));

  const routeModal = document.getElementById('route-modal');
  if (routeModal) {
    routeModal.addEventListener('click', event => {
      if (event.target === routeModal) {
        closeRouteModal();
      }
    });
  }

  const routeModalCloseBtn = document.getElementById('route-modal-close-btn');
  if (routeModalCloseBtn) routeModalCloseBtn.addEventListener('click', () => closeRouteModal());

  const routeModalCancelBtn = document.getElementById('route-modal-cancel-btn');
  if (routeModalCancelBtn) routeModalCancelBtn.addEventListener('click', () => closeRouteModal());

  const routeModalSaveBtn = document.getElementById('route-modal-save-btn');
  if (routeModalSaveBtn) routeModalSaveBtn.addEventListener('click', () => submitRouteForm());

  const listenerTransport = document.getElementById('rm-listener-transport');
  if (listenerTransport) listenerTransport.addEventListener('change', event => toggleListenerTls(event.target.value));

  const targetTransport = document.getElementById('rm-target-transport');
  if (targetTransport) targetTransport.addEventListener('change', event => toggleTargetTls(event.target.value));

  document.addEventListener('click', async event => {
    const actionEl = event.target.closest('[data-action]');
    if (!actionEl) return;
    const { action } = actionEl.dataset;
    switch (action) {
      case 'select-route':
        if (!event.target.closest('.route-actions')) {
          selectRoute(actionEl.dataset.routeId);
        }
        break;
      case 'edit-route':
        event.stopPropagation();
        openEditRouteModal(actionEl.dataset.routeId);
        break;
      case 'delete-route':
        event.stopPropagation();
        await confirmDeleteRoute(actionEl.dataset.routeId);
        break;
      case 'export-har':
        await exportHar();
        break;
      case 'toggle-config-panel':
        toggleConfigPanel();
        break;
      case 'select-session':
        await selectSession(actionEl.dataset.sessionId, Number(actionEl.dataset.exchangeIndex || 0));
        break;
      case 'change-request-page':
        await changeRequestPage(Number(actionEl.dataset.delta || 0));
        break;
      case 'copy-current-body':
        await copyCurrentBody(parseBooleanAttr(actionEl.dataset.isRequest));
        break;
      case 'release-pending':
        await releasePending(actionEl.dataset.pendingId);
        break;
      case 'show-edit-pending':
        showEdit(
          actionEl.dataset.pendingId,
          JSON.parse(actionEl.dataset.decodedPayload || 'null'),
          actionEl.dataset.base64Value || ''
        );
        break;
      case 'toggle-diff-mode':
        toggleDiffMode();
        break;
      case 'select-exchange':
        await selectExchange(Number(actionEl.dataset.exchangeIndex || 0));
        break;
      case 'replay-payload':
        await replayPayload(
          actionEl.dataset.routeId,
          actionEl.dataset.sessionId || '',
          Number(actionEl.dataset.exchangeIndex || 0),
          actionEl.dataset.destination || 'listener'
        );
        break;
      case 'copy-curl-from-session':
        await copyCurlFromSession();
        break;
      case 'copy-current-headers':
        copyCurrentHeaders(parseBooleanAttr(actionEl.dataset.isRequest));
        break;
      case 'submit-structured-http':
        await submitStructuredHttp(actionEl.dataset.pendingId);
        break;
      case 'submit-edited':
        await submitEdited(actionEl.dataset.pendingId);
        break;
      default:
        break;
    }
  });

  document.addEventListener('input', event => {
    if (event.target.id === 'request-search') {
      debounceRequestSearch();
    }
  });

  document.addEventListener('change', event => {
    if (event.target.id === 'request-method-filter' || event.target.id === 'request-status-code-filter' || event.target.id === 'request-page-size') {
      resetRequestPageAndRender();
    }
  });

  document.addEventListener('toggle', event => {
    const detailsEl = event.target;
    if (!(detailsEl instanceof HTMLDetailsElement)) return;
    const action = detailsEl.dataset.action;
    if (action === 'toggle-payload-headers') {
      setPayloadHeadersExpanded(detailsEl.dataset.title || '', detailsEl.open);
    } else if (action === 'toggle-events-expanded') {
      setEventsExpanded(detailsEl.open);
    }
  }, true);
}

function setStatus(type, text) {
  setState('statusMessage', { type, text });
  const activeSession = getState('activeSession');
  renderApp({
    list: false,
    subtitle: false,
    detail: Boolean(activeSession)
  });
}

async function connectEventStream() {
  const currentEventSource = getState('eventSource');
  if (currentEventSource) {
    currentEventSource.close();
  }
  try {
    await fetchJson('/api/config');
  } catch (error) {
    setState('streamMessage', { type: 'error', text: error.message });
    renderApp({ list: false, subtitle: false, detail: false });
    return;
  }
  const nextEventSource = new EventSource('/api/events');
  setState('eventSource', nextEventSource);
  nextEventSource.addEventListener('open', () => {
    setState('streamMessage', null);
    renderApp({ list: false, subtitle: false, detail: false });
  });
  nextEventSource.addEventListener('error', () => {
    setState('streamMessage', { type: 'info', text: 'Live updates disconnected. Trying to reconnect...' });
    renderApp({ list: false, subtitle: false, detail: false });
    setTimeout(() => connectEventStream(), 1500);
  });
  nextEventSource.addEventListener('session-created', handleSessionChange);
  nextEventSource.addEventListener('session-updated', handleSessionChange);
  nextEventSource.addEventListener('session-closed', handleSessionChange);
  nextEventSource.addEventListener('pending-released', handleSessionChange);
}

async function handleSessionChange(event) {
  let payload;
  try {
    payload = JSON.parse(event.data || '{}');
  } catch (error) {
    return;
  }
  const activeSession = getState('activeSession');
  const activeRoute = getState('activeRoute');
  const affectsActiveSession = Boolean(activeSession && payload.sessionId === activeSession);
  const affectsActiveRoute = Boolean(activeRoute && payload.routeId === activeRoute);
  if (payload.type === 'session-created' || payload.type === 'session-closed') {
    scheduleListRefresh();
    if (affectsActiveSession) {
      scheduleDetailRefresh();
    }
    return;
  }
  if (payload.type === 'pending-released') {
    if (affectsActiveSession) {
      scheduleDetailRefresh();
    }
    return;
  }
  if (payload.reason === 'PAYLOAD') {
    if (affectsActiveSession) {
      scheduleDetailRefresh();
      scheduleRequestTableRefresh();
      return;
    }
    if (affectsActiveRoute) {
      scheduleRequestTableRefresh();
    }
    return;
  }
  if (affectsActiveSession) {
    scheduleDetailRefresh();
    scheduleListRefresh();
    return;
  }
  if (affectsActiveRoute) {
    scheduleListRefresh();
  }
}

function scheduleDetailRefresh() {
  setState('pendingDetailRefresh', true);
  if (getState('scheduledDetailRefreshTimer')) {
    return;
  }
  setState('scheduledDetailRefreshTimer', setTimeout(async () => {
    setState('scheduledDetailRefreshTimer', null);
    if (getState('detailRefreshInFlight')) {
      scheduleDetailRefresh();
      return;
    }
    const activeSession = getState('activeSession');
    if (!getState('pendingDetailRefresh') || !activeSession) {
      setState('pendingDetailRefresh', false);
      return;
    }
    patchState({
      pendingDetailRefresh: false,
      detailRefreshInFlight: true
    });
    try {
      await loadSessionDetails(activeSession);
    } catch (error) {
      setState('streamMessage', { type: 'info', text: 'Live update received, but refresh failed. Use Refresh to resync.' });
      renderApp({ list: false, subtitle: false, detail: false });
    } finally {
      setState('detailRefreshInFlight', false);
    }
    if (!getState('streamMessage')) {
      renderApp({ list: false, subtitle: false, detail: false });
    }
    if (getState('pendingDetailRefresh')) {
      scheduleDetailRefresh();
    }
  }, 150));
}

function scheduleListRefresh() {
  setState('pendingListRefresh', true);
  if (getState('scheduledListRefreshTimer')) {
    return;
  }
  setState('scheduledListRefreshTimer', setTimeout(async () => {
    setState('scheduledListRefreshTimer', null);
    if (getState('listRefreshInFlight')) {
      scheduleListRefresh();
      return;
    }
    if (!getState('pendingListRefresh')) {
      return;
    }
    patchState({
      pendingListRefresh: false,
      listRefreshInFlight: true
    });
    try {
      await refreshSessionsView(true, false);
    } catch (error) {
      setState('streamMessage', { type: 'info', text: 'Live update received, but refresh failed. Use Refresh to resync.' });
      renderApp({ list: false, subtitle: false, detail: false });
    } finally {
      setState('listRefreshInFlight', false);
    }
    if (!getState('streamMessage')) {
      renderApp({ list: false, subtitle: false, detail: false });
    }
    if (getState('pendingListRefresh')) {
      scheduleListRefresh();
    }
  }, 800));
}

function scheduleRequestTableRefresh() {
  setState('pendingRequestTableRefresh', true);
  if (getState('scheduledRequestTableRefreshTimer')) {
    return;
  }
  setState('scheduledRequestTableRefreshTimer', setTimeout(async () => {
    setState('scheduledRequestTableRefreshTimer', null);
    if (getState('requestTableRefreshInFlight')) {
      scheduleRequestTableRefresh();
      return;
    }
    if (!getState('pendingRequestTableRefresh')) {
      return;
    }
    patchState({
      pendingRequestTableRefresh: false,
      requestTableRefreshInFlight: true
    });
    try {
      const activeRoute = getState('activeRoute');
      if (activeRoute) {
        await loadRequestsForRoute(activeRoute);
        renderRequestTable();
      }
    } catch (error) {
      setState('streamMessage', { type: 'info', text: 'Live update received, but refresh failed. Use Refresh to resync.' });
      renderApp({ list: false, subtitle: false, detail: false });
    } finally {
      setState('requestTableRefreshInFlight', false);
    }
    if (!getState('streamMessage')) {
      renderApp({ list: false, subtitle: true, detail: false });
    }
    if (getState('pendingRequestTableRefresh')) {
      scheduleRequestTableRefresh();
    }
  }, 800));
}

function updateTopbarSubtitle() {
  const el = document.getElementById('topbar-subtitle');
  if (!el) return;
  const activeRoute = getState('activeRoute');
  if (!activeRoute) {
    el.textContent = 'Select route, inspect recorded requests, open one to view request and response.';
    return;
  }
  const sessions = sessionsForActiveRoute();
  const facets = getState('requestFacets') || {};
  const pending = sessions.reduce((sum, s) => sum + Number(s.pendingCount || 0), 0);
  if (pending > 0) {
    el.textContent = activeRoute + ' — ' + pending + ' pending';
  } else {
    const count = Number(facets.totalRequests || 0);
    el.textContent = activeRoute + ' — ' + count + ' request' + (count !== 1 ? 's' : '');
  }
}

async function initApp() {
  try {
    await refreshSessions(false);
    await loadConfig();
  } catch (error) {
    setStatus('error', error.message);
  }
  await connectEventStream();
}

bindUiEvents();
initApp();
