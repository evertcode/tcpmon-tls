---
name: "Modernize control plane UI and UX"
description: "Raise the tcpmon-tls control plane to an enterprise-grade product through a design system foundation, trustworthy async states, a fleet overview dashboard, and power-user controls."
created_at: "2026-08-21T17:31:32Z"

created_by:
  tool: "Claude Code"
  model:
    name: "Claude Opus"
    version: "5"
    reasoning_effort: "high"
---

# Modernize control plane UI and UX

## 🎯 Goal

Raise the tcpmon-tls control plane from a functional tool to an enterprise-grade product. Give it one design system, trustworthy loading and empty states, a fleet-wide health overview, and power-user controls for the daily inspection loop.

## 👀 Context

There is no `AGENTS.md` file and no `docs/` architecture documentation in this repository. The conventions below come from the code itself.

### Web UI files

- [`src/main/resources/web/index.html`](../../../src/main/resources/web/index.html): single page shell. Topbar, sidebar and five empty containers that JavaScript fills. Six `div` overlay modals with `role="dialog"`. Thirteen classic scripts loaded with `defer`.
- [`src/main/resources/web/styles/app.css`](../../../src/main/resources/web/styles/app.css): the only stylesheet. It holds 19 colour tokens on `:root` (lines 49-72), a dark theme on `:root[data-theme="dark"]` (lines 1524-1542), six media queries, and a `prefers-reduced-motion` block (lines 2406-2424).
- [`src/main/resources/web/js/state.js`](../../../src/main/resources/web/js/state.js): the global `window.uiState` object with `getState`, `setState` and `patchState`.
- [`src/main/resources/web/js/utils.js`](../../../src/main/resources/web/js/utils.js): shared helpers. `formatDuration` (line 210) and `buildEmptyState(message, hint, action)` (line 251) already exist.
- [`src/main/resources/web/js/app.js`](../../../src/main/resources/web/js/app.js): `renderApp`, the SSE connection (line 621), `setStatus` (line 592), the modal focus trap (line 577) and the Escape handler (line 489).
- [`src/main/resources/web/js/routes.js`](../../../src/main/resources/web/js/routes.js): `renderRouteList` (line 192), `renderRouteHeader` (line 242), `buildRouteHeaderViewModel` (line 282), `calculateAverageDuration` (line 304).
- [`src/main/resources/web/js/sessions.js`](../../../src/main/resources/web/js/sessions.js): `renderRequestTable` (line 150), `renderRequestTableContent` (line 231), `buildRequestTableElement` (line 332).
- [`src/main/resources/web/js/details.js`](../../../src/main/resources/web/js/details.js): `loadSessionDetails` (line 1), `renderPayloads` (line 44).

### Backend files

- [`src/main/java/com/cafeina/tcpmon/web/ControlPlaneServer.java`](../../../src/main/java/com/cafeina/tcpmon/web/ControlPlaneServer.java): every REST handler and the static asset handler (`handleAsset`, line 151). Assets under `src/main/resources/web` are served from the classpath. A new `.js` file needs no registration on the Java side, only a `script` tag in `index.html`.

### Test files

- [`src/test/js/web-helpers.test.mjs`](../../../src/test/js/web-helpers.test.mjs): the JavaScript suite. It runs on the Node.js built-in test runner. It carries its own DOM shim (lines 96-281) that supports `querySelector`, `querySelectorAll`, `closest`, `classList.toggle`, `dataset`, `setAttribute` and `addEventListener`. It loads `state.js`, `utils.js`, `routes.js`, `route-modal.js`, `sessions.js`, `details.js` and `actions.js` (lines 308-318). A new JavaScript file under test must be added to that load list.
- [`src/test/java/com/cafeina/tcpmon/web/ControlPlaneServerTest.java`](../../../src/test/java/com/cafeina/tcpmon/web/ControlPlaneServerTest.java): the endpoint suite. It covers authentication, asset loading, caching headers and content types.

### Conventions

- Verification command: `npm run test:all`. It runs the JavaScript suite and then `mvn test`. There is no lint step, no formatter configuration and no frontend build step.
- JavaScript files use lowercase names with hyphens. Functions use camelCase. There are no ES modules: every file declares globals and the `defer` order in `index.html` guarantees the load sequence.
- JaCoCo enforces a minimum of 45 percent line coverage and 25 percent branch coverage.
- [`CHANGELOG.md`](../../../CHANGELOG.md) uses `## [version] - YYYY-MM-DD` headers with `### Features`, `### UI improvements` and `### Bug fixes` sections. The version lives in `pom.xml` line 8.

