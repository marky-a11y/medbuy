# ADR-0002: PrimeFaces over React for Dashboard UI

## Status
Accepted (2026-06-29)

## Date
2026-06-29

## Context
The Media Buying Dashboard requires a rich, interactive user interface with:
- A hierarchical drill-down table (Platform → Sector → KPI metrics)
- Lazy loading and server-side sorting/pagination for a 13-column metrics table
- Real-time data staleness indicators
- Role-based KPI masking
- A Top Opportunity card with composite score and client prospects
- Global sector filter with URL state persistence

The UI must work within the existing Java web app architecture (mandated by `java_web_app.md`) and be deliverable within a 12-week MVP timeline with a single frontend developer.

## Decision
Use **PrimeFaces 12** on **Jakarta EE / JSF 2.3** for the main dashboard, combined with **Thymeleaf** for the ROI Calculator page. Both share a common CSS theme (`dashboard-theme.css`).

### Key Architecture

```
┌─────────────────────────────────────────────────────┐
│                  Browser (no SPA)                     │
├─────────────────────────────────────────────────────┤
│  PrimeFaces JSF (dashboard)  │  Thymeleaf (calculator) │
│  - Top Opportunity card       │  - ROI Calculator form  │
│  - Platform/Sector hierarchy  │  - Results display      │
│  - KPI LazyDataModel table   │  - Back to Dashboard    │
│  - Sector filter topbar      │                         │
│  - Source citation dialog    │                         │
│  - Client prospects table    │                         │
├─────────────────────────────────────────────────────┤
│              Spring Boot 2.7 (single JVM)              │
│  - JSF PhaseListener (PrimeFaces)                     │
│  - Thymeleaf ViewResolver                             │
│  - REST API controllers (/api/*)                      │
│  - WebSocket (for future real-time updates)           │
└─────────────────────────────────────────────────────┘
```

### Rationale

1. **`LazyDataModel` Integration** — PrimeFaces `LazyDataModel<T>` maps directly to Spring Data JPA `Page<T>` with server-side sorting. The `KpiLazyDataModel.load()` method translates UI pagination/sort state into a `Pageable` query. This avoids loading all 1000+ KPI rows into browser memory.

2. **Single JVM Simplicity** — JSF and the REST API run in the same Spring Boot process. The `DashboardBean` (JSF `@ManagedBean`) calls `DashboardService` synchronously — no inter-service HTTP calls, no CORS, no API gateway for internal requests.

3. **No SPA Build Pipeline** — PrimeFaces ships as a JAR dependency. No npm, webpack, Babel, or TypeScript compilation. The frontend developer only needs Java and a CSS editor.

4. **Server-Side State Management** — View-scoped beans (`@ViewScoped`) retain state across AJAX requests without client-side state management. The `KpiLazyDataModel` holds the current page, sort, and filter state on the server.

5. **Accessibility & Theming** — PrimeFaces provides built-in ARIA attributes, keyboard navigation, and a theming API. Custom CSS (`dashboard-theme.css`) overrides the default styling for a modern dashboard look.

## Alternatives Considered

### Alternative 1: React + TypeScript SPA
- **Pros**: Rich component ecosystem, better developer experience for complex UIs, virtual DOM performance, React Query for data fetching, strong typing with TypeScript.
- **Cons**: Requires separate build toolchain (Vite/Webpack, ESLint, Jest). CORS configuration for API calls. 6–8 week delay for frontend setup, component library integration, and training. Splits the codebase into two repositories or a monorepo with separate CI pipelines. Violates single-JVM simplicity.

### Alternative 2: Vaadin Flow
- **Pros**: Server-side Java UI, no JavaScript required, built-in data binding, Spring Boot integration.
- **Cons**: Heavy component tree on the server. Less mature lazy-loading for large tables. Smaller community than PrimeFaces. Custom styling requires CSS/SCSS overrides. Team has no Vaadin experience.

### Alternative 3: Thymeleaf for Everything
- **Pros**: Simplest architecture — all pages are server-rendered templates.
- **Cons**: No built-in lazy data table (would require building pagination with JavaScript). No `LazyDataModel` equivalent. Would need significant custom JavaScript for drill-down, sorting, and filtering. Loses PrimeFaces AJAX component updates.

### Alternative 4: Pure REST API + Any Frontend (Future)
- **Pros**: Frontend-agnostic. Could be consumed by a future mobile app or third-party tool.
- **Cons**: Over-engineered for MVP. Requires API versioning, client SDK, and a separate frontend team. We already have REST endpoints for CSV export and calculator — adding a full SPA is post-MVP scope.

## Consequences

### Positive
- **Rapid development**: Dashboard UI (FRONT-01 through FRONT-22) delivered in 2 weeks by a single developer.
- **Single JVM**: No CORS, no inter-service HTTP, no API gateway complexity for internal calls.
- **Server-side pagination**: `LazyDataModel` ensures the 13-column KPI table performs well even with 10,000+ rows.
- **Thymeleaf for static pages**: The ROI Calculator benefits from Thymeleaf's form binding and validation without PrimeFaces overhead.
- **CSS-only theming**: `dashboard-theme.css` provides consistent styling across JSF and Thymeleaf pages.

### Negative
- **No SPA benefits**: Every page navigation is a full server round-trip. No client-side routing, no optimistic UI updates, no offline support.
- **Heavier payloads**: JSF view state is serialized (although we use client-side state saving with compression).
- **PrimeFaces dependency**: Royalty-free but commercial-friendly. Must stay within the PrimeFaces 12.x line for compatibility.
- **JSF lifecycle complexity**: Debugging JSF lifecycle issues (restore view → apply values → validate → update model → invoke application → render response) requires experience.
- **Limited component ecosystem**: If we need a charting library or a complex form builder, we must integrate a third-party JSF library (e.g., PrimeFaces Extensions) or build custom components.

### Mitigation
- The REST API layer (`/api/*`) is maintained alongside the JSF UI. If a future SPA is developed, it can reuse the same REST endpoints without modifying the backend.
- The CSS theme is framework-agnostic. A React SPA could reuse `dashboard-theme.css` with minimal changes.
- The Thymeleaf calculator page can be migrated to JSF or kept as-is — it's isolated from the main dashboard.

## Related Documents
- [HLD.md](/design/HLD.md) §3.1 — Frontend Layer
- [LLD.md](/design/LLD.md) §5 — Frontend Architecture
- [Project_Plan.md](/design/Project_Plan.md) §5.4 — Frontend Development (WBS)
- `dashboard-theme.css` — Global CSS theme shared across rendering technologies
