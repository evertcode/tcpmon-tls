window.uiState = {
  allSessions: [],
  allRequests: [],
  activeRoute: null,
  lastLoadedSession: null,
  proxyConfig: null,
  diffMode: false,
  activeSession: null,
  activeExchangeIndex: 0,
  requestPage: 1,
  requestPageSize: 10,
  requestSearchValue: '',
  requestMethodFilterValue: '',
  requestStatusCodeFilterValue: '',
  requestSearchDebounceTimer: null,
  statusMessage: null,
  streamMessage: { type: 'info', text: 'Connecting live updates...' },
  eventsExpanded: true,
  eventsScrollTop: 0,
  eventSource: null,
  scheduledDetailRefreshTimer: null,
  scheduledListRefreshTimer: null,
  pendingDetailRefresh: false,
  pendingListRefresh: false,
  detailRefreshInFlight: false,
  listRefreshInFlight: false,
  authPromptInFlight: null,
  configPanelOpen: false,
  payloadHeadersExpanded: {
    Request: true,
    Response: true
  }
};

function getState(key) {
  return window.uiState[key];
}

function setState(key, value) {
  window.uiState[key] = value;
  return value;
}

function patchState(patch) {
  Object.assign(window.uiState, patch);
  return window.uiState;
}