### Findings that motivate this plan

- The token set holds colours only. Font sizes are ad-hoc values between 10px and 16px. There is no spacing, radius or elevation scale.
- Five card component blocks are defined twice: `app.css:731-746` and `app.css:1893-1913`.
- The focus rings use hardcoded `rgba(15, 108, 189, ...)` values at `app.css:157`, `209` and `252`. They are not overridden for the dark theme.
- The action buttons use the Unicode glyphs `✎`, `⇧`, `+` and `✕` at `index.html:46-48`. There is no icon set.
- No skeleton component exists. Only a single `.loading-overlay` class is toggled on `#payloads`.
- The REST API has no aggregate endpoint, so a user cannot see the health of every route in one screen.
- The request table has no sorting, no density control and no latency thresholds. Every duration renders in the same alarm colour.
- The markup has no skip link. The sidebar is an `aside`, not a `nav`. The route search input has no label element.

## 🪜 Phases

### Phase 1: Design system foundation and shared UI states

Give the whole UI one visual language and one set of async-state components. Every later phase consumes these primitives. The user sees the result immediately across every screen.

#### Public contracts

**Application services (JavaScript)**

- `src/main/resources/web/js/icons.js` (new file)
  - `icon(name, options)`: returns an inline SVG element. Supported names: `edit`, `upload`, `plus`, `close`, `search`, `refresh`, `warning`, `check`, `chevron-down`, `chevron-up`, `command`, `activity`.
- `src/main/resources/web/js/utils.js`
  - `buildSkeleton(variant, rowCount)` (new): returns a placeholder element. Variants: `route-list`, `table`, `payload`.
  - `buildErrorState(message, retryLabel, onRetry)` (new): returns an error block with a retry button.
  - `buildEmptyState(message, hint, action)` (unchanged): this phase only adds callers.

**CSS tokens (`src/main/resources/web/styles/app.css`)**

- Spacing scale: `--space-1` to `--space-8`.
- Typography scale: `--text-xs`, `--text-sm`, `--text-base`, `--text-lg`, `--text-xl`, plus `--leading-tight` and `--leading-normal`.
- Radius scale: `--radius-sm`, `--radius-md`, `--radius-lg`, `--radius-pill`.
- Elevation scale: `--elevation-1`, `--elevation-2`, `--elevation-3`.
- Focus tokens: `--focus-ring` and `--focus-ring-soft`, redefined under `:root[data-theme="dark"]`.
- Deleted: the duplicated card block at `app.css:1893-1913`.

**Text copies**

- Skip link: "Skip to main content".
- Route search label: "Filter routes".
- First run empty state: "No routes yet" / "Create your first route to start capturing traffic." / action "Add route".
- Empty request table: "No requests captured yet" / "Traffic sent to this listener appears here."
- Empty result after a filter: "No requests match your filters" / action "Clear filters".
- Error state: "Could not load this data." / action "Retry".

**Test suite (`src/test/js/web-helpers.test.mjs`)**

- "icon returns an svg element with the requested name"
- "icon falls back to a generic glyph for an unknown name"
- "buildSkeleton renders the requested row count"
- "buildErrorState renders the message and wires the retry action"
- "buildEmptyState renders an action button when an action is given"

#### To-do actions

- [x] Add the spacing, typography, radius, elevation and focus tokens to `:root`, and redefine the focus tokens under `:root[data-theme="dark"]`.
- [x] Replace the ad-hoc `px` font sizes and paddings in `app.css` with the new tokens.
- [x] Delete the duplicated card block at `app.css:1893-1913` and keep one definition per card component.
- [x] Replace the hardcoded `rgba(15, 108, 189, ...)` focus colours with `--focus-ring` and `--focus-ring-soft`.
- [x] ~~Create `src/main/resources/web/js/icons.js`~~ See deviation 1 below.
- [x] Replace the `✎`, `⇧`, `+` and `✕` glyphs and the six modal close buttons with declarative `data-icon` markup.
- [x] Add `buildSkeleton` and `buildErrorState` to `utils.js`, together with their CSS.
- [x] Show a skeleton in `renderRouteList`, `loadRequestsPage` and `loadSessionDetails` while a load is in flight.
- [x] Add the first run onboarding state. See deviation 2 below.
- [x] Add the skip link, change the sidebar `aside` to `nav aria-label="Routes"`, and add a label for `#route-search`.
- [x] Add the five test cases to `web-helpers.test.mjs`.
- [x] Verify the changes with `npm run test:all`. 30 JavaScript tests and 63 Java tests pass.
- [x] STOP. Present the changes to the user for review.

