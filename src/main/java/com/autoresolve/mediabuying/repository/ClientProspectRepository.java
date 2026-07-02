package com.autoresolve.mediabuying.repository;

import com.autoresolve.mediabuying.model.entity.ClientProspect;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClientProspectRepository extends JpaRepository<ClientProspect, Long> {

    List<ClientProspect> findTop5BySectorIdAndIsActiveTrueOrderByEstAdBudgetDesc(Long sectorId);

    List<ClientProspect> findTop5BySectorIdAndIsActiveTrueOrderByEstAnnualRevenueDesc(Long sectorId);

    List<ClientProspect> findTop5BySectorIdAndIsActiveTrueOrderByYoyGrowthRateDesc(Long sectorId);

    List<ClientProspect> findBySectorIdAndIsActiveTrue(Long sectorId);
}
