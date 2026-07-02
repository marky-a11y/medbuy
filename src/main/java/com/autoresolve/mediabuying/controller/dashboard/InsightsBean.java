package com.autoresolve.mediabuying.controller.dashboard;

import com.autoresolve.mediabuying.model.dto.InsightDTO;
import com.autoresolve.mediabuying.service.InsightsEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * View-scoped managed bean for the AI-Powered Insights panel on the dashboard.
 * Loads LLM-generated client gap/opportunity/risk insights from {@link InsightsEngine}.
 */
@Component
@Scope("view")
public class InsightsBean implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(InsightsBean.class);
    private static final long serialVersionUID = 1L;

    @Autowired
    private transient InsightsEngine insightsEngine;

    private List<InsightDTO> insights = Collections.emptyList();
    private String errorMessage;

    public InsightsBean() {
    }

    @PostConstruct
    public void init() {
        loadInsights();
    }

    /**
     * Loads insights from the engine, capturing any errors.
     */
    private void loadInsights() {
        try {
            log.debug("Loading AI-powered insights");
            insights = insightsEngine.getClientGapInsights();
            errorMessage = null;
            log.debug("Loaded {} insights", insights.size());
        } catch (Exception e) {
            log.error("Failed to load insights: {}", e.getMessage(), e);
            insights = Collections.emptyList();
            errorMessage = "Unable to load AI-powered insights: " + e.getMessage();
        }
    }

    /**
     * Returns the list of insights (may be empty).
     */
    public List<InsightDTO> getInsights() {
        return insights;
    }

    /**
     * Returns the error message if loading failed, or null.
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Returns true if insights were loaded successfully and the list is non-empty.
     */
    public boolean hasInsights() {
        return insights != null && !insights.isEmpty();
    }

    /**
     * Retry loading insights (e.g., from a UI button after an error).
     */
    public void retry() {
        log.debug("Retrying insight load");
        loadInsights();
    }

    /**
     * Returns the appropriate CSS class for an insight card based on its type.
     *
     * @param insight the insight DTO
     * @return CSS class name
     */
    public String getInsightCssClass(InsightDTO insight) {
        if (insight == null || insight.getInsightType() == null) {
            return "insight-card";
        }
        switch (insight.getInsightType().toUpperCase()) {
            case "GAP":
                return "insight-card-gap";
            case "OPPORTUNITY":
                return "insight-card-opportunity";
            case "RISK":
                return "insight-card-risk";
            default:
                return "insight-card";
        }
    }
}
