async function loadSessionDetails(sessionId) {
  const payloadsEl = document.getElementById('payloads');
  if (payloadsEl) payloadsEl.classList.add('loading-overlay');
  const data = await fetchJson('/api/sessions/' + sessionId);
  setState('lastLoadedSession', data);
  if (getState('activeSession') === sessionId) {
    renderRouteHeader();
  }
  if (payloadsEl) payloadsEl.classList.remove('loading-overlay');
  const exchanges = data.exchanges || [];
  const activeExchangeIndex = getState('activeExchangeIndex');
  if (activeExchangeIndex >= exchanges.length) {
    setState('activeExchangeIndex', 0);
  }
  const selectedExchangeIndex = getState('activeExchangeIndex');
  const activeExchange = exchanges[selectedExchangeIndex] || {};
  renderPayloads(activeExchange, data);
  renderEventsAndEditor(data);
}

function renderPayloads(activeExchange, data) {
  const request = activeExchange.request || data.latestRequest;
  const response = activeExchange.response || data.latestResponse;
  const payloads = document.getElementById('payloads');
  const children = [];
  const tlsPanel = buildTlsPanel(data);
  if (tlsPanel) {
    children.push(tlsPanel);
  }
  const grid = document.createElement('section');
  grid.className = 'payload-grid';
  grid.append(
    buildPayloadCard('Request', request, 'CLIENT_TO_TARGET', data),
    buildPayloadCard('Response', response, 'TARGET_TO_CLIENT', data)
  );
  children.push(grid);
  payloads.replaceChildren(...children);
}

function buildTlsPanel(data) {
  const inbound = data.inboundTls || {};
  const outbound = data.outboundTls || {};
  if (!inbound.protocol && !inbound.cipherSuite && !outbound.protocol && !outbound.cipherSuite) return null;

  const details = document.createElement('details');
  details.className = 'route-card';
  details.style.marginBottom = '12px';

  const summary = document.createElement('summary');
  summary.style.cursor = 'pointer';
  summary.style.listStyle = 'none';
  summary.style.display = 'flex';
  summary.style.justifyContent = 'space-between';
  summary.style.alignItems = 'center';
  summary.style.padding = '2px 0';

  const title = document.createElement('strong');
  title.style.fontSize = '13px';
  title.textContent = 'TLS';

  const subtitle = document.createElement('span');
  subtitle.className = 'muted';
  subtitle.style.fontSize = '11px';
  subtitle.textContent = `${inbound.protocol || ''}${inbound.protocol && outbound.protocol ? ' / ' : ''}${inbound.protocol !== outbound.protocol ? outbound.protocol || '' : ''}`;
  summary.append(title, subtitle);

  const section = document.createElement('div');
  section.className = 'tls-section';
  section.style.paddingBottom = '12px';
  section.append(
    buildTlsColumn('Inbound (client → proxy)', inbound),
    buildTlsColumn('Outbound (proxy → target)', outbound)
  );

  details.append(summary, section);
  return details;
}

function calcTtfb(events) {
  if (!Array.isArray(events)) return null;
  const firstReq = events.find(e => e.type === 'PAYLOAD' && isClientToTarget(e.direction));
  const firstRes = events.find(e => e.type === 'PAYLOAD' && isTargetToClient(e.direction));
  if (!firstReq || !firstRes) return null;
  const t1 = new Date(firstReq.timestamp).getTime();
  const t2 = new Date(firstRes.timestamp).getTime();
  if (isNaN(t1) || isNaN(t2) || t2 < t1) return null;
  return t2 - t1;
}

