package com.autoresolve.mediabuying.controller.dashboard;

import com.autoresolve.mediabuying.integration.wrapper.DataSourceStatusProvider;
import com.autoresolve.mediabuying.model.dto.ClientInsightMetricsDTO;
import com.autoresolve.mediabuying.model.dto.SourceMetadataDTO;
import com.autoresolve.mediabuying.model.entity.KPIMetrics;
import com.autoresolve.mediabuying.repository.KPIMetricsRepository;
import com.autoresolve.mediabuying.model.entity.OpportunityMetric;
import com.autoresolve.mediabuying.repository.OpportunityMetricsRepository;
import com.autoresolve.mediabuying.service.ClientMetricsService;
import com.autoresolve.mediabuying.service.SourceAttributionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * View-scoped managed bean providing recommendation info dialog data:
 * contributing source LIVE/MOCK status, KPI definitions with tooltips,
 * and clickable data source reference links specific to the recommendation.
 */
@Component
@Scope("view")
public class RecommendationInfoBean implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(RecommendationInfoBean.class);
    private static final long serialVersionUID = 1L;

    @Autowired
    private transient List<DataSourceStatusProvider> wrappers;

    @Autowired
    private transient KPIMetricsRepository kpiMetricsRepository;

    @Autowired
    private transient SourceAttributionService sourceAttributionService;

    @Autowired
    private transient KpiTooltipBean kpiTooltipBean;

    @Autowired
    private transient OpportunityMetricsRepository opportunityMetricsRepository;

    @Autowired
    private transient ClientMetricsService clientMetricsService;

    // Tracks the currently selected company from the client prospects table
    private String selectedCompanyName;
    private String selectedCompanyVertical;
    private transient ClientInsightMetricsDTO selectedCompanyMetrics;

    // Cache for KPI ID lookup
    private Long cachedPlatformId;
    private Long cachedSectorId;
    private List<DataSourceStatusProvider> cachedContributingSources;
    private List<SourceRef> cachedSourceRefs;

    /**
     * Returns only those wrappers whose data contributed to the KPI metrics
     * for the given platform and sector (the Top Pick recommendation).
     * Filters by looking up source attribution for the KPI metrics row.
     */
    public List<DataSourceStatusProvider> getContributingSources(Long platformId, Long sectorId) {
        if (platformId == null || sectorId == null) {
            return wrappers != null ? wrappers : Collections.emptyList();
        }

        // Use cache if same platform/sector
        if (platformId.equals(cachedPlatformId) && sectorId.equals(cachedSectorId)
                && cachedContributingSources != null) {
            return cachedContributingSources;
        }

        this.cachedPlatformId = platformId;
        this.cachedSectorId = sectorId;

        Set<String> contributingSourceNames = lookupContributingSourceNames(platformId, sectorId);

        if (contributingSourceNames.isEmpty() || wrappers == null) {
            cachedContributingSources = wrappers != null ? wrappers : Collections.emptyList();
        } else {
            List<DataSourceStatusProvider> filtered = new ArrayList<>();
            for (DataSourceStatusProvider w : wrappers) {
                if (contributingSourceNames.contains(w.getSourceName())) {
                    filtered.add(w);
                }
            }
            if (filtered.isEmpty()) {
                // Fallback: if no direct attribution found, show all wrappers
                filtered = new ArrayList<>(wrappers);
            }
            cachedContributingSources = filtered;
        }
        return cachedContributingSources;
    }

    /**
     * Returns source references (name + URL) for sources that contributed
     * to the KPI metrics for the given platform and sector.
     */
    public List<SourceRef> getContributingSourceRefs(Long platformId, Long sectorId) {
        if (platformId == null || sectorId == null) {
            return getAllSourceRefs();
        }

        if (platformId.equals(cachedPlatformId) && sectorId.equals(cachedSectorId)
                && cachedSourceRefs != null) {
            return cachedSourceRefs;
        }

        Set<String> contributingNames = lookupContributingSourceNames(platformId, sectorId);

        if (contributingNames.isEmpty()) {
            cachedSourceRefs = getAllSourceRefs();
        } else {
            List<SourceRef> refs = new ArrayList<>();
            for (String name : contributingNames) {
                String url = getSourceUrl(name);
                if (url != null) {
                    refs.add(new SourceRef(name, url));
                }
            }
            cachedSourceRefs = refs;
        }
        return cachedSourceRefs;
    }

    /**
     * Returns the tooltip HTML for a given KPI key.
     */
    public String getKpiTooltip(String kpiKey) {
        return kpiTooltipBean != null ? kpiTooltipBean.getTooltip(kpiKey) : "";
    }

    // Cache for company-specific metric
    private transient OpportunityMetric selectedCompanyMetric;

    /**
     * Returns the current opportunity metric to display:
     * - If a company-specific metric was selected via {@link #selectCompany(String)}, returns that
     * - Otherwise returns the global top opportunity metric (highest Opportunity Index).
     */
    public OpportunityMetric getTopOpportunityMetric() {
        // Return company-specific metric if set
        if (selectedCompanyMetric != null) {
            return selectedCompanyMetric;
        }
        // Fall back to global top
        try {
            List<OpportunityMetric> top = opportunityMetricsRepository.findTop5ByOrderByOpportunityIndexDesc();
            if (top != null && !top.isEmpty()) {
                return top.get(0);
            }
        } catch (Exception e) {
            log.warn("Failed to load top opportunity metric: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Called from the client prospects table to select a specific company.
     * Loads the opportunity metric for the company's sector and fetches
     * client-level insight metrics (Yelp, website, census, etc.).
     *
     * @param vertical the client's industry vertical (e.g. "technology", "health-wellness")
     */
    public void selectCompany(String vertical) {
        // Reset only the opportunity metric (not company name/metrics — those are managed
        // by selectCompanyByName for the dialog's Client Insights section).
        this.selectedCompanyMetric = null;

        if (vertical == null || vertical.trim().isEmpty()) {
            return;
        }
        try {
            // Normalize the industry vertical to match sector_name in opportunity_metrics:
            // lowercase, strip anything that isn't a letter/digit/hyphen
            String normalized = vertical.trim().toLowerCase()
                    .replaceAll("[^a-z0-9-]", "")
                    .replaceAll("-+", "-")
                    .replaceAll("^-|-$", "");
            this.selectedCompanyVertical = normalized;
            log.debug("selectCompany: vertical='{}' normalized='{}'", vertical, normalized);

            // Look up opportunity metrics for this sector
            List<OpportunityMetric> metrics = opportunityMetricsRepository
                    .findBySectorNameOrderByOpportunityIndexDesc(normalized);
            if (metrics != null && !metrics.isEmpty()) {
                selectedCompanyMetric = metrics.get(0);
                log.debug("Selected company metric for sector '{}': opportunityIndex={}",
                        normalized, selectedCompanyMetric.getOpportunityIndex());
            } else {
                log.debug("No opportunity metrics found for sector '{}'", normalized);
            }
        } catch (Exception e) {
            log.warn("Failed to load metric for sector '{}': {}", vertical, e.getMessage());
        }
    }

    /**
     * Called from the client prospects table to select a specific company.
     * Stores the company name so client-level insight metrics can be loaded.
     *
     * @param companyName the client company name
     * @param vertical    the client's industry vertical
     */
    public void selectCompanyByName(String companyName, String vertical) {
        this.selectedCompanyName = companyName;
        this.selectedCompanyVertical = vertical;

        // Load opportunity metrics for the vertical
        selectCompany(vertical);

        // Load client-level insight metrics
        if (clientMetricsService != null) {
            try {
                this.selectedCompanyMetrics = clientMetricsService.getMetrics(companyName, vertical);
                log.debug("Loaded client metrics for company='{}' vertical='{}'", companyName, vertical);
            } catch (Exception e) {
                log.warn("Failed to load client metrics for company '{}': {}", companyName, e.getMessage());
            }
        }
    }

    /**
     * Clears the company-specific metric selection, reverting to the global top.
     */
    public void clearCompanySelection() {
        this.selectedCompanyMetric = null;
        this.selectedCompanyMetrics = null;
        this.selectedCompanyName = null;
        this.selectedCompanyVertical = null;
    }

    // ========================================================================
    //  Client-level insight metrics
    // ========================================================================

    /** Whether a company is currently selected (i.e. the dialog shows client data). */
    public boolean isCompanySelected() {
        return selectedCompanyName != null || selectedCompanyMetrics != null;
    }

    /** The currently selected company name, or null. */
    public String getSelectedCompanyName() {
        return selectedCompanyName;
    }

    /** The currently selected industry vertical, or null. */
    public String getSelectedCompanyVertical() {
        return selectedCompanyVertical;
    }

    /** Returns the client insight metrics for the selected company, or null. */
    public ClientInsightMetricsDTO getSelectedCompanyMetrics() {
        return selectedCompanyMetrics;
    }

    /**
     * Returns the display name for a given KPI key.
     */
    public String getKpiDisplayName(String kpiKey) {
        Map<String, KpiDef> defs = getKpiDefinitions();
        KpiDef def = defs.get(kpiKey);
        return def != null ? def.getDisplayName() : kpiKey;
    }

    /**
     * Returns all KPI keys and their definitions as a map, now with benchmark ranges.
     */
    public Map<String, KpiDef> getKpiDefinitions() {
        Map<String, KpiDef> defs = new HashMap<>();
        if (kpiTooltipBean == null) return defs;

        // Each entry: {key, displayName, formula, low, normal, high}
        String[][] kpis = {
            {"roas", "Return on Ad Spend (ROAS)", "Revenue \u00f7 Ad Spend", "1.5", "3.5", "6.0"},
            {"cac", "Customer Acquisition Cost (CAC)", "Total Acquisition Cost \u00f7 New Customers", "80", "40", "10"},
            {"cltv", "Customer Lifetime Value (CLTV)", "Avg Order Value \u00d7 Frequency \u00d7 Lifespan", "100", "400", "800"},
            {"conversionRate", "Conversion Rate (CR)", "Conversions \u00f7 Clicks \u00d7 100", "2%", "8%", "15%"},
            {"scalability", "Scalability ($ Ceiling)", "Forecasted from audience reach", "3", "6", "9"},
            {"attributionAccuracy", "Attribution Accuracy", "Correctly Attributed \u00f7 Total \u00d7 100", "40%", "65%", "90%"},
            {"contributionMargin", "Contribution Margin", "Gross Margin \u2013 Ad Spend", "0.8", "1.5", "3.0"},
            {"paybackPeriod", "Payback Period (months)", "CAC \u00f7 Monthly Revenue per Customer", "12", "6", "3"},
            {"incrementalReturn", "Incremental Return", "(Post-Campaign Revenue \u2013 Baseline) \u00f7 Spend", "1.0", "3.0", "6.0"},
            {"costPerQualifiedLead", "Cost Per Qualified Lead (CPQL)", "Ad Spend \u00f7 Qualified Leads", "$80", "$35", "$10"}
        };
        for (String[] kpi : kpis) {
            defs.put(kpi[0], new KpiDef(kpi[1], kpi[2], kpi[3], kpi[4], kpi[5]));
        }
        return defs;
    }

    /**
     * KPI keys in display order.
     */
    public List<String> getKpiKeys() {
        List<String> keys = new ArrayList<>();
        keys.add("roas");
        keys.add("cac");
        keys.add("cltv");
        keys.add("conversionRate");
        keys.add("scalability");
        keys.add("attributionAccuracy");
        keys.add("contributionMargin");
        keys.add("paybackPeriod");
        keys.add("incrementalReturn");
        keys.add("costPerQualifiedLead");
        return keys;
    }

    // ========================================================================
    //  Opportunity Metric Benchmark & Data Source Status
    // ========================================================================

    /**
     * Keys for the six opportunity metrics in display order.
     */
    public List<String> getOpportunityMetricKeys() {
        List<String> keys = new ArrayList<>();
        keys.add("marketDemand");
        keys.add("advertisingCompetition");
        keys.add("marketSaturation");
        keys.add("avgCpc");
        keys.add("searchGrowth");
        keys.add("opportunityIndex");
        return keys;
    }

    /**
     * Benchmark values for each opportunity metric: {low, normal, high}.
     */
    public String[] getMetricBenchmarks(String metricKey) {
        if (metricKey == null) return new String[]{"—", "—", "—"};
        switch (metricKey) {
            case "marketDemand":          return new String[]{"20", "50", "80"};
            case "advertisingCompetition": return new String[]{"70", "40", "10"};
            case "marketSaturation":      return new String[]{"60", "35", "10"};
            case "avgCpc":                return new String[]{"$4.00", "$2.00", "$0.50"};
            case "searchGrowth":          return new String[]{"-10%", "5%", "20%"};
            case "opportunityIndex":      return new String[]{"20", "50", "80"};
            default:                      return new String[]{"—", "—", "—"};
        }
    }

    /**
     * Returns the display name for an opportunity metric key.
     */
    public String getMetricDisplayName(String metricKey) {
        if (metricKey == null) return "";
        switch (metricKey) {
            case "marketDemand":          return "Market Demand Score";
            case "advertisingCompetition": return "Advertising Competition";
            case "marketSaturation":      return "Local Market Saturation";
            case "avgCpc":                return "Avg. Estimated CPC";
            case "searchGrowth":          return "Search Growth";
            case "opportunityIndex":      return "Opportunity Index";
            default:                      return metricKey;
        }
    }

    /**
     * Returns the numeric value of a metric from the top opportunity metric entity.
     */
    public double getMetricValue(String metricKey) {
        OpportunityMetric m = getTopOpportunityMetric();
        if (m == null) return 0;
        switch (metricKey) {
            case "marketDemand":          return m.getMarketDemandScore() != null ? m.getMarketDemandScore().doubleValue() : 0;
            case "advertisingCompetition": return m.getAdvertisingCompetitionScore() != null ? m.getAdvertisingCompetitionScore().doubleValue() : 0;
            case "marketSaturation":      return m.getLocalMarketSaturation() != null ? m.getLocalMarketSaturation().doubleValue() : 0;
            case "avgCpc":                return m.getAverageEstimatedCpc() != null ? m.getAverageEstimatedCpc().doubleValue() : 0;
            case "searchGrowth":          return m.getSearchGrowth() != null ? m.getSearchGrowth().doubleValue() : 0;
            case "opportunityIndex":      return m.getOpportunityIndex() != null ? m.getOpportunityIndex().doubleValue() : 0;
            default:                      return 0;
        }
    }

    /**
     * Returns the data-source status for a given opportunity metric:
     * "LIVE" — all contributing sources are live,
     * "MOCK" — all contributing sources are mock,
     * "LIVE+MOCK" — a mix of live and mock sources.
     */
    public String getMetricDataSourceStatus(String metricKey) {
        OpportunityMetric m = getTopOpportunityMetric();
        if (m == null || m.getDataSourcesUsed() == null || m.getDataSourcesUsed().isEmpty()) {
            return "MOCK";
        }

        String[] sourceNames = m.getDataSourcesUsed().split(",");
        boolean hasLive = false;
        boolean hasMock = false;

        for (String name : sourceNames) {
            String trimmed = name.trim().toLowerCase();
            boolean isLive = false;
            if (wrappers != null) {
                for (DataSourceStatusProvider w : wrappers) {
                    if (w.getSourceName().equalsIgnoreCase(trimmed) && w.isLive()) {
                        isLive = true;
                        break;
                    }
                }
            }
            if (isLive) {
                hasLive = true;
            } else {
                hasMock = true;
            }
        }

        if (hasLive && hasMock) return "LIVE+MOCK";
        if (hasLive) return "LIVE";
        return "MOCK";
    }

    /**
     * Returns a CSS class for the data-source status badge.
     */
    public String getDataSourceStatusClass(String status) {
        if (status == null) return "ds-mock";
        switch (status) {
            case "LIVE":      return "ds-live";
            case "LIVE+MOCK": return "ds-hybrid";
            default:          return "ds-mock";
        }
    }

    // ========================================================================
    //  Research Links (Data Source Section)
    // ========================================================================

    /**
     * Returns the list of research links for the current recommendation.
     * Each link is parameterized based on the recommendation's sector and platform.
     */
    public List<ResearchLink> getResearchLinks() {
        List<ResearchLink> links = new ArrayList<>();

        // Determine the search category and location from the Top Pick
        String category = resolveSearchCategory();
        String location = "New+York"; // Default location — could be made configurable

        // Yelp — business count for the sector's category
        String yelpUrl = "https://www.yelp.com/search?find_desc=" + category + "&find_loc=" + location;
        links.add(new ResearchLink("Yelp business count", yelpUrl,
                "Search Yelp for businesses in this category and location."));

        // Google Trends — search trend for the sector
        String trendsUrl = "https://trends.google.com/trends/explore?q=" + category;
        links.add(new ResearchLink("Search trend", trendsUrl,
                "Pre-populated with the sector keyword. Adjust query as needed."));

        // Google Ads Keyword Planner
        links.add(new ResearchLink("Search volume", "https://ads.google.com/home/tools/keyword-planner/",
                "Requires a Google Ads account. Use for precise search volume data."));

        // Meta Ads Library — competitor ads
        String metaUrl = "https://www.facebook.com/ads/library/?country=US";
        links.add(new ResearchLink("Competitor ads", metaUrl,
                "Search by company, keyword, or location to see active ad creatives."));

        // U.S. Census — population
        String censusUrl = "https://data.census.gov/";
        links.add(new ResearchLink("Population", censusUrl,
                "U.S. Census Bureau — population data for any region."));

        // U.S. Census — income
        links.add(new ResearchLink("Income", censusUrl,
                "Median household income and other demographic data from the Census."));

        // Business website — from Yelp if available, or a generic search
        String websiteUrl = "https://www.yelp.com/search?find_desc=" + category + "&find_loc=" + location;
        links.add(new ResearchLink("Business website", websiteUrl,
                "Find business websites via Yelp or other directory search."));

        // Google PageSpeed Insights
        String pagespeedUrl = "https://pagespeed.web.dev/";
        links.add(new ResearchLink("Website performance", pagespeedUrl,
                "Enter any public URL for PageSpeed analysis."));

        return links;
    }

    /**
     * Resolve a search category from the top opportunity's sector name.
     */
    private String resolveSearchCategory() {
        OpportunityMetric m = getTopOpportunityMetric();
        String sector = m != null && m.getSectorName() != null ? m.getSectorName().toLowerCase().trim() : "";
        switch (sector) {
            case "technology":       return "technology+companies";
            case "finance":          return "financial+services";
            case "manufacturing":    return "manufacturing";
            case "retail":           return "retail+stores";
            case "health-wellness":  return "health+wellness";
            case "travel":           return "travel+agencies";
            case "job-market":       return "recruitment+agencies";
            default:                 return "businesses";
        }
    }

    /**
     * Holds a single research link entry.
     */
    public static class ResearchLink implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String metricInput;
        private final String url;
        private final String notes;

        public ResearchLink(String metricInput, String url, String notes) {
            this.metricInput = metricInput;
            this.url = url;
            this.notes = notes;
        }

        public String getMetricInput() { return metricInput; }
        public String getUrl() { return url; }
        public String getNotes() { return notes; }
    }

    // ========== Internal helpers ==========

    /**
     * Look up the source names that contributed to the KPI for the given
     * platform and sector by querying the source attribution table.
     */
    private Set<String> lookupContributingSourceNames(Long platformId, Long sectorId) {
        Set<String> names = new HashSet<>();

        // Find the KPI metrics row
        Optional<KPIMetrics> kpiOpt = kpiMetricsRepository
                .findTopByPlatformIdAndSectorIdOrderByIngestionTimestampDesc(platformId, sectorId);
        if (!kpiOpt.isPresent()) {
            return names;
        }
        Long kpiId = kpiOpt.get().getId();

        // Get source attribution
        try {
            List<SourceMetadataDTO> sources = sourceAttributionService.getSourcesForKpi(kpiId);
            if (sources != null) {
                for (SourceMetadataDTO src : sources) {
                    String srcName = src.getSourceName();
                    if (srcName != null) {
                        names.add(srcName.toLowerCase().trim());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load source attribution for KPI {}: {}", kpiId, e.getMessage());
        }

        return names;
    }

    /**
     * All source reference links as a fallback.
     */
    private List<SourceRef> getAllSourceRefs() {
        List<SourceRef> refs = new ArrayList<>();
        String[][] allSources = {
            {"pytrends", "https://github.com/GeneralMills/pytrends"},
            {"yelp_fusion", "https://fusion.yelp.com/"},
            {"foursquare_places", "https://docs.foursquare.com/developer/reference/places-api"},
            {"skyscanner", "https://www.partners.skyscanner.net/"},
            {"bing_webmaster", "https://learn.microsoft.com/bingwebmaster/"},
            {"ebay", "https://developer.ebay.com/Devzone/finding/Concepts/FindingAPIGuide.html"},
            {"reddit", "https://www.reddit.com/dev/api/"},
            {"x_api", "https://developer.twitter.com/en/docs/twitter-api"},
            {"meta_ads_library", "https://www.facebook.com/ads/library/api/"},
            {"job_market", "https://developer.adzuna.com/"}
        };
        for (String[] s : allSources) {
            refs.add(new SourceRef(s[0], s[1]));
        }
        return refs;
    }

    /**
     * Returns the reference URL for a data source by name, or null.
     */
    private String getSourceUrl(String sourceName) {
        if (sourceName == null) return null;
        switch (sourceName.toLowerCase().trim()) {
            case "pytrends":       return "https://github.com/GeneralMills/pytrends";
            case "yelp_fusion":    return "https://fusion.yelp.com/";
            case "foursquare_places": return "https://docs.foursquare.com/developer/reference/places-api";
            case "skyscanner":     return "https://www.partners.skyscanner.net/";
            case "bing_webmaster": return "https://learn.microsoft.com/bingwebmaster/";
            case "ebay":           return "https://developer.ebay.com/Devzone/finding/Concepts/FindingAPIGuide.html";
            case "reddit":         return "https://www.reddit.com/dev/api/";
            case "x_api":          return "https://developer.twitter.com/en/docs/twitter-api";
            case "meta_ads_library": return "https://www.facebook.com/ads/library/api/";
            case "job_market":     return "https://developer.adzuna.com/";
            default:               return null;
        }
    }

    // ========== Inner types ==========

    /**
     * Holds a data source reference (name + URL).
     */
    public static class SourceRef implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String name;
        private final String url;
        public SourceRef(String name, String url) { this.name = name; this.url = url; }
        public String getName() { return name; }
        public String getUrl() { return url; }
    }

    /**
     * Holds a KPI definition (display name, formula, benchmark ranges).
     */
    public static class KpiDef implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String displayName;
        private final String formula;
        private final String low;
        private final String normal;
        private final String high;
        public KpiDef(String displayName, String formula, String low, String normal, String high) {
            this.displayName = displayName; this.formula = formula;
            this.low = low; this.normal = normal; this.high = high;
        }
        public String getDisplayName() { return displayName; }
        public String getFormula() { return formula; }
        public String getLow() { return low; }
        public String getNormal() { return normal; }
        public String getHigh() { return high; }
    }
}
