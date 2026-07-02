package com.autoresolve.mediabuying.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Represents a single AI-generated insight about client portfolio gaps,
 * opportunities, or risks within a commerce sector.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsightDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The commerce sector this insight applies to (e.g. "Technology", "Finance"). */
    private String sectorName;

    /** One of: "GAP", "OPPORTUNITY", "RISK". */
    private String insightType;

    /** Short, human-readable headline for the insight. */
    private String headline;

    /** Longer detail / explanation of the insight. */
    private String detail;

    /** Confidence score between 0.0 and 1.0. */
    private Double confidenceScore;
}