function buildTlsColumn(label, tls) {
  const column = document.createElement('div');
  column.className = 'tls-col';

  const title = document.createElement('span');
  title.className = 'label';
  title.textContent = label;
  column.appendChild(title);

  const rows = [];
  if (tls.protocol) rows.push(buildTlsRow('Protocol', tls.protocol));
  if (tls.cipherSuite) rows.push(buildTlsRow('Cipher', tls.cipherSuite));
  if (tls.sni) rows.push(buildTlsRow('SNI', tls.sni));
  if (tls.peerCertCount != null) rows.push(buildTlsRow('Peer certs', tls.peerCertCount));
  if (tls.tlsVersion) rows.push(buildTlsRow('Version', tls.tlsVersion));

  if (!rows.length) {
    const emptyRow = document.createElement('div');
    emptyRow.className = 'tls-row';
    const emptyLabel = document.createElement('span');
    emptyLabel.className = 'tls-key muted';
    emptyLabel.textContent = 'No details';
    emptyRow.appendChild(emptyLabel);
    column.appendChild(emptyRow);
    return column;
  }

  column.append(...rows);
  return column;
}

function buildTlsRow(key, value) {
  const row = document.createElement('div');
  row.className = 'tls-row';
  const keyEl = document.createElement('span');
  keyEl.className = 'tls-key';
  keyEl.textContent = key;
  const valueEl = document.createElement('span');
  valueEl.className = 'tls-val';
  valueEl.textContent = String(value);
  row.append(keyEl, valueEl);
  return row;
}

function buildPayloadHeader(title, timestamp, size, chunkText, direction, ttfb) {
  const header = document.createElement('div');
  header.className = 'payload-header';

  const left = document.createElement('div');
  const heading = document.createElement('h3');
  heading.textContent = title;
  const meta = document.createElement('div');
  meta.className = 'muted';
  meta.textContent = `${timestamp} / ${size} bytes${chunkText}`;
  left.append(heading, meta);

  if (ttfb !== null) {
    const ttfbWrap = document.createElement('span');
    ttfbWrap.className = 'muted';
    ttfbWrap.style.fontSize = '11px';
    ttfbWrap.append('TTFB: ');
    const ttfbValue = document.createElement('span');
    ttfbValue.className = ttfb < 200 ? 'timing-fast' : ttfb < 1000 ? 'timing-medium' : 'timing-slow';
    ttfbValue.textContent = `${ttfb} ms`;
    ttfbWrap.appendChild(ttfbValue);
    left.appendChild(ttfbWrap);
  }

  const pill = document.createElement('span');
  pill.className = 'pill route';
  pill.textContent = direction;

  header.append(left, pill);
  return header;
}

function buildPayloadEmptySection(message) {
  const section = document.createElement('div');
  section.className = 'payload-section muted';
  section.textContent = message;
  return section;
}

function buildStartLineSection(text) {
  const section = document.createElement('div');
  section.className = 'payload-section';
  const label = document.createElement('span');
  label.className = 'label';
  label.textContent = 'Start line';
  const pre = document.createElement('pre');
  pre.textContent = text;
  section.append(label, pre);
  return section;
}

function buildPayloadHeadersDetails(title, headers, decoded, isRequest, payloadHeadersExpanded) {
  const details = document.createElement('details');
  details.className = 'payload-details';
  details.dataset.action = 'toggle-payload-headers';
  details.dataset.title = title;
  details.open = Boolean(payloadHeadersExpanded[title]);

  const summary = document.createElement('summary');
  summary.textContent = 'Headers';

  const body = document.createElement('div');
  body.className = 'payload-details-body';
  body.appendChild(renderHeadersTable(headers, decoded, isRequest));

  details.append(summary, body);
  return details;
}

