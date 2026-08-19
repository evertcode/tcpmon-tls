function isSessionLive(session) {
  if (!session || session.live == null) {
    return String(session?.status || '').toUpperCase() === 'OPEN' && session?.durationMs == null;
  }
  return Boolean(session.live);
}

function isClientToTarget(direction) {
  return String(direction || '').toUpperCase() === 'CLIENT_TO_TARGET';
}

function isTargetToClient(direction) {
  return String(direction || '').toUpperCase() === 'TARGET_TO_CLIENT';
}

function looksLikeJson(value) {
  const text = String(value || '').trim();
  return text.startsWith('{') || text.startsWith('[');
}

function looksLikeXml(value) {
  return String(value || '').trim().startsWith('<');
}

function prettyPrintXml(value) {
  const compact = String(value || '').replace(/>\s+</g, '><').trim();
  if (!compact) {
    return value;
  }
  const tokens = compact.replace(/></g, '>\n<').split('\n');
  let indent = 0;
  const lines = [];
  for (const rawToken of tokens) {
    const token = rawToken.trim();
    if (!token) continue;
    if (token.startsWith('</')) {
      indent = Math.max(0, indent - 1);
    }
    lines.push('  '.repeat(indent) + token);
    if (token.startsWith('<') && !token.startsWith('</') && !token.endsWith('/>') && !token.includes('</')) {
      indent++;
    }
  }
  return lines.join('\n');
}

function formatBody(decoded) {
  const bodyText = decoded.bodyText || '';
  if (!decoded.isHttp || !bodyText) {
    return bodyText;
  }
  const headers = Array.isArray(decoded.headers) ? decoded.headers : [];
  const contentTypeHeader = headers.find(header => String(header.name || '').toLowerCase() === 'content-type');
  const contentType = String(contentTypeHeader?.value || '').toLowerCase();
  if (contentType.includes('json') || looksLikeJson(bodyText)) {
    try {
      return JSON.stringify(JSON.parse(bodyText), null, 2);
    } catch (error) {
      return bodyText;
    }
  }
  if (contentType.includes('xml') || contentType.includes('soap') || looksLikeXml(bodyText)) {
    return prettyPrintXml(bodyText);
  }
  return bodyText;
}

function setMethodSelectValue(selectId, method) {
  const select = document.getElementById(selectId);
  if (!select) return;
  const value = String(method || 'GET').toUpperCase();
  const hasOption = Array.from(select.options).some(option => option.value === value);
  if (!hasOption) {
    const option = document.createElement('option');
    option.value = value;
    option.textContent = value;
    select.appendChild(option);
  }
  select.value = value;
}

function buildHeaderPairRow(name = '', value = '') {
  const row = document.createElement('div');
  row.className = 'header-pair-row';
  const nameInput = document.createElement('input');
  nameInput.type = 'text';
  nameInput.className = 'header-pair-name';
  nameInput.placeholder = 'Name';
  nameInput.value = name;
  const valueInput = document.createElement('input');
  valueInput.type = 'text';
  valueInput.className = 'header-pair-value';
  valueInput.placeholder = 'Value';
  valueInput.value = value;
  const removeBtn = document.createElement('button');
  removeBtn.type = 'button';
  removeBtn.className = 'utility header-pair-remove';
  removeBtn.setAttribute('aria-label', 'Remove header');
  removeBtn.textContent = '✕';
  removeBtn.addEventListener('click', () => row.remove());
  row.append(nameInput, valueInput, removeBtn);
  return row;
}

function populateHeaderPairs(containerId, headers) {
  const container = document.getElementById(containerId);
  if (!container) return;
  container.replaceChildren();
  const list = Array.isArray(headers) && headers.length ? headers : [{ name: '', value: '' }];
  for (const header of list) {
    container.appendChild(buildHeaderPairRow(header.name || '', header.value || ''));
  }
}

function addHeaderPairRow(containerId) {
  const container = document.getElementById(containerId);
  if (!container) return;
  container.appendChild(buildHeaderPairRow());
}

function serializeHeaderPairs(containerId) {
  const container = document.getElementById(containerId);
  if (!container) return '';
  const lines = [];
  for (const row of container.children) {
    const name = row.querySelector('.header-pair-name')?.value.trim();
    const value = row.querySelector('.header-pair-value')?.value.trim() || '';
    if (name) lines.push(`${name}: ${value}`);
  }
  return lines.join('\n');
}

function formatBodyFieldInPlace(textareaId) {
  const field = document.getElementById(textareaId);
  if (!field) return;
  const value = field.value;
  if (looksLikeJson(value)) {
    try {
      field.value = JSON.stringify(JSON.parse(value), null, 2);
    } catch (error) {
      setStatus('error', 'Body is not valid JSON');
    }
    return;
  }
  if (looksLikeXml(value)) {
    field.value = prettyPrintXml(value);
  }
}

