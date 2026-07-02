package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.cache.CacheKeys;
import com.autoresolve.mediabuying.cache.CacheService;
import com.autoresolve.mediabuying.model.dto.PlatformDTO;
import com.autoresolve.mediabuying.model.dto.SectorDTO;
import com.autoresolve.mediabuying.model.entity.CommerceSector;
import com.autoresolve.mediabuying.model.entity.Platform;
import com.autoresolve.mediabuying.repository.CommerceSectorRepository;
import com.autoresolve.mediabuying.repository.PlatformRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PlatformSectorService {

    private static final Logger log = LoggerFactory.getLogger(PlatformSectorService.class);

    private final PlatformRepository platformRepository;
    private final CommerceSectorRepository sectorRepository;
    private final CacheService cacheService;

    public PlatformSectorService(PlatformRepository platformRepository,
                                  CommerceSectorRepository sectorRepository,
                                  CacheService cacheService) {
        this.platformRepository = platformRepository;
        this.sectorRepository = sectorRepository;
        this.cacheService = cacheService;
    }

    @SuppressWarnings("unchecked")
    public List<PlatformDTO> getActivePlatforms() {
        String cacheKey = CacheKeys.PLATFORMS_LIST;

        List<PlatformDTO> cached = cacheService.get(cacheKey);
        if (cached != null) {
            log.debug("Platforms list served from cache: key={}", cacheKey);
            return cached;
        }

        List<Platform> platforms = platformRepository.findByIsActiveTrue();
        List<PlatformDTO> dtoList = platforms.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        cacheService.put(cacheKey, dtoList, 15 * 60 * 1000L);
        return dtoList;
    }

    public List<SectorDTO> getSectorsForPlatform(Long platformId) {
        List<CommerceSector> sectors = sectorRepository.findByIsActiveTrue();
        return sectors.stream()
                .map(this::convertToSectorDTO)
                .collect(Collectors.toList());
    }

    public List<PlatformDTO> filterBySector(List<PlatformDTO> platforms, Long sectorId) {
        if (sectorId == null) {
            return platforms;
        }
        // In a full implementation, this would filter platforms that have data for the given sector.
        // For MVP, return the same list with sectors filter applied.
        return platforms.stream()
                .peek(p -> {
                    if (p.getSectors() != null) {
                        p.setSectors(p.getSectors().stream()
                                .filter(s -> s.getId().equals(sectorId))
                                .collect(Collectors.toList()));
                    }
                })
                .collect(Collectors.toList());
    }

    public List<CommerceSector> getAllSectors() {
        return sectorRepository.findByIsActiveTrue();
    }

    private PlatformDTO convertToDTO(Platform platform) {
        return PlatformDTO.builder()
                .id(platform.getId())
                .name(platform.getName())
                .displayName(platform.getDisplayName())
                .logoUrl(platform.getLogoUrl())
                .isActive(platform.getIsActive())
                .sectors(Collections.emptyList())
                .build();
    }

    /**
     * Look up a Platform entity by its ID.
     */
    public Platform getPlatformById(Long id) {
        return platformRepository.findById(id).orElse(null);
    }

    /**
     * Look up a CommerceSector entity by its ID.
     */
    public CommerceSector getSectorById(Long id) {
        return sectorRepository.findById(id).orElse(null);
    }

    private SectorDTO convertToSectorDTO(CommerceSector sector) {
        return SectorDTO.builder()
                .id(sector.getId())
                .name(sector.getName())
                .displayName(sector.getDisplayName())
                .isActive(sector.getIsActive())
                .build();
    }
}
