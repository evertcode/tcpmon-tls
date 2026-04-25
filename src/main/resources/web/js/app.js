function clearBannerTimer() {
  const timer = getState('statusMessageDismissTimer');
  if (timer) {
    clearTimeout(timer);
    setState('statusMessageDismissTimer', null);
  }
}

function renderBanner() {
  const el = document.getElementById('status-banner');
  const streamMessage = getState('streamMessage');
  const statusMessage = getState('statusMessage');
  const fragments = [];
  if (streamMessage) {
    const banner = document.createElement('div');
    banner.className = `banner ${streamMessage.type}`;
    banner.textContent = streamMessage.text;
    fragments.push(banner);
  }
  if (statusMessage) {
    const banner = document.createElement('div');
    banner.className = `banner ${statusMessage.type}`;
    const closeBtn = document.createElement('button');
    closeBtn.className = 'banner-close';
    closeBtn.textContent = '✕';
    closeBtn.setAttribute('aria-label', 'Dismiss notification');
    closeBtn.addEventListener('click', () => {
      clearBannerTimer();
      setState('statusMessage', null);
      renderBanner();
    });
    const text = document.createElement('span');
    text.textContent = statusMessage.text;
    banner.append(closeBtn, text);
    fragments.push(banner);
  }
  if (!fragments.length) {
    el.replaceChildren();
    return;
  }
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

const THEME_STORAGE_KEY = 'tcpmon-theme-preference';

function getStoredThemePreference() {
  try {
    const value = localStorage.getItem(THEME_STORAGE_KEY);
    return value === 'light' || value === 'dark' || value === 'system' ? value : 'system';
  } catch (error) {
    return 'system';
  }
}

function getSystemTheme() {
  return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function getEffectiveTheme(preference) {
  return preference === 'system' ? getSystemTheme() : preference;
}

function applyThemePreference(preference) {
  const safePreference = preference === 'light' || preference === 'dark' ? preference : 'system';
  document.documentElement.dataset.theme = getEffectiveTheme(safePreference);
  document.documentElement.dataset.themePreference = safePreference;
  setState('themePreference', safePreference);
  try {
    localStorage.setItem(THEME_STORAGE_KEY, safePreference);
  } catch (error) {
    // Ignore storage failures and keep using the in-memory value.
  }
}

function initializeTheme() {
  applyThemePreference(getStoredThemePreference());
  renderConfigButton();
  if (!window.matchMedia) return;
  const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
  const handleChange = () => {
    if (getState('themePreference') === 'system') {
      applyThemePreference('system');
      renderConfigButton();
    }
  };
  if (typeof mediaQuery.addEventListener === 'function') {
    mediaQuery.addEventListener('change', handleChange);
  } else if (typeof mediaQuery.addListener === 'function') {
    mediaQuery.addListener(handleChange);
  }
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

  const confirmModalCloseBtn = document.getElementById('confirm-modal-close-btn');
  if (confirmModalCloseBtn) confirmModalCloseBtn.addEventListener('click', () => closeConfirmModal());

  const confirmModalCancelBtn = document.getElementById('confirm-modal-cancel-btn');
  if (confirmModalCancelBtn) confirmModalCancelBtn.addEventListener('click', () => closeConfirmModal());

  const listenerTransport = document.getElementById('rm-listener-transport');
  if (listenerTransport) listenerTransport.addEventListener('change', event => toggleListenerTls(event.target.value));

  const targetTransport = document.getElementById('rm-target-transport');
  if (targetTransport) targetTransport.addEventListener('change', event => toggleTargetTls(event.target.value));

  for (const fieldId of [
    'rm-id',
    'rm-listener-host',
    'rm-listener-port',
    'rm-listener-transport',
    'rm-target-host',
    'rm-target-port',
    'rm-target-transport'
  ]) {
    const field = document.getElementById(fieldId);
    if (field && typeof updateRouteModalSummary === 'function') {
      field.addEventListener('input', updateRouteModalSummary);
      field.addEventListener('change', updateRouteModalSummary);
    }
  }

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
      case 'confirm-delete-route':
        event.stopPropagation();
        await deleteConfirmedRoute();
        break;
      case 'export-har':
        await exportHar();
        break;
      case 'toggle-config-panel':
        toggleConfigPanel();
        break;
      case 'set-theme-light':
        applyThemePreference('light');
        renderConfigButton();
        break;
      case 'set-theme-system':
        applyThemePreference('system');
        renderConfigButton();
        break;
      case 'set-theme-dark':
        applyThemePreference('dark');
        renderConfigButton();
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
      case 'download-exchange':
        await downloadExchange(actionEl.dataset.format || 'json');
        break;
      case 'clear-request-filters':
        await clearRequestFilters();
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

  document.addEventListener('keydown', event => {
    const modal = getOpenModal();
    if (event.key === 'Escape') {
      if (modal) {
        if (modal.id === 'route-modal') {
          closeRouteModal();
        } else if (modal.id === 'confirm-modal') {
          closeConfirmModal();
        }
      }
    }
    if (event.key === 'Tab' && modal) {
      trapModalFocus(event, modal);
    }
    if (event.key === 'Enter' || event.key === ' ') {
      const el = event.target.closest('[data-action]:not(button):not(a)');
      if (el) {
        event.preventDefault();
        el.click();
      }
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

function getOpenModal() {
  return [...document.querySelectorAll('.modal-overlay')]
    .find(modal => modal.style.display !== 'none') || null;
}

function trapModalFocus(event, modal) {
  const focusable = [...modal.querySelectorAll('button, input, select, textarea, [tabindex]:not([tabindex="-1"])')]
    .filter(el => !el.disabled && el.offsetParent !== null);
  if (!focusable.length) return;
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}

function setStatus(type, text) {
  clearBannerTimer();
  setState('statusMessage', { type, text });
  const activeSession = getState('activeSession');
  renderApp({
    list: false,
    subtitle: false,
    detail: Boolean(activeSession)
  });
  const delay = type === 'success' ? 5000 : type === 'error' ? 10000 : 0;
  if (delay > 0) {
    setState('statusMessageDismissTimer', setTimeout(() => {
      setState('statusMessageDismissTimer', null);
      setState('statusMessage', null);
      renderBanner();
    }, delay));
  }
}

function setConnectionStatus(status) {
  const dot = document.getElementById('connection-status');
  if (!dot) return;
  dot.className = `connection-dot${status ? ' ' + status : ''}`;
  const labels = { live: 'Connected — live updates active', offline: 'Disconnected — retrying…' };
  const label = labels[status] || 'Connecting…';
  dot.setAttribute('aria-label', label);
  dot.title = label;
}

async function connectEventStream() {
  const currentEventSource = getState('eventSource');
  if (currentEventSource) {
    currentEventSource.close();
  }
  setConnectionStatus('');
  try {
    await fetchJson('/api/config');
  } catch (error) {
    setConnectionStatus('offline');
    setState('streamMessage', { type: 'error', text: error.message });
    renderApp({ list: false, subtitle: false, detail: false });
    return;
  }
  const nextEventSource = new EventSource('/api/events');
  setState('eventSource', nextEventSource);
  nextEventSource.addEventListener('open', () => {
    setConnectionStatus('live');
    setState('streamMessage', null);
    renderApp({ list: false, subtitle: false, detail: false });
  });
  nextEventSource.addEventListener('error', () => {
    setConnectionStatus('offline');
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

async function clearRequestFilters() {
  patchState({
    requestSearchValue: '',
    requestMethodFilterValue: '',
    requestStatusCodeFilterValue: ''
  });
  const activeRoute = getState('activeRoute');
  if (!activeRoute) return;
  await loadRequestsForRoute(activeRoute);
  renderRequestTable();
}

bindUiEvents();
initializeTheme();
initApp();
