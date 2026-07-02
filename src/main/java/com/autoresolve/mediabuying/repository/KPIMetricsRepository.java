package com.autoresolve.mediabuying.repository;

import com.autoresolve.mediabuying.model.entity.KPIMetrics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface KPIMetricsRepository extends JpaRepository<KPIMetrics, Long> {

    Page<KPIMetrics> findByPlatformIdAndSectorId(Long platformId, Long sectorId, Pageable pageable);

    List<KPIMetrics> findAll();

    Optional<KPIMetrics> findTopByPlatformIdAndSectorIdOrderByIngestionTimestampDesc(Long platformId, Long sectorId);

    @Modifying
    @Transactional
    @Query(value = "MERGE INTO media_buying.kpi_metrics (" +
            " platform_id, sector_id, roas, cac, cltv, conversion_rate," +
            " scalability, attribution_accuracy, contribution_margin," +
            " payback_period, incremental_return, cost_per_qualified_lead," +
            " cash_conversion_cycle, saturation_point, ingestion_timestamp, data_source" +
            " ) KEY (platform_id, sector_id) VALUES (" +
            " :#{#m.platformId}, :#{#m.sectorId}, :#{#m.roas}, :#{#m.cac}," +
            " :#{#m.cltv}, :#{#m.conversionRate}, :#{#m.scalability}," +
            " :#{#m.attributionAccuracy}, :#{#m.contributionMargin}," +
            " :#{#m.paybackPeriod}, :#{#m.incrementalReturn}," +
            " :#{#m.costPerQualifiedLead}, :#{#m.cashConversionCycle}," +
            " :#{#m.saturationPoint}, :#{#m.ingestionTimestamp}, :#{#m.dataSource}" +
            " )", nativeQuery = true)
    void upsert(@Param("m") KPIMetrics m);
}