#### Phase 1 outcome and deviations

**Deviation 1: no new `icons.js` file.** The exploration reported that the UI had no icon set. That was wrong. `utils.js` already held `ICON_PATHS` with 16 icons, `buildIcon(name)` and `setButtonContent(button, label, iconName, options)`, and `app.js` already called them from `initializeStaticButtonIcons()`. The Unicode glyphs in `index.html` were only fallback content that the boot code replaced.

A new `icons.js` would have duplicated that system. Instead this phase:

- Added seven icons to the existing `ICON_PATHS`: `search`, `warning`, `check`, `chevron-down`, `chevron-up`, `command`, `activity`.
- Added `hydrateStaticIcons(root)` to `utils.js`, which wires every `[data-icon]` element.
- Replaced the 13 hardcoded `setButtonContent` calls in `initializeStaticButtonIcons()` with one call to `hydrateStaticIcons(document)`, and moved the icon names into `index.html` as `data-icon` and `data-icon-label` attributes. The rendered result is identical and the button list is no longer duplicated between the markup and the boot code.

**Deviation 2: the request table keeps its own empty copies.** The agreed copies "No requests captured yet" and "No requests match your filters" were going to replace the copies in `renderRequestTable`. The existing copies are more specific: they name the listener address and summarise the active filters. Replacing them would be a regression, so this phase kept them. Only the route list first run copy changed to the agreed "No routes yet" wording, and its button now uses the `plus` icon instead of a literal `+` character.

**Extra change:** `FakeNode.getAttribute` was added to the DOM shim in `web-helpers.test.mjs`, because `hydrateStaticIcons` reads the `aria-label` attribute.

**Not done in this phase:** `CHANGELOG.md` and the `pom.xml` version are untouched. This project records them per release, and Phase 1 is not a release.

### Phase 2: Fleet overview dashboard

Add a cross-route health view. A user sees the health of every route in one screen before they drill into a single route. This phase carries the enterprise impact of the plan.

#### Public contracts

**Application services (Java)**

- `com.cafeina.tcpmon.web.OverviewAggregator` (new class)
  - `aggregate(int windowMinutes)`: runs the SQL aggregation over the existing session tables. There is no schema change.
- `com.cafeina.tcpmon.web.ControlPlaneServer`
  - `handleOverview(HttpExchange exchange)` (new): serves `GET /api/overview?windowMinutes={int}`.
  - Response shape: `{ generatedAt, windowMinutes, totals: { requests, errors, errorRate, p50Ms, p95Ms }, routes: [ { routeId, listener, target, status, requests, errors, errorRate, p50Ms, p95Ms, sparkline: [int] } ], slowestPaths: [ { method, path, routeId, p95Ms, count } ] }`.

**Application services (JavaScript)**

- `src/main/resources/web/js/overview.js` (new file)
  - `loadOverview()`: fetches the payload and stores it in the state.
  - `buildOverviewViewModel(payload)`: maps the payload to render data.
  - `renderOverview()`: renders the view into `#overview`.
  - `buildSparkline(points, options)`: returns an inline SVG sparkline.
- `src/main/resources/web/js/state.js`
  - New keys: `activeView` (`routes` or `overview`), `overviewData`, `overviewWindowMinutes`, `overviewRefreshInFlight`.

**Text copies**

- View switch: "Overview", "Routes".
- Metric labels: "Requests", "Error rate", "p50 latency", "p95 latency".
- Section titles: "Route health", "Slowest paths".
- Window selector: "Last 15 minutes", "Last hour", "Last 24 hours".
- Empty state: "No traffic in this window" / "Widen the time window or send a request through a route."

**Test suites**

- `src/test/java/com/cafeina/tcpmon/web/ControlPlaneServerTest.java`
  - "overview endpoint returns totals and per route rows"
  - "overview endpoint honours the window parameter"
  - "overview endpoint rejects an invalid window"
  - "overview endpoint requires authentication"
