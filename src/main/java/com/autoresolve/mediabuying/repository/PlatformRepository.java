package com.autoresolve.mediabuying.repository;

import com.autoresolve.mediabuying.model.entity.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformRepository extends JpaRepository<Platform, Long> {

    Optional<Platform> findByName(String name);

    List<Platform> findByIsActiveTrue();
}