function buildPayloadBodySection(bodyText, hasBody, isRequest, bodyTruncated, sessionId, exchangeIndex) {
  const body = document.createElement('div');
  body.className = 'payload-body';

  const head = document.createElement('div');
  head.className = 'payload-body-head';
  const label = document.createElement('span');
  label.className = 'label';
  label.textContent = 'Body';
  head.appendChild(label);

  if (hasBody && !bodyTruncated) {
    const copyBtn = document.createElement('button');
    copyBtn.className = 'utility';
    copyBtn.dataset.action = 'copy-current-body';
    copyBtn.dataset.isRequest = String(isRequest);
    copyBtn.textContent = 'Copy body';
    head.appendChild(copyBtn);
  }

  const pre = document.createElement('pre');
  pre.className = 'scroll';
  pre.textContent = bodyText;

  if (bodyTruncated) {
    const expandBtn = document.createElement('button');
    expandBtn.className = 'utility';
    expandBtn.style.marginTop = '6px';
    expandBtn.textContent = 'Load full body';
    expandBtn.addEventListener('click', async () => {
      expandBtn.disabled = true;
      expandBtn.textContent = 'Loading…';
      try {
        const direction = isRequest ? 'request' : 'response';
        const data = await fetchJson(
          `/api/sessions/${sessionId}/exchanges/${exchangeIndex}/body?direction=${direction}`
        );
        pre.textContent = data.bodyText || '';
        const copyBtn = document.createElement('button');
        copyBtn.className = 'utility';
        copyBtn.dataset.action = 'copy-current-body';
        copyBtn.dataset.isRequest = String(isRequest);
        copyBtn.textContent = 'Copy body';
        head.appendChild(copyBtn);
        expandBtn.remove();
      } catch {
        expandBtn.textContent = 'Load failed — retry?';
        expandBtn.disabled = false;
      }
    });
    body.append(head, pre, expandBtn);
  } else {
    body.append(head, pre);
  }

  return body;
}

function buildPayloadCard(title, payload, expectedDirection, data) {
  const payloadHeadersExpanded = getState('payloadHeadersExpanded');
  const article = document.createElement('article');
  article.className = 'payload-card';
  if (!payload) {
    article.append(
      buildPayloadHeader(title, '', 0, '', expectedDirection, null),
      buildPayloadEmptySection(`No ${title.toLowerCase()} payload captured yet.`)
    );
    return article;
  }
  const decoded = payload.decoded || {};
  const headers = Array.isArray(decoded.headers) ? decoded.headers : [];
  const bodyText = formatBody(decoded);
  const hasBody = Boolean(bodyText);
  const bodyTruncated = Boolean(decoded.bodyTruncated);
  const chunkText = payload.chunkCount ? ` / ${payload.chunkCount} chunks` : '';
  const actions = title === 'Request' ? renderRequestActions(data, exchangeIndex) : null;
  const isRequest = title === 'Request';
  const sessionId = data?.sessionId || '';
  const exchangeIndex = getState('activeExchangeIndex') || 0;
  let ttfb = null;
  if (title === 'Response' && data && Array.isArray(data.events)) {
    ttfb = calcTtfb(data.events);
  }
  article.append(
    buildPayloadHeader(title, payload.timestamp || '', payload.size || 0, chunkText, payload.direction || expectedDirection, ttfb),
    buildStartLineSection(decoded.startLine || 'No HTTP start line'),
    buildPayloadHeadersDetails(title, headers, decoded, isRequest, payloadHeadersExpanded),
    buildPayloadBodySection(bodyText || 'No body captured', hasBody, isRequest, bodyTruncated, sessionId, exchangeIndex)
  );
  if (actions) {
    article.appendChild(actions);
  }
  return article;
}