- `src/test/js/web-helpers.test.mjs`
  - "buildOverviewViewModel computes the error rate per route"
  - "buildOverviewViewModel marks a route as degraded above the error threshold"
  - "buildSparkline renders one point per bucket"
  - "buildOverviewViewModel returns an empty state flag when there is no traffic"

#### To-do actions

- [x] Create `OverviewAggregator`. See deviation 1 below.
- [x] Add `handleOverview` to `ControlPlaneServer` and register the `/api/overview` path.
- [x] Add the four `ControlPlaneServerTest` cases.
- [x] Add the `#overview` container and the view switch to `index.html`.
- [x] Create `src/main/resources/web/js/overview.js` and add its `script` tag to the `defer` chain.
- [x] Render the metric row, the route health table and the slowest paths, reusing the Phase 1 skeleton and empty states.
- [x] Add the time window selector and keep the choice in `localStorage` under the key `tcpmon-overview-window`.
- [x] Refresh the overview from the existing SSE stream when the overview is the active view.
- [x] Add `overview.js` to the module load list in `web-helpers.test.mjs` and write the four test cases.
- [x] Verify the changes with `npm run test:all`. 34 JavaScript tests and 67 Java tests pass.
- [x] STOP. Present the changes to the user for review.

#### Phase 2 outcome and deviations

**Deviation 1: the SQL lives in `SessionStore`, not in `OverviewAggregator`.** Every other query in this project sits in `SessionStore`, which owns the JDBC connection and guards it with `synchronized`. An aggregator that opened its own connection would break that. The work is split instead:

- `SessionStore.overviewExchanges(Instant since, int limit)` runs one indexed query and returns typed rows.
- `com.cafeina.tcpmon.session.OverviewExchange` is the new row record.
- `OverviewAggregator.aggregate(int windowMinutes)` keeps the agreed signature. It calls the store, then delegates to the pure static `summarize(rows, routes, windowMinutes, now)`, which is what the tests exercise.

**Deviation 2: the response reports `clientErrors` as well.** The agreed shape carried only `errors`. This project counts an error as a 5xx response, and the overview keeps that definition so the numbers agree with the route statistics shown elsewhere. A route that answers every call with 404 would then look healthy, so 4xx responses are counted apart as `clientErrors`. The route status still derives from the 5xx rate alone.

**Deviation 3: the response uses records, not maps.** The other endpoints build `LinkedHashMap` payloads. The new endpoint returns `OverviewReport`, `OverviewTotals`, `RouteOverview` and `SlowPath`. Jackson serialises them without extra configuration and the shape is checked at compile time.

**Bug found and fixed during the runtime check:** the routes layout stayed visible under the overview. The `hidden` attribute loses to `.layout { display: grid }`, so `app.css` now states `.layout[hidden] { display: none; }`. A unit test could not have caught this; it needed the real browser.

**Runtime verification:** the app ran against a local target with six requests, one 500 and one 404. The endpoint reported 6 requests, a 16.67 percent error rate, p50 7 ms, p95 410 ms, and ranked `/slow` first. The view switch, the route drill down, and the light and dark themes were all checked in a browser. The console reported no errors.

**Accepted limit:** one report reads at most 20000 exchanges (`OverviewAggregator.MAX_ROWS`). A busier 24 hour window would truncate. That is acceptable for a debugging tool and keeps the report bounded.

**Not done in this phase:** `CHANGELOG.md` and the `pom.xml` version are untouched, as in Phase 1.

### Phase 3: Power-user request table and command palette

Make the daily inspection loop fast for an expert user. Add sorting, a density control, latency thresholds, and keyboard-first navigation.

#### Public contracts

**Application services (JavaScript)**

- `src/main/resources/web/js/sessions.js`
  - `sortRequestRows(rows, sortKey, sortDirection)` (new).
  - `buildRequestTableElement(pageItems, activeSession, activeExchangeIndex, showRoute, viewOptions)`: gains the `viewOptions` parameter that carries `density`, `sortKey` and `sortDirection`.
- `src/main/resources/web/js/utils.js`
  - `latencyLevel(durationMs)` (new): returns `fast`, `moderate` or `slow` against the thresholds 300 ms and 1000 ms.
- `src/main/resources/web/js/command-palette.js` (new file)
  - `openCommandPalette()`, `closeCommandPalette()`, `buildPaletteCommands(state)`, `filterPaletteCommands(commands, query)`.
