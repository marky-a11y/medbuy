package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.cache.CacheKeys;
import com.autoresolve.mediabuying.cache.CacheService;
import com.autoresolve.mediabuying.model.dto.ClientDTO;
import com.autoresolve.mediabuying.model.entity.Client;
import com.autoresolve.mediabuying.repository.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Service for retrieving paginated, sorted client data with caching.
 */
@Service
public class ClientListService {

    private static final Logger log = LoggerFactory.getLogger(ClientListService.class);

    private static final long CACHE_TTL_MILLIS = 5 * 60 * 1000L;

    private final ClientRepository clientRepository;
    private final CacheService cacheService;

    public ClientListService(ClientRepository clientRepository,
                              CacheService cacheService) {
        this.clientRepository = clientRepository;
        this.cacheService = cacheService;
    }

    /**
     * Returns a paginated, sorted page of active clients as ClientDTOs.
     * Results are cached with a 5-minute TTL.
     *
     * @param page      zero-based page index
     * @param size      page size
     * @param sortField field to sort by (clientName, sectorName, contractType, retentionPeriod, outlookScore)
     * @param sortDir   sort direction (asc or desc)
     * @return Page of ClientDTOs
     */
    @SuppressWarnings("unchecked")
    public Page<ClientDTO> getClients(int page, int size, String sortField, String sortDir) {
        String cacheKey = CacheKeys.clientsListKey(page, size, sortField, sortDir);

        // Try cache first
        try {
            Page<ClientDTO> cached = cacheService.get(cacheKey);
            if (cached != null) {
                log.debug("Cache hit for clients list: key={}", cacheKey);
                return cached;
            }
        } catch (Exception e) {
            log.warn("Cache read failed for key={}: {}", cacheKey, e.getMessage());
        }

        log.debug("Cache miss for clients list: key={}", cacheKey);

        // Map sort field to database column or JPA property path
        String sortProperty = mapSortField(sortField);
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortProperty));

        // Query database
        Page<Client> clientPage = clientRepository.findByIsActiveTrue(pageable);

        // Convert to DTOs
        Page<ClientDTO> dtoPage;
        if (clientPage.hasContent()) {
            dtoPage = new PageImpl<>(
                    clientPage.getContent().stream()
                            .map(c -> ClientDTO.from(c,
                                    c.getSector() != null ? c.getSector().getDisplayName() : "Unknown"))
                            .collect(Collectors.toList()),
                    pageable,
                    clientPage.getTotalElements()
            );
        } else {
            dtoPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        // Store in cache
        try {
            cacheService.put(cacheKey, dtoPage, CACHE_TTL_MILLIS);
        } catch (Exception e) {
            log.warn("Cache write failed for key={}: {}", cacheKey, e.getMessage());
        }

        return dtoPage;
    }

    /**
     * Maps frontend sort field names to JPA property paths.
     */
    private String mapSortField(String sortField) {
        if (sortField == null) return "outlookScore";
        switch (sortField) {
            case "clientName":       return "clientName";
            case "sectorName":       return "sector.displayName";
            case "contractType":     return "contractType";
            case "retentionPeriod":
            case "retentionPeriodMonths": return "contractEndDate";
            case "outlookScore":     return "outlookScore";
            default:                 return "outlookScore";
        }
    }
}
