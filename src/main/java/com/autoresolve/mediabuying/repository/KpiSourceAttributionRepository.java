package com.autoresolve.mediabuying.repository;

import com.autoresolve.mediabuying.model.entity.KpiSourceAttribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Repository
public interface KpiSourceAttributionRepository extends JpaRepository<KpiSourceAttribution, Long> {

    List<KpiSourceAttribution> findByKpiMetricsId(Long kpiMetricsId);

    @Query("SELECT ksa.kpiMetricsId, ds.sourceName FROM KpiSourceAttribution ksa " +
           "JOIN DataSource ds ON ksa.dataSourceId = ds.id " +
           "WHERE ksa.kpiMetricsId IN :kpiIds")
    List<Object[]> findPrimarySourceNamesByKpiIds(@Param("kpiIds") Set<Long> kpiIds);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO media_buying.kpi_source_attribution (kpi_metrics_id, data_source_id, attribution_context) " +
            "VALUES (:kpiMetricsId, :dataSourceId, :context) " +
            "ON CONFLICT (kpi_metrics_id, data_source_id) DO NOTHING", nativeQuery = true)
    void upsert(@Param("kpiMetricsId") Long kpiMetricsId,
                @Param("dataSourceId") Long dataSourceId,
                @Param("context") String context);
}
