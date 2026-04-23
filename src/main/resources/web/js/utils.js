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
