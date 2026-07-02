# Product Requirements Document (PRD) – Media Buying Dashboard

---

## 1. Overview

**Purpose** – Provide a single, interactive dashboard for media‑buying teams to surface the highest‑performing advertising opportunities across multiple platforms and commerce sectors. The dashboard surfaces a *Top Opportunity* summary in the header (based on key performance indicators) and a hierarchical view of platform ➜ sector ➜ metrics to enable quick analysis and data‑driven planning.

**Stakeholders**
- Media Buying Managers
- Performance Marketing Analysts
- Finance / CFO Office
- Product & Engineering Teams
- Executive Leadership (strategy & budgeting)

**Scope** – This MVP focuses on the following platforms and sectors (additional platforms can be added later):
- Platforms: Google Ads, Meta Ads, TikTok, LinkedIn, iHeart Radio, …
- Commerce Sectors: Technology, Finance, Manufacturing, Retail, Health & Wellness, …

---

## 2. Goals & Success Metrics
| Goal | Success Metric |
|------|----------------|
| Surface the highest‑value buying opportunity at a glance | ✅ Top Opportunity card displays the platform/sector with the best composite score (see Scoring Model below) within ≤ 2 seconds of page load |
| Enable drill‑down analysis with minimal clicks | ✅ Users can navigate from platform → sector → metric view in ≤ 2 clicks |
| Provide accurate KPI data for decision making | ✅ Data freshness < 15 minutes (derived from source ETL) |
| Reduce manual reporting effort | ✅ 30 % reduction in time spent compiling cross‑platform performance reports |
| Maintain high usability | ✅ SUS (System Usability Scale) ≥ 80 in user testing |

---

## 3. Functional Requirements

### 3.1 Header – Top Opportunity Card
- **Composite Scoring Model** – Calculates a weighted score using the following KPI inputs (weights configurable via admin UI):
  1. Return on Ad Spend (ROAS)
  2. Customer Acquisition Cost (CAC)
  3. Customer Lifetime Value (CLTV)
  4. Conversion Rate (CR)
  5. Scalability (estimated incremental spend capacity)
  6. Attribution Accuracy
- **Display Elements**
  - Platform name (e.g., *Google Ads*)
  - Commerce sector name (e.g., *Technology*)
  - Composite score (numeric) and qualitative badge (e.g., *High*, *Medium*, *Low*)
  - Primary KPI values (ROAS and CAC) shown as secondary line items
  - Clickable – navigates user to the detailed hierarchy view pre‑filtered to that platform & sector

### 3.2 Body – Hierarchical Media‑Buy Representation
- **Level‑1 (Platform)** – List of platforms displayed as collapsible cards.
- **Level‑2 (Commerce Sector)** – When a platform card is expanded, show a list of sectors under that platform.
- **Level‑3 (Metrics Table)** – When a sector is selected, render a table with the following columns (rounded to 2 decimals unless otherwise noted):
  - Return on Ad Spend (ROAS)
  - Customer Acquisition Cost (CAC)
  - Customer Lifetime Value (CLTV)
  - Contribution Margin After Advertising
  - Payback Period (months)
  - Incremental Return (percentage)
  - Conversion Rate (CR)
  - Cost Per Qualified Lead (CPQL)
  - Scalability (estimated dollar ceiling)
  - Cash Conversion Cycle (days)
  - Saturation Point (percentage of market reach)
  - Attribution Accuracy (percentage)
- **Sorting & Pagination** – Table sortable by any column; pagination set to 20 rows per page.

### 3.3 Global Filter – Commerce Sector
- A dropdown filter positioned above the platform list to **force a sector selection**. When a sector is chosen, the hierarchy collapses to a two‑level view (Platform ➜ Metrics) – the sector level is implicitly applied and hidden.
- Filter persists across page refreshes via URL query param (e.g., `?sector=technology`).

