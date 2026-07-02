package com.autoresolve.mediabuying.repository;

import com.autoresolve.mediabuying.model.entity.ScoringWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScoringWeightRepository extends JpaRepository<ScoringWeight, Long> {

    List<ScoringWeight> findAll();
}
