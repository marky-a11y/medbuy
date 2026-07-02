package com.autoresolve.mediabuying.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "kpi_source_attribution", schema = "media_buying")
public class KpiSourceAttribution implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kpi_metrics_id", nullable = false)
    private Long kpiMetricsId;

    @Column(name = "data_source_id", nullable = false)
    private Long dataSourceId;

    @Column(name = "attribution_context", nullable = false, length = 50)
    private String attributionContext;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