### 3.4 Interactions & Navigation
| Interaction | Result |
|------------|--------|
| Click Platform card | Expands to show its list of commerce sectors |
| Click Sector row (when no global filter) | Expands to show metrics table for that platform‑sector pair |
| Click Top Opportunity card | Navigates to the metrics table for that platform‑sector automatically expanded |
| Change Global Sector filter | Dashboard reloads showing only the selected sector across all platforms |
| Hover over any KPI value | Tooltip with definition, calculation method, and latest data source timestamp |

### 3.5 Data Refresh & Staleness Indicators
- Dashboard auto‑refreshes every **5 minutes**.
- Each KPI cell shows a small **clock icon** when the underlying data is older than **15 minutes** (indicates potential staleness).

### 3.6 ROI Calculator Access
- **Calculator Button** – A prominently placed button labeled “ROI Calculator” appears adjacent to the Top Opportunity card (or in the header action bar).  
- **Navigation** – Clicking the button navigates the user to a dedicated ROI Calculator page (e.g., `/calculator`). The calculator view pre‑populates the selected platform, commerce sector, and purchase type based on the current dashboard context.  
- **Parameters** – The calculator allows the user to specify: platform, commerce sector, purchase type (e.g., “Direct Response”, “Brand Awareness”), campaign duration (weeks/months), budget allocation, and any additional assumptions.  
- **Output** – Upon submission, the calculator displays projected ROI (%), expected ROAS, CAC, payback period, and a concise summary of assumptions used.  
- **Return Path** – After calculation, the user can click “Back to Dashboard” to return to the original view, preserving filters and scroll position.

### 3.7 Recommendations Section (Analyst Dashboard)
- **Position** – A dedicated recommendations section is displayed at the top of the analyst dashboard, immediately below the header and above the existing Top Opportunity card and hierarchical view.
- **Layout** – The section contains 4 columns arranged in a responsive bento grid (each column spanning 3 of 12 grid columns). Each column displays 3 ranked recommendations.
- **Column Definitions**:
  1. **Column A – Top Commerce Sectors** – Lists the top 3 commerce sectors ranked by overall opportunity score (e.g., Technology, Finance, Retail).
  2. **Column B – Top Commerce Sector + Client (Company) Combinations** – Lists the top 3 sector–company pairings ranked by combined score (e.g., Technology + Apple, Finance + JPMorgan).
  3. **Column C – Top Advertising Platforms** – Lists the top 3 advertising platforms ranked by overall opportunity score (e.g., Google Ads, Meta Ads, TikTok).
  4. **Column D – Top Advertising Platform + Commerce Sector Combinations** – Lists the top 3 platform–sector pairings ranked by composite KPI score (e.g., Google Ads + Technology, Meta Ads + Retail).
- **Recommendation Hyperlink** – Each of the 12 recommendations displays a clickable hyperlink labeled "Additional Information".
- **Recommendation Pop‑up Dialog** – When a user clicks "Additional Information", a modal dialog appears in the center of the page containing:
  - **Supporting KPIs** – A list of key performance indicators that justify the recommendation, displayed with values and trend indicators.
  - **Source Attribution** – Links to the external data sources used to determine those KPIs, including source name, URL (clickable, opens in new tab), license type, and last-verified timestamp.
  - **Dialog Dismissal** – The user can close the dialog via a close button (X) or by clicking outside the modal.

---

## 4. Non‑Functional Requirements
| Category | Requirement |
|----------|-------------|
| **Performance** | Initial page load ≤ 2 seconds; subsequent drill‑downs ≤ 1 second (client‑side caching + API pagination). |
| **Scalability** | Backend services must handle up to **10,000 concurrent users**; support horizontal scaling via Kubernetes deployment. |
| **Security** | Role‑based access (RBAC) – only users with *Media Analyst* role can see full KPI set. Data in‑transit encrypted (TLS 1.2+). |
| **Reliability** | 99.9 % uptime SLA; graceful degradation – if a platform’s data feed fails, show placeholder “Data unavailable”. |
| **Usability** | Responsive UI (desktop & 1080p+ monitors). Keyboard navigation support for accessibility. |
| **Observability** | Metrics for API latency, error rates, and cache hit ratios; logs captured in centralized ELK stack. |
| **Compliance** | GDPR‑compliant handling of any PII (e.g., customer identifiers) – masked in UI. |