function renderWaterfall(data) {
  const events = Array.isArray(data.events) ? data.events : [];
  function firstTs(type) {
    const ev = events.find(e => e.type === type);
    return ev ? new Date(ev.timestamp).getTime() : null;
  }
  function firstPayloadTs(matchDirection) {
    const ev = events.find(e => e.type === 'PAYLOAD' && matchDirection(e.direction));
    return ev ? new Date(ev.timestamp).getTime() : null;
  }
  function lastPayloadTs(matchDirection) {
    const evs = events.filter(e => e.type === 'PAYLOAD' && matchDirection(e.direction));
    return evs.length ? new Date(evs[evs.length - 1].timestamp).getTime() : null;
  }
  function fmtMs(ta, tb) {
    const d = tb - ta;
    if (d < 1) return '< 1 ms';
    return d < 1000 ? d + ' ms' : (d / 1000).toFixed(2) + ' s';
  }
  const t0 = data.startedAt ? new Date(data.startedAt).getTime() : firstTs('CLIENT_CONNECTED');
  const tClientConn = firstTs('CLIENT_CONNECTED');
  const tTlsIn = firstTs('TLS_INBOUND');
  const tTlsOut = firstTs('TLS_OUTBOUND');
  const tTargetConn = firstTs('TARGET_CONNECTED');
  const tFirstReq = firstPayloadTs(isClientToTarget);
  const tFirstRes = firstPayloadTs(isTargetToClient);
  const tLastRes = lastPayloadTs(isTargetToClient);
  const tEnd = data.endedAt ? new Date(data.endedAt).getTime() : tLastRes || firstTs('CLIENT_CLOSED');
  if (!t0 || !tEnd || tEnd <= t0) {
    return '<div class="wf-empty">Timing data not available for this session.</div>';
  }
  const total = tEnd - t0;
  function pct(t) { return ((t - t0) / total * 100).toFixed(2); }
  function wpct(ta, tb) { return Math.max(0.3, (tb - ta) / total * 100).toFixed(2); }
  function wfBar(left, w, cls) {
    return '<div class="wf-bar ' + cls + '" style="left:' + left + '%;width:' + w + '%;"></div>';
  }
  function wfRow(label, barHtml, dur, extra) {
    return '<div class="wf-row' + (extra || '') + '">'
      + '<span class="wf-label">' + escapeHtml(label) + '</span>'
      + '<div class="wf-track">' + barHtml + '</div>'
      + '<span class="wf-dur">' + escapeHtml(dur) + '</span>'
      + '</div>';
  }
  const rows = [];
  if (tClientConn && tTlsIn && tTlsIn > tClientConn) {
    rows.push(wfRow('TLS Inbound', wfBar(pct(tClientConn), wpct(tClientConn, tTlsIn), 'wf-bar-tls-in'), fmtMs(tClientConn, tTlsIn)));
  }
  const connStart = tTlsIn || tClientConn || t0;
  const connEnd = tTlsOut || tTargetConn;
  if (connEnd && connEnd > connStart) {
    const label = tTlsOut ? 'TLS Outbound' : 'Connect';
    const cls = tTlsOut ? 'wf-bar-tls-out' : 'wf-bar-connect';
    rows.push(wfRow(label, wfBar(pct(connStart), wpct(connStart, connEnd), cls), fmtMs(connStart, connEnd)));
  }
  if (tFirstReq && tFirstRes && tFirstRes > tFirstReq) {
    rows.push(wfRow('Wait (TTFB)', wfBar(pct(tFirstReq), wpct(tFirstReq, tFirstRes), 'wf-bar-wait'), fmtMs(tFirstReq, tFirstRes)));
  }
  if (tFirstRes && tLastRes && tLastRes > tFirstRes) {
    rows.push(wfRow('Download', wfBar(pct(tFirstRes), wpct(tFirstRes, tLastRes), 'wf-bar-dl'), fmtMs(tFirstRes, tLastRes)));
  }
  if (!rows.length) {
    return '<div class="wf-empty">Not enough events to calculate timing breakdown.</div>';
  }
  const totalRow = wfRow('Total', wfBar('0', '100', 'wf-bar-total'), fmtMs(t0, tEnd), ' wf-row-total');
  return '<div class="waterfall">' + rows.join('') + '<div class="wf-sep"></div>' + totalRow + '</div>';
}