function buildComposerSummaryPills(containerId, routeId, listenerAddr, targetAddr) {
  const container = document.getElementById(containerId);
  if (!container) return;
  container.replaceChildren();
  const parts = [];
  if (routeId) parts.push(routeId);
  if (listenerAddr) parts.push(`Listener ${listenerAddr}`);
  if (targetAddr) parts.push(`Target ${targetAddr}`);
  for (const label of parts) {
    const pill = document.createElement('span');
    pill.className = 'pill route';
    pill.textContent = label;
    container.appendChild(pill);
  }
}

function getHeaderValue(headers, name) {
  if (!Array.isArray(headers)) {
    return '';
  }
  const expected = String(name || '').toLowerCase();
  const header = headers.find(item => String(item?.name || '').toLowerCase() === expected);
  return String(header?.value || '');
}

function detectBodyViewerMode(decoded) {
  const bodyText = String(decoded?.bodyText || '').trim();
  if (!bodyText) {
    return 'text';
  }
  if (!decoded?.isHttp) {
    return 'text';
  }
  const contentType = getHeaderValue(decoded.headers, 'content-type').toLowerCase();
  if (contentType.includes('json') || looksLikeJson(bodyText)) {
    return 'json';
  }
  if (contentType.includes('xml') || contentType.includes('soap') || looksLikeXml(bodyText)) {
    return 'xml';
  }
  return 'text';
}

function formatTime(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  const diffMs = Date.now() - date.getTime();
  const diffSec = Math.floor(diffMs / 1000);
  if (diffSec < 10) return 'just now';
  if (diffSec < 60) return diffSec + 's ago';
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return diffMin + 'm ago';
  const diffHr = Math.floor(diffMin / 60);
  if (diffHr < 24) return diffHr + 'h ago';
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function formatDuration(ms) {
  if (ms == null) return '<span class="muted">—</span>';
  const n = Number(ms);
  if (isNaN(n)) return '<span class="muted">—</span>';
  const cls = n < 200 ? 'timing-fast' : n < 1000 ? 'timing-medium' : 'timing-slow';
  const label = n < 1000 ? n + ' ms' : (n / 1000).toFixed(1) + ' s';
  return `<span class="${cls}">${escapeHtml(label)}</span>`;
}

function formatBytes(bytes) {
  if (bytes == null) return '<span class="muted">—</span>';
  const n = Number(bytes);
  if (isNaN(n) || n === 0) return '<span class="muted">0 B</span>';
  if (n < 1024) return escapeHtml(n + ' B');
  if (n < 1048576) return escapeHtml((n / 1024).toFixed(1) + ' KB');
  return escapeHtml((n / 1048576).toFixed(1) + ' MB');
}

function statusBadge(code) {
  const s = String(code ?? '');
  if (!s) return '';
  const first = s.charAt(0);
  const cls = first === '2' ? 'status-2xx' : first === '3' ? 'status-3xx' : first === '4' ? 'status-4xx' : first === '5' ? 'status-5xx' : 'status-other';
  return `<span class="status-badge ${cls}">${escapeHtml(s)}</span>`;
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;');
}

function escapeAttr(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('"', '&quot;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;');
}

function buildEmptyState(message, hint = '', action = null) {
  const empty = document.createElement('div');
  empty.className = 'empty empty-state';

  const body = document.createElement('div');
  body.className = 'empty-state-body';

  const title = document.createElement('strong');
  title.textContent = message;
  body.appendChild(title);

  if (hint) {
    const hintEl = document.createElement('span');
    hintEl.className = 'muted empty-state-hint';
    hintEl.textContent = hint;
    body.appendChild(hintEl);
  }

  if (action) {
    body.appendChild(action);
  }

  empty.appendChild(body);
  return empty;
}

const ICON_PATHS = {
  plus: '<path d="M12 5v14M5 12h14"/>',
  refresh: '<path d="M20 6v5h-5"/><path d="M4 18v-5h5"/><path d="M19 11a7 7 0 0 0-12-4l-3 3"/><path d="M5 13a7 7 0 0 0 12 4l3-3"/>',
  settings: '<path d="M12 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Z"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1A2 2 0 1 1 4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.6-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9l-.1-.1A2 2 0 1 1 7 4.2l.1.1a1.7 1.7 0 0 0 1.9.3h.1a1.7 1.7 0 0 0 1-1.6V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1A2 2 0 1 1 19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9v.1a1.7 1.7 0 0 0 1.6 1h.1a2 2 0 1 1 0 4H21a1.7 1.7 0 0 0-1.6 1Z"/>',
  edit: '<path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5Z"/>',
  trash: '<path d="M3 6h18"/><path d="M8 6V4h8v2"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v5"/><path d="M14 11v5"/>',
  download: '<path d="M12 3v12"/><path d="m7 10 5 5 5-5"/><path d="M5 21h14"/>',
  copy: '<path d="M8 8h11v11H8z"/><path d="M5 16H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h11a1 1 0 0 1 1 1v1"/>',
  replay: '<path d="M3 12a9 9 0 0 1 15-6.7L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-15 6.7L3 16"/><path d="M3 21v-5h5"/>',
  send: '<path d="m22 2-7 20-4-9-9-4 20-7Z"/><path d="M22 2 11 13"/>',
  more: '<circle cx="5" cy="12" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="19" cy="12" r="1.5"/>',
  close: '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>',
  file: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z"/><path d="M14 2v6h6"/>',
  grip: '<circle cx="9" cy="6" r="1.2"/><circle cx="9" cy="12" r="1.2"/><circle cx="9" cy="18" r="1.2"/><circle cx="15" cy="6" r="1.2"/><circle cx="15" cy="12" r="1.2"/><circle cx="15" cy="18" r="1.2"/>',
  expand: '<path d="M15 3h6v6"/><path d="m21 3-7 7"/><path d="m3 21 7-7"/><path d="M9 21H3v-6"/>',
  upload: '<path d="M12 21V9"/><path d="m7 14 5-5 5 5"/><path d="M5 3h14"/>'
};

function buildIcon(name) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('class', 'icon');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('aria-hidden', 'true');
  svg.setAttribute('focusable', 'false');
  svg.innerHTML = ICON_PATHS[name] || ICON_PATHS.file;
  return svg;
}

