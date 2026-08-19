let harImportEntryPrefills = [];

function openHarImportPicker() {
  const input = document.getElementById('har-import-file-input');
  input.value = '';
  input.click();
}

async function handleHarFileSelected(event) {
  const file = event.target.files?.[0];
  if (!file) return;
  let data;
  try {
    data = JSON.parse(await file.text());
  } catch {
    setStatus('error', 'That file is not valid JSON');
    return;
  }
  const entries = data?.log?.entries;
  if (!Array.isArray(entries) || !entries.length) {
    setStatus('error', 'No entries found in this HAR file');
    return;
  }
  const prefills = entries.map(buildHarEntryPrefill);
  if (prefills.length === 1) {
    openRequestBuilderModal(prefills[0]);
    return;
  }
  harImportEntryPrefills = prefills;
  openHarEntryPicker(prefills);
}

function buildHarEntryPrefill(entry) {
  const req = entry.request || {};
  let path = req.url || '';
  let query = '';
  try {
    const url = new URL(req.url, 'http://placeholder');
    path = url.pathname;
    query = url.search ? url.search.slice(1) : '';
  } catch {
    // Keep the raw url string as the path if it isn't a valid absolute/relative URL.
  }
  const headersText = (req.headers || [])
    .map(h => (h.name || '') + ': ' + (h.value || ''))
    .join('\n');
  const bodyText = (req.postData && req.postData.text) || '';
  return {
    method: req.method || 'GET',
    path,
    query,
    version: req.httpVersion || 'HTTP/1.1',
    headers: req.headers || [],
    headersText,
    bodyText,
    startedAt: entry.startedDateTime || ''
  };
}

function openHarEntryPicker(prefills) {
  const list = document.getElementById('har-import-entry-list');
  list.replaceChildren(...prefills.map((prefill, index) => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'har-entry-item';
    btn.dataset.action = 'select-har-entry';
    btn.dataset.entryIndex = String(index);
    const target = prefill.path + (prefill.query ? '?' + prefill.query : '');
    const when = formatTime(prefill.startedAt);
    btn.textContent = prefill.method + ' ' + target + (when ? ' — ' + when : '');
    return btn;
  }));
  const modal = document.getElementById('har-import-modal');
  modal.style.display = 'flex';
  modal.removeAttribute('aria-hidden');
  setTimeout(() => list.querySelector('.har-entry-item')?.focus(), 50);
}

function selectHarEntry(index) {
  const prefill = harImportEntryPrefills[index];
  closeHarImportModal();
  if (prefill) openRequestBuilderModal(prefill);
}

function closeHarImportModal() {
  const modal = document.getElementById('har-import-modal');
  modal.style.display = 'none';
  modal.setAttribute('aria-hidden', 'true');
}
