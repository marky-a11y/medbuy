package com.autoresolve.mediabuying.repository;

import com.autoresolve.mediabuying.model.entity.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Page<Client> findByIsActiveTrue(Pageable pageable);

    List<Client> findBySectorIdAndIsActiveTrue(Long sectorId);

    long countByIsActiveTrue();

    List<Client> findByIsActiveTrue();
}
