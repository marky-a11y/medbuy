package com.autoresolve.mediabuying.controller.dashboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Application-scoped bean providing KPI tooltip definitions.
 * Loads descriptions from a static map for the 13 KPI columns.
 */
@Component
public class KpiTooltipBean implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(KpiTooltipBean.class);
    private static final long serialVersionUID = 1L;

    private Map<String, KpiDefinition> kpiDefinitions;

    @PostConstruct
    public void init() {
        kpiDefinitions = new HashMap<>();

        kpiDefinitions.put("roas", new KpiDefinition(
                "Return on Ad Spend (ROAS)",
                "Revenue generated per dollar of ad spend. Calculated as Revenue \u00f7 Ad Spend.",
                "Revenue \u00f7 Ad Spend"));
        kpiDefinitions.put("cac", new KpiDefinition(
                "Customer Acquisition Cost (CAC)",
                "Average cost to acquire a new customer. Includes ad spend and associated overhead.",
                "Total Acquisition Cost \u00f7 Number of New Customers"));
        kpiDefinitions.put("cltv", new KpiDefinition(
                "Customer Lifetime Value (CLTV)",
                "Predicted net profit attributed to the entire future relationship with a customer.",
                "Avg Order Value \u00d7 Purchase Frequency \u00d7 Customer Lifespan"));
        kpiDefinitions.put("conversionRate", new KpiDefinition(
                "Conversion Rate (CR)",
                "Percentage of users who completed a desired action.",
                "Conversions \u00f7 Clicks \u00d7 100"));
        kpiDefinitions.put("contributionMargin", new KpiDefinition(
                "Contribution Margin After Advertising",
                "Gross margin remaining after deducting advertising costs.",
                "Gross Margin \u2013 Ad Spend"));
        kpiDefinitions.put("paybackPeriod", new KpiDefinition(
                "Payback Period",
                "Time required to recover the cost of acquiring a customer.",
                "CAC \u00f7 (Monthly Revenue per Customer)"));
        kpiDefinitions.put("incrementalReturn", new KpiDefinition(
                "Incremental Return",
                "Additional revenue beyond baseline, divided by ad spend.",
                "(Revenue After Campaign \u2013 Baseline) \u00f7 Ad Spend"));
        kpiDefinitions.put("costPerQualifiedLead", new KpiDefinition(
                "Cost Per Qualified Lead (CPQL)",
                "Average cost to generate a qualified lead.",
                "Ad Spend \u00f7 Qualified Leads"));
        kpiDefinitions.put("scalability", new KpiDefinition(
                "Scalability ($ Ceiling)",
                "Maximum incremental spend capacity before diminishing returns.",
                "Forecasted from audience reach and platform caps"));
        kpiDefinitions.put("cashConversionCycle", new KpiDefinition(
                "Cash Conversion Cycle (Days)",
                "Days between spending on acquisition and receiving revenue.",
                "Days Inventory + Days Receivable \u2013 Days Payable"));
        kpiDefinitions.put("saturationPoint", new KpiDefinition(
                "Saturation Point",
                "Percentage of total addressable market reached.",
                "Current Reach \u00f7 TAM \u00d7 100"));
        kpiDefinitions.put("attributionAccuracy", new KpiDefinition(
                "Attribution Accuracy",
                "Percentage of conversions correctly matched to touch-points.",
                "Correctly Attributed \u00f7 Total Conversions \u00d7 100"));

        log.info("Loaded {} KPI tooltip definitions", kpiDefinitions.size());
    }

    /**
     * Returns an HTML-formatted tooltip string for a given KPI name.
     */
    public String getTooltip(String kpiName) {
        if (kpiName == null) return "";
        KpiDefinition def = kpiDefinitions.get(kpiName);
        if (def == null) return "";
        return def.toHtml();
    }

    /**
     * Returns the description for a given KPI name.
     */
    public String getDescription(String kpiName) {
        if (kpiName == null) return "";
        KpiDefinition def = kpiDefinitions.get(kpiName);
        return def != null ? def.description : "";
    }

    // --- Inner class for KPI definitions ---

    public static class KpiDefinition implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String displayName;
        private final String description;
        private final String formula;

        public KpiDefinition(String displayName, String description, String formula) {
            this.displayName = displayName;
            this.description = description;
            this.formula = formula;
        }

        public String toHtml() {
            return "<b>" + displayName + "</b><br/>"
                    + description + "<br/><br/>"
                    + "<b>Formula:</b> " + formula;
        }
    }
}