---

## 5. Data Sources & KPI Calculations
| KPI | Primary Data Sources | Calculation Notes |
|-----|----------------------|-----------------|
| ROAS | Advertising spend, revenue attribution tables | Revenue ÷ Ad Spend |
| CAC | Total acquisition cost (ad spend + associated overhead) ÷ number of new customers |
| CLTV | Historical purchase data, churn rate, average order value |
| Conversion Rate | Clicks ÷ Impressions or Leads ÷ Visits (depending on funnel stage) |
| Scalability | Forecasted audience reach, platform caps, budget ceiling |
| Attribution Accuracy | % of conversions correctly matched to touch‑points (multi‑touch model) |
| Contribution Margin After Advertising | Gross margin – ad spend |
| Payback Period | CAC ÷ (Average Monthly Revenue per Customer) |
| Incremental Return | (Revenue after campaign – baseline revenue) ÷ ad spend |
| Cost Per Qualified Lead (CPQL) | Ad spend ÷ number of qualified leads |
| Cash Conversion Cycle | Days inventory + days receivable – days payable |
| Saturation Point | % of total addressable market reached by current spend |
| Attribution Accuracy | Same as above – provided as a separate column for clarity |

**ETL Frequency** – All source tables refreshed nightly; KPI‑specific real‑time streams (e.g., ROAS) refreshed every **15 minutes** via Kafka consumers.

---

## 6. User Interaction Flow (Wire‑frame Narrative)
1. **Landing Page** – User sees header with *Top Opportunity* card and the platform list collapsed.
2. **Identify Opportunity** – User reads the composite score and clicks the card.
3. **Drill‑Down** – Dashboard expands the selected platform, shows the chosen sector, and auto‑opens the metrics table.
4. **Analyze** – User sorts columns, hovers for definitions, and optionally applies the global sector filter to compare platforms side‑by‑side.
5. **Export** – A **Download CSV** button (visible in metrics view) lets the user export the current table for offline analysis.
6. **Feedback Loop** – Users can flag a KPI row as “needs review”; this creates a ticket in the internal issue tracker (future integration).

---

## 7. Acceptance Criteria
- **Header** displays a Top Opportunity card with a composite score calculated from the six KPI inputs. The card updates within **2 seconds** after any data refresh.
- **Hierarchical navigation** works with exactly the click sequence described; no more than **2 clicks** to reach the metrics table for any platform‑sector pair.
- **Global sector filter** correctly filters all platforms and hides the second hierarchical level.
- **Metrics table** shows all 13 KPI columns, sortable, with pagination, and correct data for at least **95 %** of test rows (verified against source ETL snapshots).
- **Performance** – Page load ≤ 2 seconds on a Chrome browser (network throttled to 3G).
- **Security** – Users without the *Media Analyst* role see the KPI columns masked as “---”.
- **Responsiveness** – UI renders correctly on 1920×1080 screens and scales down to 1366×768.
- **Documentation** – A brief user guide (Markdown) is added to the repo under `docs/dashboard_user_guide.md`.

---

## 8. Open Questions & Future Enhancements
- **Weight Configuration UI** – Should the weighting of the composite scoring model be editable per user or globally?
- **Real‑Time Attribution** – Integrate server‑side event ingestion to provide sub‑hour attribution updates.
- **Predictive Modeling** – Add a “What‑If” simulation layer to forecast ROI when scaling spend.
- **Mobile View** – Consider a simplified mobile‑optimized version for on‑the‑go executives.

---

*Prepared by the Product Management team – June 2026*