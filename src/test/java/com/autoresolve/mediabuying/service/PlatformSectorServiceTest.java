package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.cache.CacheKeys;
import com.autoresolve.mediabuying.cache.CacheService;
import com.autoresolve.mediabuying.model.dto.PlatformDTO;
import com.autoresolve.mediabuying.model.dto.SectorDTO;
import com.autoresolve.mediabuying.model.entity.CommerceSector;
import com.autoresolve.mediabuying.model.entity.Platform;
import com.autoresolve.mediabuying.repository.CommerceSectorRepository;
import com.autoresolve.mediabuying.repository.PlatformRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformSectorServiceTest {

    @Mock
    private PlatformRepository platformRepository;

    @Mock
    private CommerceSectorRepository sectorRepository;

    @Mock
    private CacheService cacheService;

    private PlatformSectorService platformSectorService;

    @BeforeEach
    void setUp() {
        platformSectorService = new PlatformSectorService(
                platformRepository, sectorRepository, cacheService);
    }

    @Test
    void testGetActivePlatformsReturnsDTOs() {
        List<Platform> platforms = Arrays.asList(
                Platform.builder().id(1L).name("google_ads").displayName("Google Ads").isActive(true).build(),
                Platform.builder().id(2L).name("meta_ads").displayName("Meta Ads").isActive(true).build()
        );

        // Mock cache miss (null return from cache)
        when(cacheService.get(CacheKeys.PLATFORMS_LIST)).thenReturn(null);

        when(platformRepository.findByIsActiveTrue()).thenReturn(platforms);

        List<PlatformDTO> result = platformSectorService.getActivePlatforms();

        assertEquals(2, result.size());
        assertEquals("Google Ads", result.get(0).getDisplayName());
        assertEquals("Meta Ads", result.get(1).getDisplayName());
    }

    @Test
    void testGetAllSectors() {
        List<CommerceSector> sectors = Arrays.asList(
                CommerceSector.builder().id(1L).name("technology").displayName("Technology").isActive(true).build(),
                CommerceSector.builder().id(2L).name("finance").displayName("Finance").isActive(true).build()
        );

        when(sectorRepository.findByIsActiveTrue()).thenReturn(sectors);

        List<CommerceSector> result = platformSectorService.getAllSectors();

        assertEquals(2, result.size());
        assertEquals("Technology", result.get(0).getDisplayName());
    }
}
