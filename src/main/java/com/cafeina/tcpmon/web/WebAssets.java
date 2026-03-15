package com.cafeina.tcpmon.web;

public final class WebAssets {
    private WebAssets() {
    }

    public static String indexHtml() {
        return htmlHead() + htmlBody() + htmlScript() + htmlRouteModalScript();
    }

    private static String htmlHead() {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>tcpmon-tls</title>
                  <style>
                    :root {
                      --canvas: #f4f5f7;
                      --surface: #ffffff;
                      --surface-2: #f8fafc;
                      --surface-3: #eef2f7;
                      --text: #18212f;
                      --text-muted: #5f6b7a;
                      --border: rgba(24, 33, 47, 0.12);
                      --border-strong: rgba(24, 33, 47, 0.2);
                      --accent: #0f6cbd;
                      --accent-strong: #0a5aa0;
                      --accent-soft: rgba(15, 108, 189, 0.1);
                      --route: #106c5a;
                      --ok: #13795b;
                      --warn: #a15c07;
                      --danger: #b42318;
                      --button-shadow: 0 1px 2px rgba(24, 33, 47, 0.08), 0 6px 14px rgba(24, 33, 47, 0.04);
                      --mono: "IBM Plex Mono", "SFMono-Regular", Consolas, monospace;
                      --sans: "Segoe UI", "Helvetica Neue", sans-serif;
                      --shadow: 0 10px 26px rgba(24, 33, 47, 0.06);
                    }
                    * { box-sizing: border-box; }
                    html { background: var(--canvas); }
                    body {
                      margin: 0;
                      min-height: 100vh;
                      background: var(--canvas);
                      color: var(--text);
                      font-family: var(--sans);
                    }
                    .app {
                      display: flex;
                      flex-direction: column;
                      min-height: 100vh;
                    }
                    .topbar {
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      gap: 16px;
                      padding: 12px 16px;
                      background: var(--surface);
                      border-bottom: 1px solid var(--border);
                    }
                    .topbar-title {
                      display: flex;
                      flex-direction: column;
                      gap: 2px;
                    }
                    .topbar-title strong {
                      font-size: 16px;
                    }
                    .layout {
                      display: grid;
                      grid-template-columns: 280px minmax(0, 1fr);
                      flex: 1;
                      min-height: 0;
                    }
                    .sidebar {
                      background: var(--surface);
                      border-right: 1px solid var(--border);
                      display: grid;
                      grid-template-rows: auto auto 1fr;
                      min-height: 0;
                    }
                    .sidebar-section {
                      padding: 12px;
                      border-bottom: 1px solid var(--border);
                    }
                    .sidebar h2,
                    .content h2,
                    .content h3 {
                      margin: 0;
                      font-size: 14px;
                    }
                    .toolbar {
                      display: grid;
                      gap: 8px;
                    }
                    input,
                    select,
                    textarea {
                      width: 100%;
                      border: 1px solid var(--border);
                      border-radius: 8px;
                      background: var(--surface);
                      color: var(--text);
                      font: inherit;
                      padding: 9px 10px;
                    }
                    input:focus,
                    select:focus,
                    textarea:focus {
                      outline: 2px solid rgba(15, 108, 189, 0.14);
                      border-color: rgba(15, 108, 189, 0.45);
                    }
                    button {
                      display: inline-flex;
                      align-items: center;
                      justify-content: center;
                      min-height: 34px;
                      border: 1px solid transparent;
                      border-radius: 10px;
                      padding: 8px 12px;
                      font: inherit;
                      font-weight: 600;
                      line-height: 1.2;
                      letter-spacing: 0.01em;
                      cursor: pointer;
                      white-space: nowrap;
                      transition: background-color 120ms ease, border-color 120ms ease, color 120ms ease, transform 120ms ease, box-shadow 120ms ease;
                    }
                    button:hover:not(:disabled) {
                      transform: translateY(-1px);
                    }
                    button:active:not(:disabled) {
                      transform: translateY(0);
                    }
                    button:focus-visible {
                      outline: 2px solid rgba(15, 108, 189, 0.18);
                      outline-offset: 2px;
                    }
                    button:disabled {
                      opacity: 0.45;
                      cursor: not-allowed;
                      transform: none;
                      box-shadow: none;
                    }
                    button.primary {
                      background: var(--accent);
                      border-color: rgba(10, 90, 160, 0.55);
                      color: white;
                      box-shadow: var(--button-shadow);
                    }
                    button.primary:hover:not(:disabled) {
                      background: var(--accent-strong);
                    }
                    button.secondary {
                      background: var(--surface-2);
                      border-color: rgba(24, 33, 47, 0.16);
                      color: var(--text);
                      box-shadow: 0 1px 2px rgba(24, 33, 47, 0.04);
                    }
                    button.secondary:hover:not(:disabled) {
                      background: var(--surface-3);
                      border-color: rgba(24, 33, 47, 0.24);
                    }
                    button.ghost {
                      background: rgba(255, 255, 255, 0.55);
                      border-color: rgba(24, 33, 47, 0.1);
                      color: var(--text-muted);
                    }
                    button.ghost:hover:not(:disabled) {
                      background: var(--surface);
                      border-color: rgba(24, 33, 47, 0.18);
                      color: var(--text);
                    }
                    button.utility {
                      min-height: 28px;
                      padding: 5px 9px;
                      border-radius: 8px;
                      background: transparent;
                      border-color: rgba(24, 33, 47, 0.08);
                      color: var(--text-muted);
                      font-size: 12px;
                      font-weight: 500;
                      box-shadow: none;
                    }
                    button.utility:hover:not(:disabled) {
                      background: var(--surface-2);
                      border-color: rgba(24, 33, 47, 0.16);
                      color: var(--text);
                    }
                    button.nav {
                      min-height: 30px;
                      padding: 6px 10px;
                      border-radius: 8px;
                      font-size: 12px;
                      font-weight: 600;
                    }
                    button.action-main {
                      min-width: 172px;
                      min-height: 38px;
                      padding: 10px 14px;
                    }
                    button.action-alt {
                      min-height: 38px;
                      padding: 10px 14px;
                    }
                    button.action-edit {
                      min-width: 170px;
                    }
                    .route-list {
                      overflow: auto;
                      padding: 8px;
                    }
                    .route-row {
                      border: 1px solid var(--border);
                      border-left: 3px solid var(--border);
                      border-radius: 10px;
                      padding: 10px 10px 10px 12px;
                      margin-bottom: 8px;
                      background: var(--surface);
                      cursor: pointer;
                      transition: background 100ms, border-color 100ms;
                    }
                    .route-row:hover {
                      border-color: var(--border-strong);
                      border-left-color: var(--border-strong);
                      background: var(--surface-2);
                    }
                    .route-row.active {
                      border-color: rgba(15, 108, 189, 0.3);
                      border-left-color: var(--accent);
                      background: var(--accent-soft);
                    }
                    .route-row.status-open {
                      border-left-color: var(--ok);
                    }
                    .route-row.status-error {
                      border-left-color: var(--danger);
                    }
                    .row-top,
                    .row-bottom {
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      gap: 8px;
                    }
                    .row-top {
                      margin-bottom: 4px;
                    }
                    .route-preview {
                      margin-top: 6px;
                      padding-top: 6px;
                      border-top: 1px solid var(--border);
                      font-family: var(--mono);
                      font-size: 11px;
                      color: var(--text-muted);
                      white-space: nowrap;
                      overflow: hidden;
                      text-overflow: ellipsis;
                    }
                    .route-preview .method-tag {
                      font-weight: 700;
                      color: var(--accent);
                      margin-right: 4px;
                    }
                    .pill {
                      display: inline-flex;
                      align-items: center;
                      border-radius: 999px;
                      padding: 3px 8px;
                      font-size: 11px;
                      line-height: 1;
                      border: 1px solid transparent;
                      white-space: nowrap;
                    }
                    .pill.route {
                      background: rgba(16, 108, 90, 0.08);
                      color: var(--route);
                      border-color: rgba(16, 108, 90, 0.18);
                    }
                    .pill.open {
                      background: rgba(19, 121, 91, 0.08);
                      color: var(--ok);
                    }
                    .pill.closed {
                      background: rgba(24, 33, 47, 0.06);
                      color: var(--text-muted);
                    }
                    .pill.error {
                      background: rgba(180, 35, 24, 0.08);
                      color: var(--danger);
                    }
                    .pill.pending {
                      background: rgba(161, 92, 7, 0.1);
                      color: var(--warn);
                      border-color: rgba(161, 92, 7, 0.2);
                    }
                    .pill.pending-alarm {
                      background: rgba(180, 35, 24, 0.1);
                      color: var(--danger);
                      border-color: rgba(180, 35, 24, 0.2);
                      animation: pulse-alarm 1.4s infinite;
                    }
                    @keyframes pulse-alarm {
                      0%, 100% { opacity: 1; }
                      50% { opacity: 0.55; }
                    }
                    .status-badge {
                      display: inline-flex;
                      align-items: center;
                      border-radius: 6px;
                      padding: 2px 6px;
                      font-family: var(--mono);
                      font-size: 11px;
                      font-weight: 600;
                      line-height: 1;
                      border: 1px solid transparent;
                    }
                    .status-2xx { background: rgba(19, 121, 91, 0.1); color: var(--ok); border-color: rgba(19, 121, 91, 0.2); }
                    .status-3xx { background: rgba(15, 108, 189, 0.1); color: var(--accent); border-color: rgba(15, 108, 189, 0.2); }
                    .status-4xx { background: rgba(161, 92, 7, 0.1); color: var(--warn); border-color: rgba(161, 92, 7, 0.2); }
                    .status-5xx { background: rgba(180, 35, 24, 0.1); color: var(--danger); border-color: rgba(180, 35, 24, 0.2); }
                    .status-other { background: rgba(24, 33, 47, 0.06); color: var(--text-muted); }
                    .timing-fast { color: var(--ok); font-weight: 600; }
                    .timing-medium { color: var(--warn); font-weight: 600; }
                    .timing-slow { color: var(--danger); font-weight: 600; }
                    .tls-section {
                      display: grid;
                      grid-template-columns: 1fr 1fr;
                      gap: 10px;
                      margin-top: 10px;
                      padding-top: 10px;
                      border-top: 1px solid var(--border);
                    }
                    .tls-col { font-size: 12px; }
                    .tls-col .label { margin-bottom: 6px; }
                    .tls-row {
                      display: flex;
                      gap: 8px;
                      margin-bottom: 4px;
                    }
                    .tls-row .tls-key {
                      color: var(--text-muted);
                      min-width: 80px;
                      flex-shrink: 0;
                    }
                    .tls-row .tls-val {
                      font-family: var(--mono);
                      word-break: break-all;
                    }
                    .config-panel {
                      background: var(--surface-2);
                      border: 1px solid var(--border);
                      border-radius: 10px;
                      padding: 10px 12px;
                      margin-top: 8px;
                      font-size: 12px;
                    }
                    .config-row {
                      display: flex;
                      gap: 8px;
                      margin-bottom: 4px;
                    }
                    .config-row .config-key {
                      color: var(--text-muted);
                      min-width: 110px;
                      flex-shrink: 0;
                    }
                    .config-row .config-val {
                      font-family: var(--mono);
                    }
                    .waterfall {
                      display: flex;
                      flex-direction: column;
                      gap: 5px;
                      padding: 4px 0;
                    }
                    .wf-row {
                      display: grid;
                      grid-template-columns: 110px 1fr 64px;
                      align-items: center;
                      gap: 10px;
                      font-size: 12px;
                    }
                    .wf-label {
                      color: var(--text-muted);
                      text-align: right;
                      font-size: 11px;
                      text-transform: uppercase;
                      letter-spacing: .04em;
                      white-space: nowrap;
                    }
                    .wf-track {
                      position: relative;
                      height: 14px;
                      background: var(--surface-3);
                      border-radius: 4px;
                      overflow: hidden;
                    }
                    .wf-bar {
                      position: absolute;
                      top: 0;
                      height: 100%;
                      border-radius: 3px;
                      min-width: 2px;
                    }
                    .wf-bar-tls-in  { background: var(--accent); opacity: .75; }
                    .wf-bar-tls-out { background: var(--ok); }
                    .wf-bar-connect { background: var(--ok); }
                    .wf-bar-wait    { background: var(--warn); }
                    .wf-bar-dl      { background: var(--route); }
                    .wf-bar-total   { background: var(--border-strong); }
                    .wf-dur {
                      font-family: var(--mono);
                      font-size: 11px;
                      color: var(--text-muted);
                      text-align: right;
                      white-space: nowrap;
                    }
                    .wf-sep { height: 1px; background: var(--border); margin: 3px 0; }
                    .wf-row-total .wf-label { color: var(--text); font-weight: 600; font-size: 12px; text-transform: none; letter-spacing: 0; }
                    .wf-row-total .wf-dur   { color: var(--text); font-weight: 600; }
                    .wf-empty { font-size: 12px; color: var(--text-muted); padding: 4px 0; }
                    .route-line,
                    .mono {
                      font-family: var(--mono);
                    }
                    .route-line {
                      font-size: 11px;
                      color: var(--text-muted);
                      white-space: nowrap;
                      overflow: hidden;
                      text-overflow: ellipsis;
                    }
                    .url-cell {
                      max-width: 280px;
                      overflow: hidden;
                      text-overflow: ellipsis;
                      white-space: nowrap;
                    }
                    .content {
                      display: grid;
                      grid-template-rows: auto auto auto 1fr auto;
                      gap: 12px;
                      padding: 12px;
                      min-height: 0;
                    }
                    .banner {
                      padding: 10px 12px;
                      border-radius: 8px;
                      border: 1px solid var(--border);
                      background: var(--surface);
                      font-size: 13px;
                    }
                    .banner.success {
                      color: var(--ok);
                      border-color: rgba(19, 121, 91, 0.2);
                      background: rgba(19, 121, 91, 0.06);
                    }
                    .banner.error {
                      color: var(--danger);
                      border-color: rgba(180, 35, 24, 0.2);
                      background: rgba(180, 35, 24, 0.06);
                    }
                    .banner.info {
                      color: var(--text-muted);
                      border-color: rgba(102, 102, 102, 0.18);
                      background: var(--surface);
                    }
                    .route-card,
                    .table-card,
                    .payload-card,
                    .events-card,
                    .editor-card {
                      background: var(--surface);
                      border: 1px solid var(--border);
                      border-radius: 12px;
                      box-shadow: var(--shadow);
                    }
                    .route-card,
                    .table-card,
                    .events-card,
                    .editor-card {
                      padding: 12px;
                    }
                    .route-title {
                      display: flex;
                      justify-content: space-between;
                      align-items: start;
                      gap: 12px;
                    }
                    .route-title strong {
                      display: block;
                      font-size: 15px;
                    }
                    .route-stats {
                      display: grid;
                      grid-template-columns: repeat(3, 1fr);
                      gap: 8px;
                      margin-top: 12px;
                    }
                    .stat-block {
                      background: var(--surface-2);
                      border: 1px solid var(--border);
                      border-radius: 10px;
                      padding: 10px 12px;
                      display: flex;
                      flex-direction: column;
                      gap: 2px;
                    }
                    .stat-block .stat-value {
                      font-size: 22px;
                      font-weight: 700;
                      line-height: 1;
                      color: var(--text);
                    }
                    .stat-block .stat-label {
                      font-size: 11px;
                      color: var(--text-muted);
                      text-transform: uppercase;
                      letter-spacing: 0.04em;
                    }
                    .stat-block.stat-warn .stat-value { color: var(--warn); }
                    .stat-block.stat-danger .stat-value { color: var(--danger); }
                    .route-meta-grid {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 6px 16px;
                      margin-top: 10px;
                      padding-top: 10px;
                      border-top: 1px solid var(--border);
                      font-size: 12px;
                    }
                    .label {
                      display: block;
                      margin-bottom: 4px;
                      color: var(--text-muted);
                      font-size: 11px;
                      text-transform: uppercase;
                      letter-spacing: 0.04em;
                    }
                    .request-toolbar {
                      display: grid;
                      grid-template-columns: 1fr 140px 140px;
                      gap: 8px;
                      margin-bottom: 10px;
                    }
                    .table-footer {
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      gap: 12px;
                      margin-top: 10px;
                    }
                    .pager {
                      display: flex;
                      gap: 8px;
                      align-items: center;
                    }
                    .pager button {
                      min-width: 92px;
                    }
                    table {
                      width: 100%;
                      border-collapse: collapse;
                      font-size: 12px;
                    }
                    th,
                    td {
                      text-align: left;
                      padding: 9px 8px;
                      border-bottom: 1px solid var(--border);
                      vertical-align: top;
                    }
                    th {
                      color: var(--text-muted);
                      font-size: 11px;
                      text-transform: uppercase;
                      letter-spacing: 0.04em;
                    }
                    tr.session-entry {
                      cursor: pointer;
                    }
                    tr.session-entry:hover {
                      background: var(--surface-2);
                    }
                    tr.session-entry.active {
                      background: var(--accent-soft);
                    }
                    .payload-grid {
                      display: grid;
                      grid-template-columns: 1fr 1fr;
                      gap: 12px;
                      min-height: 0;
                    }
                    .payload-card {
                      display: grid;
                      grid-template-rows: auto auto auto 1fr auto;
                      min-height: 360px;
                    }
                    .payload-header {
                      padding: 12px 12px 0;
                      display: flex;
                      justify-content: space-between;
                      align-items: start;
                      gap: 12px;
                    }
                    .payload-section {
                      padding: 0 12px 12px;
                    }
                    .payload-details {
                      margin: 0 12px 12px;
                      border: 1px solid var(--border);
                      border-radius: 8px;
                      background: var(--surface-2);
                    }
                    .payload-details summary {
                      list-style: none;
                      cursor: pointer;
                      padding: 10px 12px;
                      color: var(--text-muted);
                      font-size: 12px;
                      text-transform: uppercase;
                      letter-spacing: 0.04em;
                      user-select: none;
                    }
                    .payload-details summary::-webkit-details-marker {
                      display: none;
                    }
                    .payload-details summary::after {
                      content: '+';
                      float: right;
                      color: var(--text-muted);
                      font: 14px/1 var(--mono);
                    }
                    .payload-details[open] summary::after {
                      content: '-';
                    }
                    .payload-details-body {
                      padding: 0 12px 12px;
                      max-height: 220px;
                      overflow-y: auto;
                    }
                    .headers-table {
                      width: 100%;
                      table-layout: fixed;
                    }
                    .headers-table td {
                      padding: 7px 8px;
                      border-bottom: 1px solid var(--border);
                      font-size: 12px;
                      vertical-align: top;
                      word-break: break-all;
                      overflow-wrap: anywhere;
                    }
                    .headers-table td:first-child {
                      width: 32%;
                      color: var(--text-muted);
                      font-family: var(--mono);
                    }
                    .payload-body {
                      padding: 0 12px 12px;
                      min-height: 0;
                    }
                    .payload-body-head {
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      gap: 8px;
                      margin-bottom: 4px;
                    }
                    .payload-actions {
                      display: flex;
                      gap: 10px;
                      flex-wrap: nowrap;
                      padding: 0 12px 12px;
                      border-top: 1px solid var(--border);
                      margin-top: 4px;
                      padding-top: 12px;
                      align-items: stretch;
                    }
                    pre {
                      margin: 0;
                      white-space: pre-wrap;
                      overflow-wrap: anywhere;
                      font: 12px/1.5 var(--mono);
                      color: var(--text);
                      background: var(--surface-2);
                      border: 1px solid var(--border);
                      border-radius: 8px;
                      padding: 10px;
                    }
                    .payload-body pre {
                      max-height: 320px;
                      overflow: auto;
                    }
                    .loading-overlay {
                      opacity: 0.5;
                      pointer-events: none;
                      transition: opacity 200ms;
                    }
                    .scroll {
                      max-height: 100%;
                      overflow: auto;
                    }
                    .muted {
                      color: var(--text-muted);
                      font-size: 12px;
                    }
                    .intercept-panel {
                      border: 1px solid rgba(161, 92, 7, 0.25);
                      border-left: 3px solid var(--warn);
                      border-radius: 12px;
                      background: rgba(161, 92, 7, 0.04);
                      margin-bottom: 12px;
                      overflow: hidden;
                    }
                    .intercept-panel-header {
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      padding: 10px 14px;
                      background: rgba(161, 92, 7, 0.07);
                      border-bottom: 1px solid rgba(161, 92, 7, 0.15);
                    }
                    .intercept-item {
                      padding: 12px 14px;
                      border-bottom: 1px solid rgba(161, 92, 7, 0.1);
                    }
                    .intercept-item:last-child {
                      border-bottom: none;
                    }
                    .timeline {
                      display: flex;
                      flex-direction: column;
                      padding-left: 11px;
                      border-left: 2px solid var(--border);
                      max-height: 340px;
                      overflow-y: auto;
                    }
                    .tl-item {
                      display: grid;
                      grid-template-columns: 22px 1fr;
                      gap: 10px;
                      margin-left: -12px;
                      padding-bottom: 10px;
                      align-items: start;
                    }
                    .tl-dot {
                      width: 22px;
                      height: 22px;
                      border-radius: 50%;
                      display: flex;
                      align-items: center;
                      justify-content: center;
                      font-size: 9px;
                      font-weight: 800;
                      border: 2px solid;
                      background: var(--surface);
                      flex-shrink: 0;
                    }
                    .tl-dot.dot-accent { color: var(--accent); border-color: var(--accent); }
                    .tl-dot.dot-ok { color: var(--ok); border-color: var(--ok); }
                    .tl-dot.dot-warn { color: var(--warn); border-color: var(--warn); }
                    .tl-dot.dot-danger { color: var(--danger); border-color: var(--danger); }
                    .tl-dot.dot-muted { color: var(--text-muted); border-color: var(--border-strong); }
                    .tl-body {
                      padding-top: 2px;
                    }
                    .tl-label {
                      display: flex;
                      justify-content: space-between;
                      align-items: baseline;
                      gap: 8px;
                      margin-bottom: 2px;
                    }
                    .tl-label strong {
                      font-size: 12px;
                    }
                    .tl-detail {
                      font-family: var(--mono);
                      font-size: 11px;
                      color: var(--text-muted);
                      white-space: nowrap;
                      overflow: hidden;
                      text-overflow: ellipsis;
                    }
                    .actions {
                      display: flex;
                      flex-wrap: wrap;
                      gap: 8px;
                      margin-top: 10px;
                    }
                    .event-actions {
                      display: flex;
                      gap: 8px;
                    }
                    .editor-actions {
                      display: flex;
                      justify-content: flex-end;
                    }
                    .actions button,
                    .payload-actions button {
                      min-width: 0;
                    }
                    .request-toolbar button {
                      min-width: 88px;
                    }
                    .editor-grid {
                      display: grid;
                      gap: 8px;
                      margin-top: 10px;
                    }
                    .request-grid {
                      display: grid;
                      grid-template-columns: 120px 1fr 200px 130px;
                      gap: 8px;
                    }
                    .empty {
                      display: grid;
                      place-items: center;
                      min-height: 180px;
                      color: var(--text-muted);
                      border: 1px dashed var(--border);
                      border-radius: 12px;
                      background: var(--surface);
                    }
                    @media (max-width: 1180px) {
                      .layout,
                      .payload-grid {
                        grid-template-columns: 1fr;
                      }
                    }
                    @media (max-width: 760px) {
                      .request-toolbar,
                      .request-grid,
                      .route-meta-grid {
                        grid-template-columns: 1fr;
                      }
                      .payload-actions {
                        flex-wrap: wrap;
                      }
                    }
                    @media (prefers-color-scheme: dark) {
                      :root {
                        --canvas: #0d1117;
                        --surface: #161b22;
                        --surface-2: #1c2128;
                        --surface-3: #22272e;
                        --text: #e6edf3;
                        --text-muted: #8b949e;
                        --border: rgba(230, 237, 243, 0.1);
                        --border-strong: rgba(230, 237, 243, 0.18);
                        --accent: #388bfd;
                        --accent-strong: #58a6ff;
                        --accent-soft: rgba(56, 139, 253, 0.12);
                        --route: #3fb950;
                        --ok: #3fb950;
                        --warn: #d29922;
                        --danger: #f85149;
                        --button-shadow: 0 1px 2px rgba(0,0,0,0.3), 0 6px 14px rgba(0,0,0,0.15);
                        --shadow: 0 10px 26px rgba(0,0,0,0.25);
                      }
                    }
                    .modal-overlay { position:fixed; inset:0; background:rgba(0,0,0,.6); display:flex; align-items:center; justify-content:center; z-index:1000; }
                    .modal-box { background:var(--surface); border:1px solid var(--border); border-radius:8px; padding:24px; min-width:420px; max-width:560px; max-height:90vh; overflow-y:auto; }
                    .form-group { display:flex; flex-direction:column; gap:4px; margin-bottom:12px; }
                    .form-group label { font-size:11px; color:var(--text-muted); text-transform:uppercase; letter-spacing:.04em; }
                    .form-group input[type=text], .form-group input[type=number], .form-group select { background:var(--surface-2); border:1px solid var(--border); color:var(--text); padding:6px 8px; border-radius:4px; font-size:13px; width:100%; }
                    .form-section-title { font-size:11px; font-weight:600; color:var(--text-muted); text-transform:uppercase; letter-spacing:.04em; margin:16px 0 8px; }
                    .form-row { display:grid; grid-template-columns:1fr 1fr; gap:8px; }
                    .form-check { display:flex; align-items:center; gap:6px; font-size:13px; margin-bottom:8px; }
                    .btn-danger { background:#c0392b; color:#fff; border:none; padding:6px 14px; border-radius:4px; cursor:pointer; font:inherit; font-weight:600; min-height:34px; }
                    .btn-danger:hover { background:#a93226; }
                    .route-actions { display:inline-flex; gap:4px; opacity:0; transition:opacity .15s; margin-left:4px; }
                    .route-row:hover .route-actions { opacity:1; }
                    .route-action-btn { background:transparent; border:1px solid var(--border); border-radius:4px; padding:2px 6px; font-size:11px; cursor:pointer; color:var(--text-muted); min-height:0; line-height:1.4; }
                    .route-action-btn:hover { background:var(--surface-2); color:var(--text); }
                    .modal-error { color:var(--danger); font-size:12px; margin-top:8px; padding:6px 8px; border:1px solid rgba(180,35,24,.2); border-radius:4px; background:rgba(180,35,24,.06); display:none; }
                    .modal-hint { font-size:11px; color:var(--text-muted); margin-top:12px; padding:8px; border:1px solid var(--border); border-radius:4px; background:var(--surface-2); }
                  </style>
                </head>
                """;
    }

    private static String htmlBody() {
        return """
                <body>
                  <div class="app">
                    <header class="topbar">
                      <div class="topbar-title">
                        <strong>tcpmon-tls control plane</strong>
                        <span id="topbar-subtitle" class="muted">Select route, inspect recorded requests, open one to view request and response.</span>
                      </div>
                      <div id="topbar-config"></div>
                    </header>
                    <div id="config-panel-container"></div>

                    <div id="app-layout" class="layout">
                      <aside class="sidebar">
                        <div class="sidebar-section">
                          <div style="display:flex;justify-content:space-between;align-items:center;">
                            <h2>Routes</h2>
                            <button class="utility" style="min-height:24px;padding:2px 8px;font-size:13px;" onclick="openAddRouteModal()" title="Add route">+</button>
                          </div>
                        </div>
                        <div class="sidebar-section">
                          <div class="toolbar">
                            <input id="route-search" type="search" placeholder="Search route or target" oninput="renderRouteList()">
                            <button class="utility" onclick="refreshSessions(true)">Refresh</button>
                          </div>
                        </div>
                        <div id="routes" class="route-list"></div>
                      </aside>

                      <main class="content">
                        <div id="status-banner"></div>
                        <div id="route-header"></div>
                        <div id="request-table"></div>
                        <div id="payloads"></div>
                        <div id="events-and-editor"></div>
                      </main>
                    </div>
                  </div>

                  <div id="route-modal" class="modal-overlay" style="display:none;" onclick="if(event.target===this)closeRouteModal()">
                    <div class="modal-box">
                      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;">
                        <strong id="route-modal-title" style="font-size:15px;">Add Route</strong>
                        <button class="utility" onclick="closeRouteModal()" style="min-height:0;padding:2px 8px;">✕</button>
                      </div>
                      <div id="route-modal-error" class="modal-error"></div>

                      <div class="form-group">
                        <label>Route ID</label>
                        <input type="text" id="rm-id" placeholder="e.g. my-route" autocomplete="off">
                      </div>

                      <div class="form-section-title">Listener</div>
                      <div class="form-row">
                        <div class="form-group">
                          <label>Host</label>
                          <input type="text" id="rm-listener-host" value="0.0.0.0">
                        </div>
                        <div class="form-group">
                          <label>Port</label>
                          <input type="number" id="rm-listener-port" placeholder="9001" min="1" max="65535">
                        </div>
                      </div>
                      <div class="form-group">
                        <label>Transport</label>
                        <select id="rm-listener-transport">
                          <option value="PLAIN">PLAIN</option>
                          <option value="TLS">TLS</option>
                        </select>
                      </div>

                      <div class="form-section-title">Target</div>
                      <div class="form-row">
                        <div class="form-group">
                          <label>Host</label>
                          <input type="text" id="rm-target-host" placeholder="localhost">
                        </div>
                        <div class="form-group">
                          <label>Port</label>
                          <input type="number" id="rm-target-port" placeholder="8080" min="1" max="65535">
                        </div>
                      </div>
                      <div class="form-row">
                        <div class="form-group">
                          <label>Transport</label>
                          <select id="rm-target-transport">
                            <option value="PLAIN">PLAIN</option>
                            <option value="TLS">TLS</option>
                          </select>
                        </div>
                        <div class="form-group">
                          <label>SNI Host</label>
                          <input type="text" id="rm-target-sni" placeholder="optional">
                        </div>
                      </div>
                      <div class="form-check">
                        <input type="checkbox" id="rm-target-insecure">
                        <label for="rm-target-insecure">Insecure trust all</label>
                      </div>
                      <div class="form-check">
                        <input type="checkbox" id="rm-target-verify" checked>
                        <label for="rm-target-verify">Verify hostname</label>
                      </div>
                      <div class="form-check">
                        <input type="checkbox" id="rm-target-rewrite">
                        <label for="rm-target-rewrite">Rewrite Host header</label>
                      </div>

                      <p class="modal-hint">TLS material (certificates, keystores) must be configured in the config file. Only PLAIN transport is supported for routes created via UI.</p>

                      <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:16px;" id="route-modal-actions">
                        <button class="secondary" onclick="closeRouteModal()">Cancel</button>
                        <button class="primary" onclick="submitRouteForm()">Save</button>
                      </div>
                    </div>
                  </div>

                """;
    }

    private static String htmlScript() {
        return """
                  <script>
                    let allSessions = [];
                    let activeRoute = null;
                    let lastLoadedSession = null;
                    let proxyConfig = null;
                    let diffMode = false;
                    let activeSession = null;
                    let activeExchangeIndex = 0;
                    let requestPage = 1;
                    const requestPageSize = 10;
                    let requestSearchValue = '';
                    let requestMethodFilterValue = '';
                    let requestStatusCodeFilterValue = '';
                    let requestSearchDebounceTimer = null;
                    let statusMessage = null;
                    let streamMessage = { type: 'info', text: 'Connecting live updates...' };
                    let eventsExpanded = true;
                    let eventsScrollTop = 0;
                    let eventSource = null;
                    let scheduledDetailRefreshTimer = null;
                    let scheduledListRefreshTimer = null;
                    let pendingDetailRefresh = false;
                    let pendingListRefresh = false;
                    let detailRefreshInFlight = false;
                    let listRefreshInFlight = false;
                    const payloadHeadersExpanded = {
                      Request: true,
                      Response: true
                    };

                    async function fetchJson(url, options) {
                      const response = await fetch(url, options);
                      const data = await response.json();
                      if (!response.ok) {
                        const error = new Error(data.error || 'Request failed');
                        error.payload = data;
                        throw error;
                      }
                      return data;
                    }

                    async function refreshSessions(preserveSelection = true) {
                      return refreshSessionsView(preserveSelection, true);
                    }

                    async function refreshSessionsView(preserveSelection = true, refreshDetail = true) {
                      const data = await fetchJson('/api/sessions');
                      allSessions = Array.isArray(data.sessions) ? data.sessions : [];
                      renderRouteList();

                      if (!allSessions.length) {
                        if (!activeRoute) renderEmptyState('No sessions yet.');
                        return;
                      }

                      const routes = groupedRoutes();
                      if (!preserveSelection || !activeRoute || !routes.some(route => route.routeId === activeRoute)) {
                        activeRoute = routes[0].routeId;
                      }

                      const routeSessions = sessionsForActiveRoute();
                      if (!preserveSelection || !activeSession || !routeSessions.some(session => session.sessionId === activeSession)) {
                        activeSession = routeSessions[0] ? routeSessions[0].sessionId : null;
                      }

                      renderRouteList();
                      renderBanner();
                      renderRouteHeader();
                      renderRequestTable();
                      if (refreshDetail && activeSession) {
                        await loadSessionDetails(activeSession);
                      } else if (!activeSession) {
                        renderDetailEmpty('No requests for the selected route.');
                      }
                    }

                    function groupedRoutes() {
                      const map = new Map();
                      for (const session of allSessions) {
                        const routeId = session.routeId || 'default';
                        const current = map.get(routeId) || {
                          routeId,
                          sessions: [],
                          targetAddress: session.targetAddress || '',
                          clientAddress: session.clientAddress || '',
                          status: 'CLOSED'
                        };
                        current.sessions.push(session);
                        if (!current.targetAddress && session.targetAddress) current.targetAddress = session.targetAddress;
                        if (!current.clientAddress && session.clientAddress) current.clientAddress = session.clientAddress;
                        if (String(session.status || '').toUpperCase() === 'OPEN') current.status = 'OPEN';
                        if (String(session.status || '').toUpperCase() === 'ERROR') current.status = 'ERROR';
                        map.set(routeId, current);
                      }
                      return [...map.values()].sort((a, b) => a.routeId.localeCompare(b.routeId));
                    }

                    function filteredRoutes() {
                      const query = document.getElementById('route-search').value.trim().toLowerCase();
                      const sessionRoutes = groupedRoutes();
                      const sessionRouteIds = new Set(sessionRoutes.map(r => r.routeId));
                      const configRoutes = proxyConfig ? (proxyConfig.routes || []) : [];
                      const configOnly = configRoutes
                        .filter(cr => !sessionRouteIds.has(cr.id))
                        .map(cr => ({
                          routeId: cr.id,
                          sessions: [],
                          targetAddress: cr.target.host + ':' + cr.target.port,
                          clientAddress: '',
                          status: 'CLOSED'
                        }));
                      const all = [...sessionRoutes, ...configOnly].sort((a, b) => a.routeId.localeCompare(b.routeId));
                      return all.filter(route => {
                        if (!query) return true;
                        return [route.routeId, route.targetAddress, route.clientAddress].join(' ').toLowerCase().includes(query);
                      });
                    }

                    function sessionsForActiveRoute() {
                      return allSessions
                        .filter(session => (session.routeId || 'default') === activeRoute)
                        .sort((a, b) => String(b.startedAt || '').localeCompare(String(a.startedAt || '')));
                    }

                    function renderRouteList() {
                      const routes = filteredRoutes();
                      const container = document.getElementById('routes');
                      if (!routes.length) {
                        container.innerHTML = '<div class="empty">No matching routes.</div>';
                        return;
                      }
                      container.innerHTML = routes.map(route => {
                        const pending = route.sessions.reduce((sum, session) => sum + Number(session.pendingCount || 0), 0);
                        const statusClass = String(route.status || 'closed').toLowerCase();
                        const isOpen = statusClass === 'open';
                        const isError = statusClass === 'error';
                        const latest = route.sessions.slice().sort((a, b) => String(b.startedAt || '').localeCompare(String(a.startedAt || '')))[0];
                        const latestPreview = latest && (latest.requestMethod || latest.requestPath)
                          ? `<div class="route-preview"><span class="method-tag">${escapeHtml(latest.requestMethod || '')}</span>${escapeHtml(latest.requestPath || latest.sessionId.slice(0, 12) + '\u2026')}</div>`
                          : '';
                        const activeClass = route.routeId === activeRoute ? ' active' : '';
                        const statusEdge = isOpen ? ' status-open' : isError ? ' status-error' : '';
                        const withDuration = route.sessions.filter(s => s.durationMs != null);
                        const avgDuration = withDuration.length
                          ? Math.round(withDuration.reduce((sum, s) => sum + Number(s.durationMs), 0) / withDuration.length)
                          : null;
                        const errors = route.sessions.filter(s => String(s.responseStatusCode || '').startsWith('5') || String(s.status || '') === 'ERROR').length;
                        const perfParts = [];
                        if (avgDuration != null) {
                          const cls = avgDuration < 200 ? 'timing-fast' : avgDuration < 1000 ? 'timing-medium' : 'timing-slow';
                          perfParts.push(`<span class="${cls}">avg ${avgDuration < 1000 ? avgDuration + ' ms' : (avgDuration/1000).toFixed(1) + ' s'}</span>`);
                        }
                        if (errors > 0) perfParts.push(`<span style="color:var(--danger);">${errors} error${errors !== 1 ? 's' : ''}</span>`);
                        const perfLine = perfParts.length
                          ? `<div class="route-preview" style="font-family:var(--sans);">${perfParts.join(' · ')}</div>`
                          : '';
                        return `<div class="route-row${activeClass}${statusEdge}" onclick="selectRoute('${route.routeId}')">
                          <div class="row-top">
                            <strong style="font-size:13px;">${escapeHtml(route.routeId)}</strong>
                            <div style="display:flex;gap:4px;align-items:center;flex-shrink:0;">
                              ${pending > 0 ? `<span class="pill ${pending >= 3 ? 'pending-alarm' : 'pending'}">${escapeHtml(pending)}</span>` : ''}
                              <span class="pill ${escapeHtml(statusClass)}">${isOpen ? 'Live' : escapeHtml(route.status)}</span>
                              <span class="route-actions" onclick="event.stopPropagation()">
                                <button class="route-action-btn" onclick="openEditRouteModal('${escapeAttr(route.routeId)}')" title="Edit">✏</button>
                                <button class="route-action-btn" onclick="confirmDeleteRoute('${escapeAttr(route.routeId)}')" title="Delete">🗑</button>
                              </span>
                            </div>
                          </div>
                          <div class="row-bottom">
                            <span class="route-line">${escapeHtml(route.targetAddress || '')}</span>
                            <span class="pill route" style="flex-shrink:0;">${escapeHtml(route.sessions.length)} req</span>
                          </div>
                          ${latestPreview}
                          ${perfLine}
                        </div>`;
                      }).join('');
                    }

                    function selectRoute(routeId) {
                      activeRoute = routeId;
                      activeSession = null;
                      activeExchangeIndex = 0;
                      requestPage = 1;
                      requestSearchValue = '';
                      requestMethodFilterValue = '';
                      requestStatusCodeFilterValue = '';
                      const sessions = sessionsForActiveRoute();
                      activeSession = sessions[0] ? sessions[0].sessionId : null;
                      renderRouteList();
                      renderBanner();
                      renderRouteHeader();
                      updateTopbarSubtitle();
                      renderRequestTable();
                      if (activeSession) {
                        loadSessionDetails(activeSession);
                      } else {
                        renderDetailEmpty('No requests for the selected route.');
                      }
                    }

                    function renderBanner() {
                      const el = document.getElementById('status-banner');
                      const messages = [];
                      if (streamMessage) {
                        messages.push(streamMessage);
                      }
                      if (statusMessage) {
                        messages.push(statusMessage);
                      }
                      if (!messages.length) {
                        el.innerHTML = '';
                        return;
                      }
                      el.innerHTML = messages.map(message => `<div class="banner ${message.type}">${escapeHtml(message.text)}</div>`).join('');
                    }

                    function renderRouteHeader() {
                      const sessions = sessionsForActiveRoute();
                      if (!activeRoute) {
                        document.getElementById('route-header').innerHTML = '';
                        updateTopbarSubtitle();
                        return;
                      }
                      if (!sessions.length) {
                        const configRoute = proxyConfig && (proxyConfig.routes || []).find(r => r.id === activeRoute);
                        const targetAddr = configRoute ? configRoute.target.host + ':' + configRoute.target.port : '';
                        document.getElementById('route-header').innerHTML = `
                          <section class="route-card">
                            <div class="route-title">
                              <div>
                                <strong>${escapeHtml(activeRoute)}</strong>
                                ${targetAddr ? `<span class="muted" style="font-size:12px;">\u2192 ${escapeHtml(targetAddr)}</span>` : ''}
                              </div>
                              <span class="pill closed">No traffic</span>
                            </div>
                          </section>
                        `;
                        updateTopbarSubtitle();
                        return;
                      }
                      const first = sessions[0] || {};
                      const open = sessions.filter(session => String(session.status || '').toUpperCase() === 'OPEN').length;
                      const pending = sessions.reduce((sum, session) => sum + Number(session.pendingCount || 0), 0);
                      const activeSessionObj = sessions.find(session => session.sessionId === activeSession);
                      const selectedLabel = activeSessionObj
                        ? escapeHtml((activeSessionObj.requestMethod ? activeSessionObj.requestMethod + ' ' : '') + (activeSessionObj.requestPath || activeSessionObj.sessionId.slice(0, 8) + '\u2026'))
                        : 'None';
                      const pendingStatClass = pending >= 3 ? 'stat-danger' : pending > 0 ? 'stat-warn' : '';
                      document.getElementById('route-header').innerHTML = `
                        <section class="route-card">
                          <div class="route-title">
                            <div>
                              <strong>${escapeHtml(activeRoute)}</strong>
                              <span class="muted" style="font-size:12px;">${escapeHtml(first.listenerAddress || '')} \u2192 ${escapeHtml(first.targetAddress || '')}</span>
                            </div>
                            <div style="display:flex;gap:8px;align-items:center;flex-shrink:0;">
                              <button class="utility" onclick="exportHar()">Export HAR</button>
                              <span class="pill ${open > 0 ? 'open' : 'closed'}">${open > 0 ? 'Live' : 'Closed'}</span>
                            </div>
                          </div>
                          <div class="route-stats">
                            <div class="stat-block">
                              <span class="stat-value">${escapeHtml(sessions.length)}</span>
                              <span class="stat-label">Total</span>
                            </div>
                            <div class="stat-block">
                              <span class="stat-value">${escapeHtml(open)}</span>
                              <span class="stat-label">Open</span>
                            </div>
                            <div class="stat-block ${escapeHtml(pendingStatClass)}">
                              <span class="stat-value">${escapeHtml(pending)}</span>
                              <span class="stat-label">Pending</span>
                            </div>
                          </div>
                          <div class="route-meta-grid">
                            <div><span class="label">Client</span><span class="mono">${escapeHtml(first.clientAddress || 'Unknown')}</span></div>
                            <div><span class="label">Selected</span><span class="mono">${selectedLabel}</span></div>
                          </div>
                        </section>
                      `;
                      updateTopbarSubtitle();
                    }

                    function renderRequestTable() {
                      const sessions = sessionsForActiveRoute();
                      if (!sessions.length) {
                        document.getElementById('request-table').innerHTML = '';
                        return;
                      }
                      document.getElementById('request-table').innerHTML = `
                        <section class="table-card">
                          <div class="request-toolbar">
                            <input id="request-search" type="search" value="${escapeAttr(requestSearchValue)}" placeholder="Filter requests in this route" oninput="debounceRequestSearch()">
                            <select id="request-method-filter" onchange="resetRequestPageAndRender()">
                              ${renderMethodOptions(sessions)}
                            </select>
                            <select id="request-status-code-filter" onchange="resetRequestPageAndRender()">
                              ${renderStatusCodeOptions(sessions)}
                            </select>
                          </div>
                          ${renderRequestTableRows(sessions)}
                        </section>
                      `;
                    }

                    function renderRequestTableRows(sessions) {
                      const query = requestSearchValue.trim().toLowerCase();
                      const methodFilter = requestMethodFilterValue;
                      const statusCodeFilter = requestStatusCodeFilterValue;
                      const filtered = sessions.filter(session => {
                        if (methodFilter && String(session.requestMethod || '') !== methodFilter) return false;
                        if (statusCodeFilter && String(session.responseStatusCode || '') !== statusCodeFilter) return false;
                        if (!query) return true;
                        return [
                          session.sessionId,
                          session.requestMethod,
                          session.requestPath,
                          session.responseStatusCode,
                          session.clientAddress,
                          session.targetAddress,
                          session.startedAt,
                          session.status
                        ].join(' ').toLowerCase().includes(query);
                      });
                      if (!filtered.length) {
                        requestPage = 1;
                        return '<div class="empty">No requests match the current filter.</div>';
                      }
                      const totalPages = Math.max(1, Math.ceil(filtered.length / requestPageSize));
                      requestPage = Math.min(requestPage, totalPages);
                      const pageStart = (requestPage - 1) * requestPageSize;
                      const pageItems = filtered.slice(pageStart, pageStart + requestPageSize);
                      return `
                        <table>
                          <thead>
                            <tr>
                              <th>Method</th>
                              <th>Path</th>
                              <th>Response</th>
                              <th>Duration</th>
                              <th>Size</th>
                              <th>Client</th>
                              <th>Started</th>
                            </tr>
                          </thead>
                          <tbody>
                            ${pageItems.map(session => `
                              <tr class="session-entry${session.sessionId === activeSession ? ' active' : ''}" onclick="selectSession('${session.sessionId}')">
                                <td>${escapeHtml(session.requestMethod || '')}</td>
                                <td class="mono url-cell" title="${escapeAttr(session.requestPath || session.sessionId || '')}">${escapeHtml(session.requestPath || session.sessionId.slice(0, 8) + '\u2026')}</td>
                                <td>${statusBadge(session.responseStatusCode)}</td>
                                <td>${formatDuration(session.durationMs)}</td>
                                <td>${formatBytes(session.responseSizeBytes)}</td>
                                <td class="mono">${escapeHtml(session.clientAddress || '')}</td>
                                <td>${escapeHtml(formatTime(session.startedAt))}</td>
                              </tr>
                            `).join('')}
                          </tbody>
                        </table>
                        <div class="table-footer">
                          <div class="muted">Showing ${pageStart + 1}-${pageStart + pageItems.length} of ${filtered.length} requests</div>
                          <div class="pager">
                            <button class="secondary nav" ${requestPage === 1 ? 'disabled' : ''} onclick="changeRequestPage(-1)">Previous</button>
                            <span class="muted">Page ${requestPage} / ${totalPages}</span>
                            <button class="secondary nav" ${requestPage >= totalPages ? 'disabled' : ''} onclick="changeRequestPage(1)">Next</button>
                          </div>
                        </div>
                      `;
                    }

                    function renderMethodOptions(sessions) {
                      const methods = [...new Set(sessions.map(session => String(session.requestMethod || '')).filter(Boolean))].sort();
                      return `<option value="">All methods</option>` + methods.map(method => `<option value="${escapeAttr(method)}" ${method === requestMethodFilterValue ? 'selected' : ''}>${escapeHtml(method)}</option>`).join('');
                    }

                    function renderStatusCodeOptions(sessions) {
                      const statusCodes = [...new Set(sessions.map(session => String(session.responseStatusCode || '')).filter(Boolean))].sort();
                      return `<option value="">All responses</option>` + statusCodes.map(code => `<option value="${escapeAttr(code)}" ${code === requestStatusCodeFilterValue ? 'selected' : ''}>${escapeHtml(code)}</option>`).join('');
                    }

                    function resetRequestPageAndRender() {
                      requestSearchValue = document.getElementById('request-search') ? document.getElementById('request-search').value : requestSearchValue;
                      requestMethodFilterValue = document.getElementById('request-method-filter') ? document.getElementById('request-method-filter').value : requestMethodFilterValue;
                      requestStatusCodeFilterValue = document.getElementById('request-status-code-filter') ? document.getElementById('request-status-code-filter').value : requestStatusCodeFilterValue;
                      requestPage = 1;
                      renderRequestTable();
                    }

                    function debounceRequestSearch() {
                      requestSearchValue = document.getElementById('request-search') ? document.getElementById('request-search').value : requestSearchValue;
                      requestPage = 1;
                      if (requestSearchDebounceTimer) {
                        clearTimeout(requestSearchDebounceTimer);
                      }
                      requestSearchDebounceTimer = setTimeout(() => {
                        requestSearchDebounceTimer = null;
                        renderRequestTable();
                      }, 250);
                    }

                    function changeRequestPage(delta) {
                      requestPage = Math.max(1, requestPage + delta);
                      renderRequestTable();
                    }

                    async function selectSession(sessionId) {
                      activeSession = sessionId;
                      activeExchangeIndex = 0;
                      diffMode = false;
                      renderRouteHeader();
                      renderRequestTable();
                      await loadSessionDetails(sessionId);
                    }

                    async function loadSessionDetails(sessionId) {
                      const payloadsEl = document.getElementById('payloads');
                      if (payloadsEl) payloadsEl.classList.add('loading-overlay');
                      const data = await fetchJson('/api/sessions/' + sessionId);
                      lastLoadedSession = data;
                      if (payloadsEl) payloadsEl.classList.remove('loading-overlay');
                      const exchanges = data.exchanges || [];
                      if (activeExchangeIndex >= exchanges.length) activeExchangeIndex = 0;
                      const activeExchange = exchanges[activeExchangeIndex] || {};
                      renderPayloads(activeExchange, data);
                      renderEventsAndEditor(data);
                    }

                    function renderPayloads(activeExchange, data) {
                      const request = activeExchange.request || data.latestRequest;
                      const response = activeExchange.response || data.latestResponse;
                      const tlsPanel = renderTlsPanel(data);
                      document.getElementById('payloads').innerHTML = `
                        ${tlsPanel}
                        <section class="payload-grid">
                          ${renderPayloadCard('Request', request, 'CLIENT_TO_TARGET', data)}
                          ${renderPayloadCard('Response', response, 'TARGET_TO_CLIENT', data)}
                        </section>
                      `;
                    }

                    function renderTlsPanel(data) {
                      const inbound = data.inboundTls || {};
                      const outbound = data.outboundTls || {};
                      if (!inbound.protocol && !inbound.cipherSuite && !outbound.protocol && !outbound.cipherSuite) return '';
                      function tlsRows(tls) {
                        const rows = [];
                        if (tls.protocol) rows.push(`<div class="tls-row"><span class="tls-key">Protocol</span><span class="tls-val">${escapeHtml(tls.protocol)}</span></div>`);
                        if (tls.cipherSuite) rows.push(`<div class="tls-row"><span class="tls-key">Cipher</span><span class="tls-val">${escapeHtml(tls.cipherSuite)}</span></div>`);
                        if (tls.sni) rows.push(`<div class="tls-row"><span class="tls-key">SNI</span><span class="tls-val">${escapeHtml(tls.sni)}</span></div>`);
                        if (tls.peerCertCount != null) rows.push(`<div class="tls-row"><span class="tls-key">Peer certs</span><span class="tls-val">${escapeHtml(tls.peerCertCount)}</span></div>`);
                        if (tls.tlsVersion) rows.push(`<div class="tls-row"><span class="tls-key">Version</span><span class="tls-val">${escapeHtml(tls.tlsVersion)}</span></div>`);
                        return rows.join('') || '<div class="tls-row"><span class="tls-key muted">No details</span></div>';
                      }
                      return `
                        <details class="route-card" style="margin-bottom:12px;">
                          <summary style="cursor:pointer;list-style:none;display:flex;justify-content:space-between;align-items:center;padding:2px 0;">
                            <strong style="font-size:13px;">TLS</strong>
                            <span class="muted" style="font-size:11px;">${escapeHtml(inbound.protocol || '')}${inbound.protocol && outbound.protocol ? ' / ' : ''}${inbound.protocol !== outbound.protocol ? escapeHtml(outbound.protocol || '') : ''}</span>
                          </summary>
                          <div class="tls-section" style="padding-bottom:12px;">
                            <div class="tls-col">
                              <span class="label">Inbound (client → proxy)</span>
                              ${tlsRows(inbound)}
                            </div>
                            <div class="tls-col">
                              <span class="label">Outbound (proxy → target)</span>
                              ${tlsRows(outbound)}
                            </div>
                          </div>
                        </details>
                      `;
                    }

                    function calcTtfb(events) {
                      if (!Array.isArray(events)) return null;
                      const firstReq = events.find(e => e.type === 'PAYLOAD' && String(e.direction || '').includes('CLIENT'));
                      const firstRes = events.find(e => e.type === 'PAYLOAD' && String(e.direction || '').includes('TARGET'));
                      if (!firstReq || !firstRes) return null;
                      const t1 = new Date(firstReq.timestamp).getTime();
                      const t2 = new Date(firstRes.timestamp).getTime();
                      if (isNaN(t1) || isNaN(t2) || t2 < t1) return null;
                      return t2 - t1;
                    }

                    function renderPayloadCard(title, payload, expectedDirection, data) {
                      if (!payload) {
                        return `
                          <article class="payload-card">
                            <div class="payload-header">
                              <h3>${title}</h3>
                              <span class="pill route">${expectedDirection}</span>
                            </div>
                            <div class="payload-section muted">No ${title.toLowerCase()} payload captured yet.</div>
                          </article>
                        `;
                      }
                      const decoded = payload.decoded || {};
                      const headers = Array.isArray(decoded.headers) ? decoded.headers : [];
                      const bodyText = formatBody(decoded);
                      const hasBody = Boolean(bodyText);
                      const chunkText = payload.chunkCount ? ` / ${payload.chunkCount} chunks` : '';
                      const actions = title === 'Request' ? renderRequestActions(data, payload) : '';
                      const isRequest = title === 'Request';
                      let ttfbHtml = '';
                      if (title === 'Response' && data && Array.isArray(data.events)) {
                        const ttfb = calcTtfb(data.events);
                        if (ttfb !== null) {
                          const ttfbCls = ttfb < 200 ? 'timing-fast' : ttfb < 1000 ? 'timing-medium' : 'timing-slow';
                          ttfbHtml = `<span class="muted" style="font-size:11px;">TTFB: <span class="${ttfbCls}">${ttfb} ms</span></span>`;
                        }
                      }
                      return `
                        <article class="payload-card">
                          <div class="payload-header">
                            <div>
                              <h3>${title}</h3>
                              <div class="muted">${escapeHtml(payload.timestamp || '')} / ${escapeHtml(payload.size || 0)} bytes${escapeHtml(chunkText)}</div>
                              ${ttfbHtml}
                            </div>
                            <span class="pill route">${escapeHtml(payload.direction || expectedDirection)}</span>
                          </div>
                          <div class="payload-section">
                            <span class="label">Start line</span>
                            <pre>${escapeHtml(decoded.startLine || 'No HTTP start line')}</pre>
                          </div>
                          <details class="payload-details" ${payloadHeadersExpanded[title] ? 'open' : ''} ontoggle="setPayloadHeadersExpanded('${title}', this.open)">
                            <summary>Headers</summary>
                            <div class="payload-details-body">
                              ${renderHeadersTable(headers, decoded, isRequest)}
                            </div>
                          </details>
                          <div class="payload-body">
                            <div class="payload-body-head">
                              <span class="label">Body</span>
                              ${hasBody ? `<button class="utility" onclick="copyCurrentBody(${isRequest})">Copy body</button>` : ''}
                            </div>
                            <pre class="scroll">${escapeHtml(bodyText || 'No body captured')}</pre>
                          </div>
                          ${actions}
                        </article>
                      `;
                    }

                    function renderRequestActions(data, payload) {
                      if (!payload?.base64 || !data?.routeId) {
                        return '';
                      }
                      return `
                        <div class="payload-actions">
                          <button class="primary action-main" onclick='replayPayload(${JSON.stringify(data.routeId)}, ${JSON.stringify(payload.base64)}, "listener")'>Recapture request</button>
                          <button class="secondary action-alt" onclick='replayPayload(${JSON.stringify(data.routeId)}, ${JSON.stringify(payload.base64)}, "target")'>Send direct</button>
                          <button class="utility" onclick="copyCurlFromSession()">Copy as cURL</button>
                        </div>
                      `;
                    }

                    async function exportHar() {
                      const sessions = sessionsForActiveRoute();
                      if (!sessions.length) { setStatus('error', 'No sessions to export'); return; }
                      setStatus('info', 'Building HAR export...');
                      const entries = [];
                      for (const session of sessions) {
                        try {
                          const data = await fetchJson('/api/sessions/' + session.sessionId);
                          const exchanges = data.exchanges || [];
                          for (const exchange of exchanges) {
                            const req = exchange.request;
                            const res = exchange.response;
                            if (!req) continue;
                            const reqDecoded = req.decoded || {};
                            const resDecoded = res ? (res.decoded || {}) : {};
                            const reqHeaders = Array.isArray(reqDecoded.headers) ? reqDecoded.headers : [];
                            const resHeaders = Array.isArray(resDecoded.headers) ? resDecoded.headers : [];
                            const reqMeta = reqDecoded.request || {};
                            const resStart = resDecoded.startLine || '';
                            const statusCode = parseInt((resStart.split(' ')[1] || '0'), 10) || 0;
                            const startedAt = new Date(session.startedAt || Date.now()).toISOString();
                            const ttfb = calcTtfb(data.events || []);
                            const totalMs = session.durationMs != null ? Number(session.durationMs) : 0;
                            const host = (data.targetAddress || '').replace(/:\\d+$/, '');
                            const port = (data.targetAddress || '').includes(':') ? parseInt(data.targetAddress.split(':')[1], 10) : 443;
                            const url = 'https://' + (data.targetAddress || 'unknown') + (reqMeta.path || '/') + (reqMeta.query ? '?' + reqMeta.query : '');
                            const entry = {
                              startedDateTime: startedAt,
                              time: totalMs,
                              request: {
                                method: reqMeta.method || 'GET',
                                url,
                                httpVersion: reqMeta.version || 'HTTP/1.1',
                                headers: reqHeaders.map(h => ({ name: h.name || '', value: h.value || '' })),
                                queryString: [],
                                cookies: [],
                                headersSize: -1,
                                bodySize: req.size || 0,
                                postData: reqDecoded.bodyText ? { mimeType: '', text: reqDecoded.bodyText } : undefined
                              },
                              response: res ? {
                                status: statusCode,
                                statusText: resStart.split(' ').slice(2).join(' ') || '',
                                httpVersion: (resStart.split(' ')[0]) || 'HTTP/1.1',
                                headers: resHeaders.map(h => ({ name: h.name || '', value: h.value || '' })),
                                cookies: [],
                                content: {
                                  size: res.size || 0,
                                  mimeType: (resHeaders.find(h => String(h.name||'').toLowerCase() === 'content-type') || {}).value || '',
                                  text: resDecoded.bodyText || ''
                                },
                                redirectURL: '',
                                headersSize: -1,
                                bodySize: res.size || 0
                              } : { status: 0, statusText: '', httpVersion: 'HTTP/1.1', headers: [], cookies: [], content: { size: 0, mimeType: '', text: '' }, redirectURL: '', headersSize: -1, bodySize: -1 },
                              cache: {},
                              timings: { send: 0, wait: ttfb != null ? ttfb : totalMs, receive: 0 }
                            };
                            entries.push(entry);
                          }
                        } catch (e) { /* skip failed sessions */ }
                      }
                      const har = {
                        log: {
                          version: '1.2',
                          creator: { name: 'tcpmon-tls', version: '1.0' },
                          entries
                        }
                      };
                      const blob = new Blob([JSON.stringify(har, null, 2)], { type: 'application/json' });
                      const link = document.createElement('a');
                      const dateStr = new Date().toISOString().slice(0, 10);
                      link.href = URL.createObjectURL(blob);
                      link.download = `tcpmon-${activeRoute || 'export'}-${dateStr}.har`;
                      link.click();
                      URL.revokeObjectURL(link.href);
                      setStatus('success', `HAR exported: ${entries.length} request${entries.length !== 1 ? 's' : ''}`);
                    }

                    function resolvePayload(isRequest) {
                      const data = lastLoadedSession;
                      if (!data) return null;
                      const exchange = (data.exchanges || [])[activeExchangeIndex] || {};
                      return isRequest
                        ? (exchange.request  || data.latestRequest)
                        : (exchange.response || data.latestResponse);
                    }

                    function copyCurrentBody(isRequest) {
                      const payload = resolvePayload(isRequest);
                      if (!payload) return;
                      const bodyText = formatBody(payload.decoded || {});
                      if (!bodyText) return;
                      copyText(bodyText);
                    }

                    function copyCurrentHeaders(isRequest) {
                      const payload = resolvePayload(isRequest);
                      if (!payload) return;
                      const headers = Array.isArray(payload.decoded?.headers) ? payload.decoded.headers : [];
                      if (!headers.length) return;
                      const text = headers.map(h => (h.name || '') + ': ' + (h.value || '')).join('\\n');
                      copyText(text);
                    }

                    function copyCurlFromSession() {
                      const payload = resolvePayload(true);
                      if (!payload) return;
                      const curl = generateCurl((lastLoadedSession || {}).targetAddress || '', payload.decoded || {});
                      copyText(curl);
                    }

                    function generateCurl(targetAddress, decoded) {
                      if (!decoded.isHttp) return '';
                      const req = decoded.request || {};
                      const headers = Array.isArray(decoded.headers) ? decoded.headers : [];
                      const method = req.method || 'GET';
                      const path = req.path || '/';
                      const query = req.query ? '?' + req.query : '';
                      let host = targetAddress || '';
                      if (host && !host.startsWith('http')) {
                        host = 'https://' + host;
                      }
                      const url = host + path + query;
                      const parts = [`curl -X ${method} '${url}'`];
                      for (const h of headers) {
                        const name = String(h.name || '').toLowerCase();
                        if (name === 'content-length' || name === 'transfer-encoding') continue;
                        parts.push(`  -H '${h.name}: ${String(h.value || '').replaceAll("'", "\\'")}'`);
                      }
                      const body = decoded.bodyText || '';
                      if (body) {
                        parts.push(`  -d '${body.replaceAll("'", "\\'")}'`);
                      }
                      return parts.join(' \\\\\\n');
                    }

                    function renderHeadersTable(headers, decoded, isRequest) {
                      if (!decoded.isHttp) {
                        return `<pre>Non-HTTP payload</pre>`;
                      }
                      if (!headers.length) {
                        return `<pre>No headers</pre>`;
                      }
                      return `
                        <div style="display:flex;justify-content:flex-end;margin-bottom:6px;">
                          <button class="utility" onclick="copyCurrentHeaders(${isRequest})">Copy headers</button>
                        </div>
                        <table class="headers-table">
                          <tbody>
                            ${headers.map(header => `
                              <tr>
                                <td>${escapeHtml(header.name || '')}</td>
                                <td>${escapeHtml(header.value || '')}</td>
                              </tr>
                            `).join('')}
                          </tbody>
                        </table>
                      `;
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
                      const tokens = compact.replace(/></g, '>\\n<').split('\\n');
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
                      return lines.join('\\n');
                    }

                    function renderWaterfall(data) {
                      const events = Array.isArray(data.events) ? data.events : [];
                      function firstTs(type) {
                        const ev = events.find(e => e.type === type);
                        return ev ? new Date(ev.timestamp).getTime() : null;
                      }
                      function firstPayloadTs(dirFragment) {
                        const ev = events.find(e => e.type === 'PAYLOAD' && String(e.direction || '').includes(dirFragment));
                        return ev ? new Date(ev.timestamp).getTime() : null;
                      }
                      function lastPayloadTs(dirFragment) {
                        const evs = events.filter(e => e.type === 'PAYLOAD' && String(e.direction || '').includes(dirFragment));
                        return evs.length ? new Date(evs[evs.length - 1].timestamp).getTime() : null;
                      }
                      function fmtMs(ta, tb) {
                        const d = tb - ta;
                        if (d < 1) return '< 1 ms';
                        return d < 1000 ? d + ' ms' : (d / 1000).toFixed(2) + ' s';
                      }
                      const t0 = data.startedAt ? new Date(data.startedAt).getTime() : firstTs('CLIENT_CONNECTED');
                      const tClientConn = firstTs('CLIENT_CONNECTED');
                      const tTlsIn      = firstTs('TLS_INBOUND');
                      const tTlsOut     = firstTs('TLS_OUTBOUND');
                      const tTargetConn = firstTs('TARGET_CONNECTED');
                      const tFirstReq   = firstPayloadTs('CLIENT');
                      const tFirstRes   = firstPayloadTs('TARGET');
                      const tLastRes    = lastPayloadTs('TARGET');
                      const tEnd = data.endedAt ? new Date(data.endedAt).getTime()
                                 : tLastRes || firstTs('CLIENT_CLOSED');
                      if (!t0 || !tEnd || tEnd <= t0) {
                        return '<div class="wf-empty">Timing data not available for this session.</div>';
                      }
                      const total = tEnd - t0;
                      function pct(t)       { return ((t - t0) / total * 100).toFixed(2); }
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
                      const connEnd   = tTlsOut || tTargetConn;
                      if (connEnd && connEnd > connStart) {
                        const label = tTlsOut ? 'TLS Outbound' : 'Connect';
                        const cls   = tTlsOut ? 'wf-bar-tls-out' : 'wf-bar-connect';
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
                      const exchanges = data.exchanges || [];
                      const events = data.events || [];
                      const pendingEvents = events.filter(event => event.pendingId);
                      const pendingBadge = pendingEvents.length
                        ? ` <span style="color:var(--warn);font-weight:700;">\u00b7 ${escapeHtml(pendingEvents.length)} intercepted</span>`
                        : '';
                      const startMs = data.startedAt ? new Date(data.startedAt).getTime() : null;
                      const endMs   = data.endedAt   ? new Date(data.endedAt).getTime()   : null;
                      const durLabel = (startMs && endMs && endMs > startMs)
                        ? ' \u00b7 ' + (endMs - startMs < 1000 ? (endMs - startMs) + ' ms' : ((endMs - startMs) / 1000).toFixed(2) + ' s')
                        : '';
                      document.getElementById('events-and-editor').innerHTML = `
                        <details class="events-card" ${eventsExpanded ? 'open' : ''} ontoggle="setEventsExpanded(this.open)">
                          <summary style="display:flex;justify-content:space-between;align-items:center;gap:12px;cursor:pointer;">
                            <span><strong>Timing</strong></span>
                            <span class="muted">${escapeHtml(exchanges.length)} exchange${exchanges.length !== 1 ? 's' : ''}${escapeHtml(durLabel)}${pendingBadge}</span>
                          </summary>
                          <div style="margin-top:12px;">
                            ${renderExchangeButtons(exchanges)}
                            ${pendingEvents.length ? renderInterceptPanel(pendingEvents) : ''}
                            ${renderWaterfall(data)}
                          </div>
                        </details>
                        <div id="editor"></div>
                      `;
                    }

                    function renderInterceptPanel(pendingEvents) {
                      const count = pendingEvents.length;
                      const items = pendingEvents.map(event => {
                        const decoded = event.decoded || {};
                        const isOut = String(event.direction || '').includes('CLIENT');
                        const preview = decoded.startLine || (isOut ? '\u2192 Outbound payload' : '\u2190 Inbound payload');
                        const sizeLabel = event.size ? escapeHtml(event.size) + ' B' : '';
                        return `
                          <div class="intercept-item">
                            <div style="display:flex;justify-content:space-between;align-items:center;gap:8px;margin-bottom:10px;">
                              <span class="mono" style="font-size:12px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(preview)}</span>
                              ${sizeLabel ? `<span class="muted" style="flex-shrink:0;">${sizeLabel}</span>` : ''}
                            </div>
                            <div style="display:flex;gap:8px;">
                              <button class="secondary" onclick="releasePending('${event.pendingId}')">Forward original</button>
                              <button class="primary" onclick='showEdit("${event.pendingId}", ${JSON.stringify(event.decoded || null)}, "${event.details?.base64 || ''}")'>Edit and forward</button>
                            </div>
                          </div>
                        `;
                      }).join('');
                      return `
                        <div class="intercept-panel">
                          <div class="intercept-panel-header">
                            <strong style="color:var(--warn);">\u26a0 ${escapeHtml(count)} payload${count !== 1 ? 's' : ''} intercepted</strong>
                            <span class="muted">Waiting for your action</span>
                          </div>
                          ${items}
                        </div>
                      `;
                    }

                    function renderTimelineItem(event) {
                      const cfg = timelineConfig(event);
                      const time = formatTime(event.timestamp);
                      const detail = cfg.detail
                        ? `<div class="tl-detail">${escapeHtml(cfg.detail)}</div>`
                        : '';
                      const pendingActions = event.pendingId ? `
                        <div style="display:flex;gap:8px;margin-top:8px;">
                          <button class="secondary" onclick="releasePending('${event.pendingId}')">Forward original</button>
                          <button class="primary" onclick='showEdit("${event.pendingId}", ${JSON.stringify(event.decoded || null)}, "${event.details?.base64 || ''}")'>Edit and forward</button>
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
                          const isOut = dir.includes('CLIENT');
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

                    function renderExchangeButtons(exchanges) {
                      if (exchanges.length <= 1) {
                        diffMode = false;
                        return '';
                      }
                      const compareBtn = exchanges.length >= 2
                        ? `<button class="${diffMode ? 'primary' : 'secondary'}" onclick="toggleDiffMode()">Compare</button>`
                        : '';
                      return `
                        <div class="actions" style="margin:0 0 10px;">
                          ${exchanges.map(exchange => `
                            <button class="${!diffMode && exchange.index === activeExchangeIndex ? 'primary' : 'secondary'}" onclick="selectExchange(${exchange.index})">${escapeHtml(exchange.index + 1)}</button>
                          `).join('')}
                          ${compareBtn}
                        </div>
                        ${diffMode ? renderExchangeDiff(exchanges) : ''}
                      `;
                    }

                    function toggleDiffMode() {
                      diffMode = !diffMode;
                      if (lastLoadedSession) {
                        renderEventsAndEditor(lastLoadedSession);
                        renderPayloads(
                          (lastLoadedSession.exchanges || [])[activeExchangeIndex] || {},
                          lastLoadedSession
                        );
                      }
                    }

                    function renderExchangeDiff(exchanges) {
                      if (exchanges.length < 2) return '';
                      const rows = [];
                      const ex0 = exchanges[0];
                      const ex1 = exchanges[1];
                      // Status codes
                      const status0 = extractExchangeStatus(ex0);
                      const status1 = extractExchangeStatus(ex1);
                      rows.push(diffRow('Status', status0, status1));
                      // Request headers that differ
                      const reqH0 = extractHeaders(ex0.request);
                      const reqH1 = extractHeaders(ex1.request);
                      const allReqKeys = [...new Set([...Object.keys(reqH0), ...Object.keys(reqH1)])].sort();
                      for (const key of allReqKeys) {
                        const v0 = reqH0[key] ?? '';
                        const v1 = reqH1[key] ?? '';
                        if (v0 !== v1) rows.push(diffRow('Req: ' + key, v0, v1));
                      }
                      // Response headers that differ
                      const resH0 = extractHeaders(ex0.response);
                      const resH1 = extractHeaders(ex1.response);
                      const allResKeys = [...new Set([...Object.keys(resH0), ...Object.keys(resH1)])].sort();
                      for (const key of allResKeys) {
                        const v0 = resH0[key] ?? '';
                        const v1 = resH1[key] ?? '';
                        if (v0 !== v1) rows.push(diffRow('Res: ' + key, v0, v1));
                      }
                      if (!rows.length) {
                        return `<div class="muted" style="font-size:12px;margin-bottom:10px;">No differences between Exchange 1 and Exchange 2.</div>`;
                      }
                      return `
                        <div style="margin-bottom:12px;border:1px solid var(--border);border-radius:10px;overflow:hidden;">
                          <table style="width:100%;font-size:12px;border-collapse:collapse;">
                            <thead>
                              <tr style="background:var(--surface-2);">
                                <th style="padding:8px 10px;text-align:left;color:var(--text-muted);font-size:11px;text-transform:uppercase;letter-spacing:.04em;width:28%;">Field</th>
                                <th style="padding:8px 10px;text-align:left;color:var(--text-muted);font-size:11px;text-transform:uppercase;letter-spacing:.04em;">Exchange 1</th>
                                <th style="padding:8px 10px;text-align:left;color:var(--text-muted);font-size:11px;text-transform:uppercase;letter-spacing:.04em;">Exchange 2</th>
                              </tr>
                            </thead>
                            <tbody>${rows.join('')}</tbody>
                          </table>
                        </div>
                      `;
                    }

                    function diffRow(label, v0, v1) {
                      const changed = v0 !== v1;
                      const style = changed ? 'background:rgba(161,92,7,0.05);' : '';
                      const cell = (v, other) => {
                        if (!v && other) return `<span style="color:var(--text-muted);">—</span>`;
                        if (v && !other) return `<span style="color:var(--ok);">${escapeHtml(v)}</span>`;
                        if (changed) return `<span style="font-family:var(--mono);word-break:break-all;">${escapeHtml(v)}</span>`;
                        return `<span style="font-family:var(--mono);color:var(--text-muted);word-break:break-all;">${escapeHtml(v)}</span>`;
                      };
                      return `<tr style="${style}border-bottom:1px solid var(--border);">
                        <td style="padding:7px 10px;color:var(--text-muted);">${escapeHtml(label)}</td>
                        <td style="padding:7px 10px;">${cell(v0, v1)}</td>
                        <td style="padding:7px 10px;">${cell(v1, v0)}</td>
                      </tr>`;
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


                    function showEdit(pendingId, decodedPayload, base64Value) {
                      const editor = document.getElementById('editor');
                      if (decodedPayload?.isHttp) {
                        const request = decodedPayload.request || {};
                        editor.innerHTML = `
                          <section class="editor-card">
                            <h3>Edit pending HTTP payload</h3>
                            <div class="editor-grid">
                              <div class="request-grid">
                                <input id="http-method" value="${escapeAttr(request.method || '')}" placeholder="Method">
                                <input id="http-path" value="${escapeAttr(request.path || '')}" placeholder="Path">
                                <input id="http-query" value="${escapeAttr(request.query || '')}" placeholder="Query">
                                <input id="http-version" value="${escapeAttr(request.version || 'HTTP/1.1')}" placeholder="Version">
                              </div>
                              <textarea id="http-headers" rows="8" placeholder="Headers">${escapeHtml(decodedPayload.headersText || '')}</textarea>
                              <textarea id="http-body" rows="10" placeholder="Body">${escapeHtml(decodedPayload.bodyText || '')}</textarea>
                              <div class="actions editor-actions">
                                <button class="primary action-edit" onclick="submitStructuredHttp('${pendingId}')">Forward edited HTTP</button>
                              </div>
                            </div>
                          </section>
                        `;
                        return;
                      }
                      editor.innerHTML = `
                        <section class="editor-card">
                          <h3>Edit pending payload</h3>
                          <div class="editor-grid">
                            <textarea id="payload-editor" rows="10">${escapeHtml(atob(base64Value || ''))}</textarea>
                            <div class="actions editor-actions">
                              <button class="primary action-edit" onclick="submitEdited('${pendingId}')">Forward edited</button>
                            </div>
                          </div>
                        </section>
                      `;
                    }

                    async function releasePending(pendingId) {
                      try {
                        await fetchJson('/api/pending/' + pendingId + '/forward', {
                          method: 'POST',
                          headers: { 'Content-Type': 'application/json' },
                          body: '{}'
                        });
                        setStatus('success', `Pending payload ${pendingId} forwarded`);
                      } catch (error) {
                        setStatus('error', error.message);
                      }
                    }

                    async function submitEdited(pendingId) {
                      const content = document.getElementById('payload-editor').value;
                      try {
                        await fetchJson('/api/pending/' + pendingId + '/forward', {
                          method: 'POST',
                          headers: { 'Content-Type': 'application/json' },
                          body: JSON.stringify({ encoding: 'utf8', content })
                        });
                        setStatus('success', `Pending payload ${pendingId} forwarded with edits`);
                      } catch (error) {
                        setStatus('error', error.message);
                      }
                    }

                    async function submitStructuredHttp(pendingId) {
                      try {
                        await fetchJson('/api/pending/' + pendingId + '/forward', {
                          method: 'POST',
                          headers: { 'Content-Type': 'application/json' },
                          body: JSON.stringify({
                            http: {
                              method: document.getElementById('http-method').value,
                              path: document.getElementById('http-path').value,
                              query: document.getElementById('http-query').value,
                              version: document.getElementById('http-version').value,
                              headersText: document.getElementById('http-headers').value,
                              bodyText: document.getElementById('http-body').value
                            }
                          })
                        });
                        setStatus('success', `Pending HTTP payload ${pendingId} forwarded with structured edits`);
                      } catch (error) {
                        setStatus('error', error.message);
                      }
                    }

                    async function replayEvent(sessionId, eventId, destination) {
                      try {
                        const result = await fetchJson('/api/replay', {
                          method: 'POST',
                          headers: { 'Content-Type': 'application/json' },
                          body: JSON.stringify({ sessionId, eventId, destination })
                        });
                        setStatus('success', `Replay ${destination} completed: sent ${result.bytesSent} bytes, received ${result.bytesReceived ?? 0} bytes from ${result.target}`);
                      } catch (error) {
                        setStatus('error', error.message);
                      }
                    }

                    async function replayPayload(routeId, base64, destination) {
                      try {
                        const result = await fetchJson('/api/replay', {
                          method: 'POST',
                          headers: { 'Content-Type': 'application/json' },
                          body: JSON.stringify({ routeId, base64, destination })
                        });
                        setStatus('success', `Replay ${destination} completed: sent ${result.bytesSent} bytes, received ${result.bytesReceived ?? 0} bytes from ${result.target}`);
                      } catch (error) {
                        setStatus('error', error.message);
                      }
                    }

                    async function selectExchange(index) {
                      activeExchangeIndex = index;
                      diffMode = false;
                      if (activeSession) {
                        await loadSessionDetails(activeSession);
                      }
                    }

                    function setPayloadHeadersExpanded(title, open) {
                      payloadHeadersExpanded[title] = open;
                    }

                    function setEventsExpanded(open) {
                      eventsExpanded = open;
                    }

                    function setEventsScroll(value) {
                      eventsScrollTop = value;
                    }

                    function restoreEventsScroll() {
                      if (!eventsExpanded) {
                        return;
                      }
                      requestAnimationFrame(() => {
                        const list = document.getElementById('events-list');
                        if (list) {
                          list.scrollTop = eventsScrollTop;
                        }
                      });
                    }

                    function setStatus(type, text) {
                      statusMessage = { type, text };
                      renderBanner();
                      if (activeSession) {
                        loadSessionDetails(activeSession);
                      }
                    }

                    function connectEventStream() {
                      if (eventSource) {
                        eventSource.close();
                      }
                      eventSource = new EventSource('/api/events');
                      eventSource.addEventListener('open', () => {
                        streamMessage = null;
                        renderBanner();
                      });
                      eventSource.addEventListener('error', () => {
                        streamMessage = { type: 'info', text: 'Live updates disconnected. Trying to reconnect...' };
                        renderBanner();
                      });
                      eventSource.addEventListener('session-created', handleSessionChange);
                      eventSource.addEventListener('session-updated', handleSessionChange);
                      eventSource.addEventListener('session-closed', handleSessionChange);
                      eventSource.addEventListener('pending-released', handleSessionChange);
                    }

                    async function handleSessionChange(event) {
                      let payload;
                      try {
                        payload = JSON.parse(event.data || '{}');
                      } catch (error) {
                        return;
                      }
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
                      if (affectsActiveSession) {
                        scheduleDetailRefresh();
                        return;
                      }
                      if (affectsActiveRoute) {
                        scheduleListRefresh();
                      }
                    }

                    function scheduleDetailRefresh() {
                      pendingDetailRefresh = true;
                      if (scheduledDetailRefreshTimer) {
                        return;
                      }
                      scheduledDetailRefreshTimer = setTimeout(async () => {
                        scheduledDetailRefreshTimer = null;
                        if (detailRefreshInFlight) {
                          scheduleDetailRefresh();
                          return;
                        }
                        if (!pendingDetailRefresh || !activeSession) {
                          pendingDetailRefresh = false;
                          return;
                        }
                        pendingDetailRefresh = false;
                        detailRefreshInFlight = true;
                        try {
                          await loadSessionDetails(activeSession);
                        } catch (error) {
                          streamMessage = { type: 'info', text: 'Live update received, but refresh failed. Use Refresh to resync.' };
                          renderBanner();
                        } finally {
                          detailRefreshInFlight = false;
                        }
                        if (!streamMessage) {
                          renderBanner();
                        }
                        if (pendingDetailRefresh) {
                          scheduleDetailRefresh();
                        }
                      }, 150);
                    }

                    function scheduleListRefresh() {
                      pendingListRefresh = true;
                      if (scheduledListRefreshTimer) {
                        return;
                      }
                      scheduledListRefreshTimer = setTimeout(async () => {
                        scheduledListRefreshTimer = null;
                        if (listRefreshInFlight) {
                          scheduleListRefresh();
                          return;
                        }
                        if (!pendingListRefresh) {
                          return;
                        }
                        pendingListRefresh = false;
                        listRefreshInFlight = true;
                        try {
                          await refreshSessionsView(true, false);
                        } catch (error) {
                          streamMessage = { type: 'info', text: 'Live update received, but refresh failed. Use Refresh to resync.' };
                          renderBanner();
                        } finally {
                          listRefreshInFlight = false;
                        }
                        if (!streamMessage) {
                          renderBanner();
                        }
                        if (pendingListRefresh) {
                          scheduleListRefresh();
                        }
                      }, 800);
                    }

                    function renderDetailEmpty(message) {
                      document.getElementById('payloads').innerHTML = `<div class="empty">${escapeHtml(message)}</div>`;
                      document.getElementById('events-and-editor').innerHTML = '';
                    }

                    function renderEmptyState(message) {
                      document.getElementById('status-banner').innerHTML = '';
                      document.getElementById('route-header').innerHTML = `<div class="empty" style="flex-direction:column;gap:6px;">${escapeHtml(message)}<span class="muted" style="font-size:11px;text-align:center;">Proxy traffic through the configured listener to begin capturing.</span></div>`;
                      document.getElementById('request-table').innerHTML = '';
                      document.getElementById('payloads').innerHTML = '';
                      document.getElementById('events-and-editor').innerHTML = '';
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

                    async function copyText(text) {
                      if (!text) return;
                      try {
                        if (navigator.clipboard?.writeText) {
                          await navigator.clipboard.writeText(text);
                        } else {
                          const helper = document.createElement('textarea');
                          helper.value = text;
                          helper.setAttribute('readonly', 'true');
                          helper.style.position = 'absolute';
                          helper.style.left = '-9999px';
                          document.body.appendChild(helper);
                          helper.select();
                          document.execCommand('copy');
                          document.body.removeChild(helper);
                        }
                        setStatus('success', 'Copied to clipboard');
                      } catch (error) {
                        setStatus('error', 'Unable to copy');
                      }
                    }

                    function updateTopbarSubtitle() {
                      const el = document.getElementById('topbar-subtitle');
                      if (!el) return;
                      if (!activeRoute) {
                        el.textContent = 'Select route, inspect recorded requests, open one to view request and response.';
                        return;
                      }
                      const sessions = sessionsForActiveRoute();
                      const pending = sessions.reduce((sum, s) => sum + Number(s.pendingCount || 0), 0);
                      if (pending > 0) {
                        el.textContent = activeRoute + ' \u2014 ' + pending + ' pending';
                      } else if (activeSession) {
                        el.textContent = activeRoute + ' \u2014 ' + sessions.length + ' request' + (sessions.length !== 1 ? 's' : '');
                      } else {
                        el.textContent = activeRoute + ' \u2014 ' + sessions.length + ' request' + (sessions.length !== 1 ? 's' : '');
                      }
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

                    async function loadConfig() {
                      try {
                        proxyConfig = await fetchJson('/api/config');
                        renderConfigButton();
                        renderRouteList();
                        if (!activeRoute) {
                          const routes = filteredRoutes();
                          if (routes.length) selectRoute(routes[0].routeId);
                        }
                      } catch (e) { /* config panel unavailable */ }
                    }

                    function renderConfigButton() {
                      const el = document.getElementById('topbar-config');
                      if (!el) return;
                      el.innerHTML = `<button class="utility" onclick="toggleConfigPanel()">Config</button>`;
                    }

                    let configPanelOpen = false;
                    function toggleConfigPanel() {
                      configPanelOpen = !configPanelOpen;
                      renderConfigPanel();
                    }

                    function renderConfigPanel() {
                      const el = document.getElementById('config-panel-container');
                      if (!el) return;
                      if (!configPanelOpen || !proxyConfig) { el.innerHTML = ''; return; }
                      const routes = proxyConfig.routes || [];
                      const routeRows = routes.map(route => `
                        <div style="margin-bottom:10px;">
                          <div class="config-row"><span class="config-key">Route</span><span class="config-val">${escapeHtml(route.id || '')}</span></div>
                          <div class="config-row"><span class="config-key">Listener</span><span class="config-val">${escapeHtml(route.listener.host + ':' + route.listener.port)} (${escapeHtml(route.listener.transport)})</span></div>
                          <div class="config-row"><span class="config-key">Target</span><span class="config-val">${escapeHtml(route.target.host + ':' + route.target.port)} (${escapeHtml(route.target.transport)})</span></div>
                          <div class="config-row"><span class="config-key">Client Auth</span><span class="config-val">${escapeHtml(route.listener.clientAuth || '')}</span></div>
                          <div class="config-row"><span class="config-key">Trust All</span><span class="config-val">${escapeHtml(String(route.target.insecureTrustAll || false))}</span></div>
                        </div>
                      `).join('');
                      el.innerHTML = `
                        <div style="background:var(--surface);border-bottom:1px solid var(--border);padding:12px 16px;">
                          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;">
                            <strong style="font-size:13px;">Proxy Configuration</strong>
                            <button class="utility" onclick="toggleConfigPanel()">Close</button>
                          </div>
                          <div class="config-row"><span class="config-key">Intercept Mode</span><span class="config-val">${escapeHtml(proxyConfig.interceptMode || '')}</span></div>
                          <div class="config-panel">${routeRows}</div>
                        </div>
                      `;
                    }

                    refreshSessions(false);
                    connectEventStream();
                    loadConfig();
                  </script>
                """;
    }

    private static String htmlRouteModalScript() {
        return """
                  <script>
                    let routeModalMode = 'add';
                    let routeModalEditId = null;

                    function openAddRouteModal() {
                      routeModalMode = 'add';
                      routeModalEditId = null;
                      document.getElementById('route-modal-title').textContent = 'Add Route';
                      document.getElementById('rm-id').value = '';
                      document.getElementById('rm-id').disabled = false;
                      document.getElementById('rm-listener-host').value = '0.0.0.0';
                      document.getElementById('rm-listener-port').value = '';
                      document.getElementById('rm-listener-transport').value = 'PLAIN';
                      document.getElementById('rm-target-host').value = '';
                      document.getElementById('rm-target-port').value = '';
                      document.getElementById('rm-target-transport').value = 'PLAIN';
                      document.getElementById('rm-target-sni').value = '';
                      document.getElementById('rm-target-insecure').checked = false;
                      document.getElementById('rm-target-verify').checked = true;
                      document.getElementById('rm-target-rewrite').checked = false;
                      document.getElementById('route-modal-error').style.display = 'none';
                      document.getElementById('route-modal').style.display = 'flex';
                    }

                    function openEditRouteModal(routeId) {
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
                      document.getElementById('rm-listener-transport').value = route.listener.transport || 'PLAIN';
                      document.getElementById('rm-target-host').value = route.target.host || '';
                      document.getElementById('rm-target-port').value = route.target.port || '';
                      document.getElementById('rm-target-transport').value = route.target.transport || 'PLAIN';
                      document.getElementById('rm-target-sni').value = route.target.sniHost || '';
                      document.getElementById('rm-target-insecure').checked = !!route.target.insecureTrustAll;
                      document.getElementById('rm-target-verify').checked = route.target.verifyHostname !== false;
                      document.getElementById('rm-target-rewrite').checked = !!route.target.rewriteHostHeader;
                      document.getElementById('route-modal-error').style.display = 'none';
                      document.getElementById('route-modal').style.display = 'flex';
                    }

                    function closeRouteModal() {
                      document.getElementById('route-modal').style.display = 'none';
                    }

                    function buildRoutePayload() {
                      return {
                        id: document.getElementById('rm-id').value.trim(),
                        listener: {
                          host: document.getElementById('rm-listener-host').value.trim() || '0.0.0.0',
                          port: parseInt(document.getElementById('rm-listener-port').value, 10),
                          transport: document.getElementById('rm-listener-transport').value
                        },
                        target: {
                          host: document.getElementById('rm-target-host').value.trim(),
                          port: parseInt(document.getElementById('rm-target-port').value, 10),
                          transport: document.getElementById('rm-target-transport').value,
                          sniHost: document.getElementById('rm-target-sni').value.trim() || null,
                          insecureTrustAll: document.getElementById('rm-target-insecure').checked,
                          verifyHostname: document.getElementById('rm-target-verify').checked,
                          rewriteHostHeader: document.getElementById('rm-target-rewrite').checked
                        }
                      };
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
                        renderRouteList();
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
                        if (activeRoute === routeId) {
                          activeRoute = null;
                          activeSession = null;
                        }
                        renderRouteList();
                        renderBanner();
                        if (activeRoute) {
                          renderRouteHeader();
                          renderRequestTable();
                        } else {
                          renderEmptyState('Route deleted.');
                        }
                        setStatus('success', 'Route "' + routeId + '" deleted.');
                      } catch (err) {
                        setStatus('error', err.message || 'Failed to delete route.');
                      }
                    }
                  </script>
                </body>
                </html>
                """;
    }
}
