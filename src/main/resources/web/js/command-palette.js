let commandPaletteOpenerEl = null;
let shortcutsOpenerEl = null;

/**
 * Builds every command the palette can run for the given state.
 * The state is passed in so the list can be tested without a live page.
 */
function buildPaletteCommands(state = {}) {
  const routes = Array.isArray(state?.proxyConfig?.routes) ? state.proxyConfig.routes : [];
  const commands = [
    {
      id: 'go-overview',
      label: 'Go to Overview',
      hint: 'View',
      run: () => setActiveView('overview')
    },
    {
      id: 'go-routes',
      label: 'Go to Routes',
      hint: 'View',
      run: () => setActiveView('routes')
    },
    {
      id: 'add-route',
      label: 'Add route',
      hint: 'Route',
      run: () => openAddRouteModal()
    },
    {
      id: 'new-request',
      label: 'New request',
      hint: 'Request',
      run: () => openRequestBuilderModal()
    },
    {
      id: 'import-har',
      label: 'Import HAR',
      hint: 'Request',
      run: () => document.getElementById('har-import-file-input')?.click()
    },
    {
      id: 'clear-filters',
      label: 'Clear filters',
      hint: 'Request',
      run: () => clearRequestFilters()
    },
    {
      id: 'toggle-theme',
      label: 'Toggle theme',
      hint: 'Appearance',
      run: () => {
        applyThemePreference(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark');
        renderConfigButton();
      }
    }
  ];
  for (const route of routes) {
    commands.push({
      id: 'open-route:' + route.id,
      label: 'Open route: ' + route.id,
      hint: 'Route',
      run: async () => {
        await setActiveView('routes');
        selectRoute(route.id);
      }
    });
  }
  return commands;
}

function filterPaletteCommands(commands, query) {
  const list = Array.isArray(commands) ? commands : [];
  const needle = String(query || '').trim().toLowerCase();
  if (!needle) return list;
  return list.filter(command => String(command.label || '').toLowerCase().includes(needle));
}

function renderPaletteResults() {
  const list = document.getElementById('command-palette-list');
  const input = document.getElementById('command-palette-input');
  if (!list) return;
  const commands = filterPaletteCommands(buildPaletteCommands(window.uiState), input ? input.value : '');
  if (!commands.length) {
    list.replaceChildren(buildEmptyState('No matching command'));
    return;
  }
  const items = commands.map((command, index) => {
    const item = document.createElement('button');
    item.className = 'palette-item' + (index === 0 ? ' is-active' : '');
    item.type = 'button';
    item.dataset.action = 'run-palette-command';
    item.dataset.commandId = command.id;

    const label = document.createElement('span');
    label.className = 'palette-item-label';
    label.textContent = command.label;
    item.appendChild(label);

    if (command.hint) {
      const hint = document.createElement('span');
      hint.className = 'palette-item-hint';
      hint.textContent = command.hint;
      item.appendChild(hint);
    }
    return item;
  });
  list.replaceChildren(...items);
}

function runPaletteCommand(commandId) {
  const command = buildPaletteCommands(window.uiState).find(entry => entry.id === commandId);
  closeCommandPalette();
  if (command) command.run();
}

function openCommandPalette() {
  const modal = document.getElementById('command-palette');
  if (!modal) return;
  commandPaletteOpenerEl = document.activeElement;
  setState('paletteOpen', true);
  modal.style.display = 'flex';
  modal.setAttribute('aria-hidden', 'false');
  const input = document.getElementById('command-palette-input');
  if (input) {
    input.value = '';
    input.focus();
  }
  renderPaletteResults();
}

function closeCommandPalette() {
  const modal = document.getElementById('command-palette');
  if (!modal) return;
  setState('paletteOpen', false);
  const opener = commandPaletteOpenerEl;
  commandPaletteOpenerEl = null;
  // Move the focus out before the dialog becomes aria-hidden. A focused element
  // inside an aria-hidden ancestor is hidden from assistive technology.
  restoreFocusOutOf(modal, opener);
  modal.style.display = 'none';
  modal.setAttribute('aria-hidden', 'true');
}

function restoreFocusOutOf(modal, opener) {
  const active = document.activeElement;
  if (active && modal.contains(active) && typeof active.blur === 'function') {
    active.blur();
  }
  if (opener && opener !== document.body && typeof opener.focus === 'function') {
    opener.focus();
  }
}

function movePaletteSelection(step) {
  const list = document.getElementById('command-palette-list');
  if (!list) return;
  const items = [...list.querySelectorAll('.palette-item')];
  if (!items.length) return;
  const currentIndex = items.findIndex(item => item.classList.contains('is-active'));
  const nextIndex = (currentIndex + step + items.length) % items.length;
  items.forEach(item => item.classList.remove('is-active'));
  items[nextIndex].classList.add('is-active');
  items[nextIndex].scrollIntoView({ block: 'nearest' });
}

function runActivePaletteCommand() {
  const active = document.querySelector('#command-palette-list .palette-item.is-active');
  if (active) runPaletteCommand(active.dataset.commandId);
}

function openShortcutsDialog() {
  const modal = document.getElementById('shortcuts-modal');
  if (!modal) return;
  shortcutsOpenerEl = document.activeElement;
  modal.style.display = 'flex';
  modal.setAttribute('aria-hidden', 'false');
  document.getElementById('shortcuts-modal-close-btn')?.focus();
}

function closeShortcutsDialog() {
  const modal = document.getElementById('shortcuts-modal');
  if (!modal) return;
  const opener = shortcutsOpenerEl;
  shortcutsOpenerEl = null;
  restoreFocusOutOf(modal, opener);
  modal.style.display = 'none';
  modal.setAttribute('aria-hidden', 'true');
}