function setButtonContent(button, label, iconName = '', options = {}) {
  if (!button) return button;
  button.replaceChildren();
  if (iconName) {
    button.appendChild(buildIcon(iconName));
  }
  if (label) {
    const text = document.createElement('span');
    text.className = 'button-label';
    text.textContent = label;
    button.appendChild(text);
  } else {
    button.classList.add('icon-only');
  }
  if (options.ariaLabel) {
    button.setAttribute('aria-label', options.ariaLabel);
  }
  if (options.title) {
    button.title = options.title;
  }
  return button;
}

function routeEndpointLabel(routeId, endpoint = 'listener') {
  const config = getState('proxyConfig');
  const route = config && (config.routes || []).find(item => item.id === routeId);
  if (!route) return '';
  const side = endpoint === 'target' ? route.target : route.listener;
  return side && side.host && side.port ? `${side.host}:${side.port}` : '';
}

function activeRouteCaptureHint() {
  const routeId = getState('activeRoute');
  const listener = routeEndpointLabel(routeId, 'listener');
  const target = routeEndpointLabel(routeId, 'target');
  if (listener && target) {
    return `Send client traffic to ${listener}; tcpmon will forward it to ${target}.`;
  }
  if (listener) {
    return `Send client traffic to ${listener} to capture the first exchange.`;
  }
  return 'Send traffic through the selected listener to populate this route.';
}

function activeFilterSummary() {
  const parts = [];
  const query = String(getState('requestSearchValue') || '').trim();
  const method = getState('requestMethodFilterValue');
  const status = getState('requestStatusCodeFilterValue');
  if (query) parts.push(`query "${query}"`);
  if (method) parts.push(`method ${method}`);
  if (status) parts.push(`status ${status}`);
  return parts.join(', ');
}

function setFieldInvalid(field, message) {
  if (!field) return;
  field.setAttribute('aria-invalid', 'true');
  const details = field.closest ? field.closest('details') : null;
  if (details) details.open = true;
  const group = field.closest ? field.closest('.form-group') : null;
  if (!group) return;
  const existing = group.querySelector ? group.querySelector('.field-error') : null;
  if (existing) {
    existing.textContent = message;
    return;
  }
  const error = document.createElement('div');
  error.className = 'field-error';
  error.textContent = message;
  const id = field.id ? `${field.id}-error` : '';
  if (id) {
    error.id = id;
    field.setAttribute('aria-describedby', id);
  }
  group.appendChild(error);
}

function clearFieldInvalid(field) {
  if (!field) return;
  field.removeAttribute('aria-invalid');
  field.removeAttribute('aria-describedby');
  const group = field.closest ? field.closest('.form-group') : null;
  const error = group && group.querySelector ? group.querySelector('.field-error') : null;
  if (error) error.remove();
}