- `src/main/resources/web/js/state.js`
  - New keys: `requestSortKey`, `requestSortDirection`, `tableDensity`, `paletteOpen`.

**Text copies**

- Density switch: "Comfortable", "Compact".
- Palette placeholder: "Search routes and actions...".
- Palette empty state: "No matching command".
- Shortcuts dialog title: "Keyboard shortcuts". Rows: "Open command palette", "Close dialog", "Move between rows", "Reorder route", "Focus route search".
- Palette commands: "Go to Overview", "Add route", "New request", "Import HAR", "Clear filters", "Toggle theme", "Open route: {routeId}".

**Test suite (`src/test/js/web-helpers.test.mjs`)**

- "sortRequestRows orders by duration in both directions"
- "sortRequestRows keeps a stable order for equal keys"
- "latencyLevel classifies a duration against the thresholds"
- "buildRequestTableElement applies the compact density class"
- "buildRequestTableElement marks the active sort column"
- "buildPaletteCommands includes one command per route"
- "filterPaletteCommands matches on the command label"

#### To-do actions

- [x] Add `latencyLevel` to `utils.js` and colour the duration cell by level. See deviation 1 below.
- [x] Add `sortRequestRows` and sortable column headers that carry `aria-sort`.
- [x] Add the density switch and keep the choice in `localStorage` under the key `tcpmon-table-density`.
- [x] Make the table header sticky inside its scroll container.
- [x] Create `src/main/resources/web/js/command-palette.js`, add its `script` tag, and bind the `Cmd/Ctrl+K` shortcut.
- [x] Reuse the existing modal focus trap and Escape handler for the palette.
- [x] Add the "Keyboard shortcuts" dialog and bind the `?` key to open it.
- [x] Add `command-palette.js` to the module load list in `web-helpers.test.mjs` and write the seven test cases.
- [x] Verify the changes with `npm run test:all`. 41 JavaScript tests and 67 Java tests pass.
- [x] STOP. Present the changes to the user for review.

#### Phase 3 outcome and deviations

**Deviation 1: `latencyLevel` replaced duplicated logic, it did not add new logic.** The threshold rule already existed twice: in `formatDuration` (`utils.js`) and in `buildDurationCell` (`sessions.js`). Both now call the new `latencyLevel(durationMs)` and its companion `latencyClass(durationMs)`. The moderate threshold moved from 200 ms to the agreed 300 ms. The slow threshold stays at 1000 ms.

The earlier note that every duration rendered in the alarm colour came from an old screenshot. The colour coding already worked before this phase.

**Deviation 2: the sort applies to the current page only.** The request list uses server-side cursor pagination, and `sortRequestRows` reorders the rows the browser already holds. A sort by duration therefore ranks the visible page, not the whole result set. Every sortable header carries the title "Sort the requests on this page" so the scope is visible to the user. Server-side sorting would need a new API contract and was not in this plan.

**Extra commands in the palette.** The agreed list did not include "Go to Routes". Without it the palette could enter the overview but not leave it, so it was added next to "Go to Overview".

**Two bugs found during the runtime check.** Neither was reachable from a unit test:

1. The request toolbar used a four column grid. The new density control became a fifth item and wrapped onto its own full width row. The toolbar is now a wrapping flex row, which also fixes the "Clear filters" button that wrapped before this phase.
2. Closing the command palette set `aria-hidden="true"` on the dialog while its input still held focus. The browser reported it, and a screen reader user would lose the focused element. `restoreFocusOutOf(modal, opener)` now moves the focus out before the dialog is hidden. The same fix applies to the shortcuts dialog.

**One refinement after the check:** the `?` and `/` shortcuts skip text entry targets. A `select` swallows no characters, so it was removed from that guard. Otherwise `?` stopped working after the user touched any dropdown.

**Runtime verification:** the sortable headers, the compact density, the persisted density choice, the palette filter and its Enter action, and the shortcuts dialog were all checked in a browser against live traffic. The console reported no errors and no warnings.

**Not done in this phase:** `CHANGELOG.md` and the `pom.xml` version are untouched, as in Phase 1 and Phase 2.

## ⏭️ Next step

Every phase is complete. Review the three phases, then decide whether to bump the version in `pom.xml` and write the `CHANGELOG.md` entry for the release.

Plan built to impress the enterprise by 🐢 💨 (Turbotuga™, [Codely](https://codely.com)'s mascot)