function renderEventsAndEditor(data) {
  const eventsExpanded = getState('eventsExpanded');
  const exchanges = data.exchanges || [];
  const events = data.events || [];
  const pendingEvents = events.filter(event => event.pendingId);
  const startMs = data.startedAt ? new Date(data.startedAt).getTime() : null;
  const endMs = data.endedAt ? new Date(data.endedAt).getTime() : null;
  const durLabel = (startMs && endMs && endMs > startMs)
    ? ' \u00b7 ' + (endMs - startMs < 1000 ? (endMs - startMs) + ' ms' : ((endMs - startMs) / 1000).toFixed(2) + ' s')
    : '';
  const container = document.getElementById('events-and-editor');
  const details = document.createElement('details');
  details.className = 'events-card';
  details.dataset.action = 'toggle-events-expanded';
  details.open = eventsExpanded;

  const summary = document.createElement('summary');
  summary.style.display = 'flex';
  summary.style.justifyContent = 'space-between';
  summary.style.alignItems = 'center';
  summary.style.gap = '12px';
  summary.style.cursor = 'pointer';

  const summaryTitle = document.createElement('span');
  const titleStrong = document.createElement('strong');
  titleStrong.textContent = 'Timing';
  summaryTitle.appendChild(titleStrong);

  const summaryMeta = document.createElement('span');
  summaryMeta.className = 'muted';
  summaryMeta.appendChild(document.createTextNode(`${exchanges.length} exchange${exchanges.length !== 1 ? 's' : ''}${durLabel}`));
  if (pendingEvents.length) {
    const badge = document.createElement('span');
    badge.style.color = 'var(--warn)';
    badge.style.fontWeight = '700';
    badge.textContent = ` · ${pendingEvents.length} intercepted`;
    summaryMeta.appendChild(badge);
  }
  summary.append(summaryTitle, summaryMeta);

  const body = document.createElement('div');
  body.style.marginTop = '12px';
  body.appendChild(buildExchangeButtons(exchanges));
  if (pendingEvents.length) {
    body.appendChild(buildInterceptPanel(pendingEvents));
  }
  body.appendChild(htmlToFragment(renderWaterfall(data)));

  details.append(summary, body);

  const editor = document.createElement('div');
  editor.id = 'editor';
  container.replaceChildren(details, editor);
}

function buildInterceptPanel(pendingEvents) {
  const count = pendingEvents.length;
  const panel = document.createElement('div');
  panel.className = 'intercept-panel';

  const header = document.createElement('div');
  header.className = 'intercept-panel-header';

  const title = document.createElement('strong');
  title.style.color = 'var(--warn)';
  title.textContent = `⚠ ${count} payload${count !== 1 ? 's' : ''} intercepted`;

  const subtitle = document.createElement('span');
  subtitle.className = 'muted';
  subtitle.textContent = 'Waiting for your action';

  header.append(title, subtitle);
  panel.appendChild(header);

  for (const event of pendingEvents) {
    const decoded = event.decoded || {};
    const isOut = isClientToTarget(event.direction);
    const preview = decoded.startLine || (isOut ? '\u2192 Outbound payload' : '\u2190 Inbound payload');
    panel.appendChild(buildInterceptItem(event, preview));
  }

  return panel;
}

function renderTimelineItem(event) {
  const cfg = timelineConfig(event);
  const time = formatTime(event.timestamp);
  const detail = cfg.detail
    ? `<div class="tl-detail">${escapeHtml(cfg.detail)}</div>`
    : '';
  const pendingActions = event.pendingId ? `
    <div style="display:flex;gap:8px;margin-top:8px;">
      <button class="secondary" data-action="release-pending" data-pending-id="${escapeAttr(event.pendingId)}">Forward original</button>
      <button class="primary" data-action="show-edit-pending" data-pending-id="${escapeAttr(event.pendingId)}" data-decoded-payload='${escapeAttr(JSON.stringify(event.decoded || null))}' data-base64-value="${escapeAttr(event.details?.base64 || '')}">Edit and forward</button>
    </div>` : '';
  return `
    <div class="tl-item">
      <div class="tl-dot ${cfg.dotClass}">${cfg.icon}</div>
      <div class="tl-body">
        <div class="tl-label">
          <strong>${escapeHtml(cfg.label)}</strong>
          <span class="muted" style="font-size:11px;white-space:nowrap;">${escapeHtml(time)}</span>
        </div>
        ${detail}
        ${pendingActions}
      </div>
    </div>
  `;
}

