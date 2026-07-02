package com.autoresolve.mediabuying.repository;

import com.autoresolve.mediabuying.model.entity.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface DataSourceRepository extends JpaRepository<DataSource, Long> {

    Optional<DataSource> findBySourceName(String sourceName);

    @Query("SELECT d FROM DataSource d WHERE d.lastVerifiedAt IS NULL OR d.lastVerifiedAt < :threshold")
    List<DataSource> findStaleSources(@Param("threshold") Instant threshold);
}
