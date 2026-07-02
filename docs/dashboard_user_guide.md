# Media Buying Dashboard - User Guide

## Overview
The Media Buying Dashboard provides a unified view of advertising performance across multiple platforms (Google Ads, Meta Ads, TikTok, LinkedIn, iHeart Radio) and commerce sectors (Technology, Finance, Manufacturing, Retail, Health & Wellness).

## Data Sources

The Media Buying Dashboard ingests market data from 10 diverse sources through a 4-stage ETL pipeline:

| Source | Type | Data Provided |
|--------|------|---------------|
| Google Trends (pytrends) | Search Trends | Keyword search volume, regional interest |
| eBay | Marketplace | Product listings, prices, demand signals |
| Reddit | Social | Subreddit posts, sentiment, brand mentions |
| X (Twitter) API | Social | Tweets, trending topics, sentiment |
| Meta Ad Library | Advertising | Ad spend estimates, ad count, demographics |
| Yelp Fusion | Local Business | Business ratings, review counts, categories |
| Foursquare Places | Local Business | Venue popularity, foot traffic, check-ins |
| Bing Webmaster | Search | Search queries, clicks, impressions, CTR |
| Skyscanner | Travel | Flight/hotel prices, demand trends |
| Job Market (Adzuna) | Employment | Job listings, salaries, hiring trends |

All sources operate in **mock mode** by default (`mock-enabled=true`) and return realistic simulated data without requiring API keys.

## Login
1. Navigate to the dashboard URL
2. Use the following demo credentials:
   - **Admin**: `admin` / `admin123` (full access)
   - **Analyst**: `analyst` / `analyst123` (can view KPI metrics)
   - **Viewer**: `viewer` / `viewer123` (limited view)

## Dashboard Features

### Top Opportunity Card
- Displays the highest-performing platform-sector combination
- Shows composite score (0-100) and qualitative badge (High/Medium/Low)
- Primary KPIs (ROAS and CAC) shown as secondary metrics
- Click to navigate directly to that platform-sector's metrics
- See **Client Prospects Table** below for associated client data

### Client Prospects Table
Located within the Top Opportunity card, this table displays the top 5 client prospects in the winning commerce sector, ranked by estimated advertising budget. It provides at-a-glance insight into the highest-value accounts to pursue.

| Column | Description |
|--------|-------------|
| **Company Name** | Name of the prospective client |
| **Est. Revenue** | Estimated annual revenue (formatted as `$XM`) |
| **YoY Growth** | Year-over-year revenue growth rate (formatted as `+X%`) |
| **Est. Ad Budget** | Estimated annual advertising budget (formatted as `$XM`) |

- The table is sorted by estimated ad budget in descending order by default
- Data is refreshed every 15 minutes in sync with KPI data

### Source Citations
Each KPI value in the metrics table has an associated **source citation** that provides provenance and transparency.

- **Info Icon** (ℹ️): Click the info icon next to any KPI value to view its source metadata
- **Citation Dialog**: A modal dialog displays:
  - Source name (e.g., "Google Ads API v17")
  - Source type (API / DOCUMENTATION / REPORT / MANUAL)
  - Source URL (clickable, opens in new tab)
  - License type
  - Last verified timestamp
  - Attribution context (RAW / INTERPOLATED / DERIVED)
- **Stale Indicator**: If the source's `last_verified_at` is more than 30 days old, the info icon appears in amber/warning color
- **CSV Export**: The "Source" column is included in CSV downloads, showing the primary source name per row

### Global Sector Filter
- Use the dropdown at the top of the dashboard to filter by sector
- When a sector is selected, the hierarchy collapses to a two-level view
- Filter persists in URL as `?sector={id}`

### Platform List
- Collapsible cards for each advertising platform
- Click a platform card to expand and view its sectors
- Click a sector to view detailed KPI metrics

### Metrics Table
- 13 KPI columns: ROAS, CAC, CLTV, Conversion Rate, Contribution Margin, Payback Period, Incremental Return, CPQL, Scalability, Cash Conversion Cycle, Saturation Point, Attribution Accuracy
- Sortable by any column
- Paginated (20 rows per page)
- Data staleness indicators (clock icon for data > 15 minutes old)
- Source citation icons adjacent to each KPI value