function buildInterceptItem(event, preview) {
  const item = document.createElement('div');
  item.className = 'intercept-item';

  const top = document.createElement('div');
  top.style.display = 'flex';
  top.style.justifyContent = 'space-between';
  top.style.alignItems = 'center';
  top.style.gap = '8px';
  top.style.marginBottom = '10px';

  const previewEl = document.createElement('span');
  previewEl.className = 'mono';
  previewEl.style.fontSize = '12px';
  previewEl.style.overflow = 'hidden';
  previewEl.style.textOverflow = 'ellipsis';
  previewEl.style.whiteSpace = 'nowrap';
  previewEl.textContent = preview;
  top.appendChild(previewEl);

  if (event.size) {
    const size = document.createElement('span');
    size.className = 'muted';
    size.style.flexShrink = '0';
    size.textContent = `${event.size} B`;
    top.appendChild(size);
  }

  const actions = document.createElement('div');
  actions.style.display = 'flex';
  actions.style.gap = '8px';
  actions.append(
    buildPendingActionButton('secondary', 'release-pending', event.pendingId, 'Forward original'),
    buildEditPendingButton(event)
  );

  item.append(top, actions);
  return item;
}

function buildPendingActionButton(className, action, pendingId, label) {
  const button = document.createElement('button');
  button.className = className;
  button.dataset.action = action;
  button.dataset.pendingId = pendingId;
  button.textContent = label;
  return button;
}

function buildEditPendingButton(event) {
  const button = buildPendingActionButton('primary', 'show-edit-pending', event.pendingId, 'Edit and forward');
  button.dataset.decodedPayload = JSON.stringify(event.decoded || null);
  button.dataset.base64Value = event.details?.base64 || '';
  return button;
}

function htmlToFragment(html) {
  const range = document.createRange();
  return range.createContextualFragment(html);
}

function timelineConfig(event) {
  const type = event.type || '';
  const dir = String(event.direction || '');
  const details = event.details || {};
  const decoded = event.decoded || {};
  const isPending = !!event.pendingId;
  switch (type) {
    case 'CLIENT_CONNECTED':
      return { icon: '\u2192', dotClass: 'dot-accent', label: 'Client connected', detail: details.client || '' };
    case 'TARGET_CONNECTED':
      return { icon: '\u2192', dotClass: 'dot-ok', label: 'Target connected', detail: details.target || '' };
    case 'TLS_INBOUND':
      return { icon: '\u25b2', dotClass: 'dot-ok', label: 'TLS inbound', detail: details.sni ? 'SNI: ' + details.sni : 'Handshake OK' };
    case 'TLS_OUTBOUND':
      return { icon: '\u25b2', dotClass: 'dot-ok', label: 'TLS outbound', detail: 'Handshake OK' };
    case 'TLS_INBOUND_FAILED':
      return { icon: '\u2715', dotClass: 'dot-danger', label: 'TLS inbound failed', detail: details.error || '' };
    case 'TLS_OUTBOUND_FAILED':
      return { icon: '\u2715', dotClass: 'dot-danger', label: 'TLS outbound failed', detail: details.error || '' };
    case 'PAYLOAD': {
      const isOut = isClientToTarget(dir);
      const startLine = decoded.startLine || '';
      const sizeStr = event.size ? ' \u00b7 ' + event.size + ' B' : '';
      return {
        icon: isOut ? '\u2192' : '\u2190',
        dotClass: isPending ? 'dot-warn' : (isOut ? 'dot-accent' : 'dot-ok'),
        label: isPending ? (isOut ? 'Request intercepted' : 'Response intercepted') : (isOut ? 'Request' : 'Response'),
        detail: startLine + sizeStr
      };
    }
    case 'CLIENT_CLOSED':
      return { icon: '\u25cb', dotClass: 'dot-muted', label: 'Session closed', detail: '' };
    case 'PENDING_RELEASED':
      return { icon: '\u2713', dotClass: 'dot-ok', label: 'Payload released', detail: '' };
    default:
      return { icon: '\u00b7', dotClass: 'dot-muted', label: type, detail: '' };
  }
}

