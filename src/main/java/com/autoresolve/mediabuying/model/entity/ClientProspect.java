package com.autoresolve.mediabuying.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "client_prospects", schema = "media_buying")
public class ClientProspect implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sector_id", nullable = false, insertable = false, updatable = false)
    private Long sectorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sector_id", insertable = false, updatable = false)
    private CommerceSector sector;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(name = "est_annual_revenue", nullable = false, precision = 15, scale = 2)
    private BigDecimal estAnnualRevenue;

    @Column(name = "yoy_growth_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal yoyGrowthRate;

    @Column(name = "est_ad_budget", nullable = false, precision = 15, scale = 2)
    private BigDecimal estAdBudget;

    @Column(name = "industry_vertical", length = 100)
    private String industryVertical;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "last_updated")
    private Instant lastUpdated;

    @PrePersist
    protected void onCreate() {
        lastUpdated = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdated = Instant.now();
    }
}
