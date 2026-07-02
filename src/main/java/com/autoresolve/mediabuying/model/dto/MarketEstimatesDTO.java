package com.autoresolve.mediabuying.model.dto;

/**
 * Market benchmark estimates to pre-populate the ROI calculator form
 * based on the selected platform, sector, business type, and city.
 */
public class MarketEstimatesDTO {

    private Double estimatedCpc;
    private Double estimatedCtr;
    private Double estimatedClickToLeadRate;
    private Double estimatedLeadToCustomerRate;
    private Double estimatedMonthlyRevenuePerCustomer;
    private Integer estimatedRetentionMonths;
    private Double estimatedGrossMargin;
    private String dataSourceDescription;

    public MarketEstimatesDTO() {}

    public MarketEstimatesDTO(Double cpc, Double ctr, Double clickToLead, Double leadToCustomer,
                              Double monthlyRevenue, Integer retention, Double grossMargin,
                              String dataSourceDesc) {
        this.estimatedCpc = cpc;
        this.estimatedCtr = ctr;
        this.estimatedClickToLeadRate = clickToLead;
        this.estimatedLeadToCustomerRate = leadToCustomer;
        this.estimatedMonthlyRevenuePerCustomer = monthlyRevenue;
        this.estimatedRetentionMonths = retention;
        this.estimatedGrossMargin = grossMargin;
        this.dataSourceDescription = dataSourceDesc;
    }

    public Double getEstimatedCpc() { return estimatedCpc; }
    public void setEstimatedCpc(Double v) { this.estimatedCpc = v; }

    public Double getEstimatedCtr() { return estimatedCtr; }
    public void setEstimatedCtr(Double v) { this.estimatedCtr = v; }

    public Double getEstimatedClickToLeadRate() { return estimatedClickToLeadRate; }
    public void setEstimatedClickToLeadRate(Double v) { this.estimatedClickToLeadRate = v; }

    public Double getEstimatedLeadToCustomerRate() { return estimatedLeadToCustomerRate; }
    public void setEstimatedLeadToCustomerRate(Double v) { this.estimatedLeadToCustomerRate = v; }

    public Double getEstimatedMonthlyRevenuePerCustomer() { return estimatedMonthlyRevenuePerCustomer; }
    public void setEstimatedMonthlyRevenuePerCustomer(Double v) { this.estimatedMonthlyRevenuePerCustomer = v; }

    public Integer getEstimatedRetentionMonths() { return estimatedRetentionMonths; }
    public void setEstimatedRetentionMonths(Integer v) { this.estimatedRetentionMonths = v; }

    public Double getEstimatedGrossMargin() { return estimatedGrossMargin; }
    public void setEstimatedGrossMargin(Double v) { this.estimatedGrossMargin = v; }

    public String getDataSourceDescription() { return dataSourceDescription; }
    public void setDataSourceDescription(String v) { this.dataSourceDescription = v; }
}