function buildExchangeButtons(exchanges) {
  const diffMode = getState('diffMode');
  const activeExchangeIndex = getState('activeExchangeIndex');
  if (exchanges.length <= 1) {
    setState('diffMode', false);
    return document.createDocumentFragment();
  }
  const fragment = document.createDocumentFragment();
  const actions = document.createElement('div');
  actions.className = 'actions';
  actions.style.margin = '0 0 10px';

  for (const exchange of exchanges) {
    const button = document.createElement('button');
    button.className = !diffMode && exchange.index === activeExchangeIndex ? 'primary' : 'secondary';
    button.dataset.action = 'select-exchange';
    button.dataset.exchangeIndex = String(exchange.index);
    button.textContent = String(exchange.index + 1);
    actions.appendChild(button);
  }

  if (exchanges.length >= 2) {
    const compareButton = document.createElement('button');
    compareButton.className = diffMode ? 'primary' : 'secondary';
    compareButton.dataset.action = 'toggle-diff-mode';
    compareButton.textContent = 'Compare';
    actions.appendChild(compareButton);
  }

  fragment.appendChild(actions);
  if (diffMode) {
    fragment.appendChild(buildExchangeDiff(exchanges));
  }
  return fragment;
}

function toggleDiffMode() {
  const diffMode = getState('diffMode');
  const lastLoadedSession = getState('lastLoadedSession');
  const activeExchangeIndex = getState('activeExchangeIndex');
  setState('diffMode', !diffMode);
  if (lastLoadedSession) {
    renderEventsAndEditor(lastLoadedSession);
    renderPayloads(
      (lastLoadedSession.exchanges || [])[activeExchangeIndex] || {},
      lastLoadedSession
    );
  }
}

function buildExchangeDiff(exchanges) {
  if (exchanges.length < 2) return document.createDocumentFragment();
  const rows = [];
  const ex0 = exchanges[0];
  const ex1 = exchanges[1];
  const status0 = extractExchangeStatus(ex0);
  const status1 = extractExchangeStatus(ex1);
  rows.push(buildDiffRow('Status', status0, status1));
  const reqH0 = extractHeaders(ex0.request);
  const reqH1 = extractHeaders(ex1.request);
  const allReqKeys = [...new Set([...Object.keys(reqH0), ...Object.keys(reqH1)])].sort();
  for (const key of allReqKeys) {
    const v0 = reqH0[key] ?? '';
    const v1 = reqH1[key] ?? '';
    if (v0 !== v1) rows.push(buildDiffRow('Req: ' + key, v0, v1));
  }
  const resH0 = extractHeaders(ex0.response);
  const resH1 = extractHeaders(ex1.response);
  const allResKeys = [...new Set([...Object.keys(resH0), ...Object.keys(resH1)])].sort();
  for (const key of allResKeys) {
    const v0 = resH0[key] ?? '';
    const v1 = resH1[key] ?? '';
    if (v0 !== v1) rows.push(buildDiffRow('Res: ' + key, v0, v1));
  }
  if (!rows.length) {
    const empty = document.createElement('div');
    empty.className = 'muted';
    empty.style.fontSize = '12px';
    empty.style.marginBottom = '10px';
    empty.textContent = 'No differences between Exchange 1 and Exchange 2.';
    return empty;
  }

  const wrap = document.createElement('div');
  wrap.style.marginBottom = '12px';
  wrap.style.border = '1px solid var(--border)';
  wrap.style.borderRadius = '10px';
  wrap.style.overflow = 'hidden';

  const table = document.createElement('table');
  table.style.width = '100%';
  table.style.fontSize = '12px';
  table.style.borderCollapse = 'collapse';

  const thead = document.createElement('thead');
  const headerRow = document.createElement('tr');
  headerRow.style.background = 'var(--surface-2)';
  headerRow.append(
    buildDiffHeader('Field', '28%'),
    buildDiffHeader('Exchange 1'),
    buildDiffHeader('Exchange 2')
  );
  thead.appendChild(headerRow);

  const tbody = document.createElement('tbody');
  tbody.append(...rows);
  table.append(thead, tbody);
  wrap.appendChild(table);
  return wrap;
}

