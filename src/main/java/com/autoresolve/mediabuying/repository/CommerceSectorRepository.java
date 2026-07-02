package com.autoresolve.mediabuying.repository;

import com.autoresolve.mediabuying.model.entity.CommerceSector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommerceSectorRepository extends JpaRepository<CommerceSector, Long> {

    Optional<CommerceSector> findByName(String name);

    List<CommerceSector> findByIsActiveTrue();
}