### Quick Command Center
Located in the dashboard bento grid, the Quick Command Center provides four action buttons for common tasks:

| Button | Action |
|--------|--------|
| **Upload CSV** | Upload campaign data in CSV format (stubbed for MVP) |
| **Auto-Optimize** | Trigger the auto-optimization engine (stubbed for MVP) |
| **Send Report** | Generate and send a summary report via email (stubbed for MVP) |
| **Instant Audit** | Launch an instant compliance audit (stubbed for MVP) |

### Strategic Insights
Two alert cards in the dashboard bento grid provide at-a-glance strategic information:

**Audience Fatigue Alert** (tertiary left border):
- Indicates when ad frequency exceeds recommended thresholds
- Shows affected platforms and sectors
- Recommends creative refresh or audience expansion

**Peak Hours Detected** (secondary left border):
- Identifies time-of-day performance patterns
- Shows optimal bidding windows
- Recommends budget reallocation to peak hours

### ROI Calculator
- Navigate via the "ROI Calculator" button on the dashboard
- Select platform, sector, purchase type, duration, and budget
- Click "Calculate ROI" to view projections
- Results include: projected ROI, expected ROAS, CAC, payback period, total revenue

### CSV Export
- Download KPI data as CSV for offline analysis
- Use the Export button to generate a CSV file with all current metrics
- Includes a "Source" column with the primary data source name per row

### Admin Panel (Admin only)
- Configure scoring weights for the composite model
- Weights must be between 0.0 and 1.0
- Normalize weights so they sum to 1.0

## Keyboard Navigation
- Tab through interactive elements
- Enter to activate buttons/links
- Escape to close expanded panels
- Escape to close source citation dialog

## Data Freshness
- Dashboard auto-refreshes every 5 minutes
- Data older than 15 minutes shows a staleness indicator (clock icon)
- Source data older than 30 days shows a stale citation icon (amber)
- KPI data ingested via Kafka from ad platform APIs

## Troubleshooting

### Dashboard fails to load
1. Check network connectivity to the dashboard URL
2. Verify your login credentials
3. Clear browser cache and cookies
4. Check the browser console (F12) for JavaScript errors
5. If the issue persists, contact the support team with the error details

### Staleness icon shown for all data
- The ingestion pipeline may be down or experiencing delays
- Check the observability dashboard (Grafana) for Kafka consumer lag alerts
- The system automatically recovers when the ingestion scheduler resumes
- If staleness persists > 30 minutes, contact the platform operations team

### KPI values shown as "---"
- You are logged in with the **Viewer** role, which masks sensitive KPI values
- KPI masking is applied to: CAC, CLTV, Contribution Margin, Payback Period, Incremental Return, CPQL, and Saturation Point
- Contact your administrator if you need access to these fields
- To test, log in as **admin** or **analyst** to see unmasked values

### Source citation dialog is empty
- The KPI data may not have associated source attribution records
- This can occur for manually entered or historical data
- Newly ingested data (via Kafka pipeline) automatically includes source references
- If the issue persists, check that the `SourceVerificationScheduler` is running

## Browser Compatibility
The dashboard is tested and supported on the following browsers:

| Browser | Minimum Version | Notes |
|---------|-----------------|-------|
| Google Chrome | 90+ | Primary development target |
| Mozilla Firefox | 90+ | Full feature parity |
| Microsoft Edge | 90+ | Chromium-based, same as Chrome |
| Apple Safari | 15+ | Some CSS grid features may render differently; functionality is unaffected |

> **Note**: Internet Explorer 11 and older browsers are not supported. The dashboard uses CSS Grid layout and modern JavaScript features not available in legacy browsers.

## Observability Reference
For monitoring and alerting procedures, refer to the [Observability Guide](observability-guide.md). Key references:

- **Alert procedures**: See *Alert Response Procedures* section for guidance on data staleness, pipeline failures, and performance degradation
- **Grafana dashboards**: Visualize JVM metrics, API latency, cache hit ratio, Kafka consumer lag, and integration health
- **Kibana logs**: Search by `correlationId` or `userId` for detailed debugging
- **Runbooks**: Contact the platform team for access to the full runbook library