function buildDiffHeader(label, width = null) {
  const th = document.createElement('th');
  th.style.padding = '8px 10px';
  th.style.textAlign = 'left';
  th.style.color = 'var(--text-muted)';
  th.style.fontSize = '11px';
  th.style.textTransform = 'uppercase';
  th.style.letterSpacing = '.04em';
  if (width) {
    th.style.width = width;
  }
  th.textContent = label;
  return th;
}

function buildDiffRow(label, v0, v1) {
  const changed = v0 !== v1;
  const row = document.createElement('tr');
  if (changed) {
    row.style.background = 'rgba(161,92,7,0.05)';
  }
  row.style.borderBottom = '1px solid var(--border)';
  row.append(
    buildDiffLabelCell(label),
    buildDiffValueCell(v0, v1, changed),
    buildDiffValueCell(v1, v0, changed)
  );
  return row;
}

function buildDiffLabelCell(label) {
  const td = document.createElement('td');
  td.style.padding = '7px 10px';
  td.style.color = 'var(--text-muted)';
  td.textContent = label;
  return td;
}

function buildDiffValueCell(value, otherValue, changed) {
  const td = document.createElement('td');
  td.style.padding = '7px 10px';
  const span = document.createElement('span');
  if (!value && otherValue) {
    span.style.color = 'var(--text-muted)';
    span.textContent = '—';
  } else {
    span.style.fontFamily = 'var(--mono)';
    span.style.wordBreak = 'break-all';
    if (value && !otherValue) {
      span.style.color = 'var(--ok)';
    } else if (!changed) {
      span.style.color = 'var(--text-muted)';
    }
    span.textContent = value;
  }
  td.appendChild(span);
  return td;
}

function extractExchangeStatus(exchange) {
  if (!exchange?.response) return '';
  const startLine = exchange.response.decoded?.startLine || '';
  return startLine.split(' ').slice(1, 3).join(' ');
}

function extractHeaders(payload) {
  if (!payload) return {};
  const headers = payload.decoded?.headers || [];
  const result = {};
  for (const h of headers) {
    if (h.name) result[String(h.name).toLowerCase()] = String(h.value || '');
  }
  return result;
}

function setPayloadHeadersExpanded(title, open) {
  const payloadHeadersExpanded = {
    ...getState('payloadHeadersExpanded'),
    [title]: open
  };
  setState('payloadHeadersExpanded', payloadHeadersExpanded);
}

function setEventsExpanded(open) {
  setState('eventsExpanded', open);
}

function setEventsScroll(value) {
  setState('eventsScrollTop', value);
}

function restoreEventsScroll() {
  if (!getState('eventsExpanded')) {
    return;
  }
  requestAnimationFrame(() => {
    const list = document.getElementById('events-list');
    if (list) {
      list.scrollTop = getState('eventsScrollTop');
    }
  });
}

function renderDetailEmpty(message) {
  const empty = document.createElement('div');
  empty.className = 'empty';
  empty.textContent = message;
  document.getElementById('payloads').replaceChildren(empty);
  document.getElementById('events-and-editor').replaceChildren();
}

function renderEmptyState(message) {
  document.getElementById('status-banner').replaceChildren();

  const headerEmpty = document.createElement('div');
  headerEmpty.className = 'empty';
  headerEmpty.style.flexDirection = 'column';
  headerEmpty.style.gap = '6px';
  headerEmpty.append(
    document.createTextNode(message),
    buildEmptyStateHint('Proxy traffic through the configured listener to begin capturing.')
  );
  document.getElementById('route-header').replaceChildren(headerEmpty);

  document.getElementById('request-table').replaceChildren();
  document.getElementById('payloads').replaceChildren();
  document.getElementById('events-and-editor').replaceChildren();
}

function buildEmptyStateHint(text) {
  const hint = document.createElement('span');
  hint.className = 'muted';
  hint.style.fontSize = '11px';
  hint.style.textAlign = 'center';
  hint.textContent = text;
  return hint;
}
