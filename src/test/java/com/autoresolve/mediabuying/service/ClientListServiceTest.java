package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.cache.CacheService;
import com.autoresolve.mediabuying.model.dto.ClientDTO;
import com.autoresolve.mediabuying.model.entity.Client;
import com.autoresolve.mediabuying.model.entity.CommerceSector;
import com.autoresolve.mediabuying.repository.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientListServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private CacheService cacheService;

    private ClientListService clientListService;

    @BeforeEach
    void setUp() {
        clientListService = new ClientListService(clientRepository, cacheService);
    }

    @Test
    void testGetClientsReturnsPaginatedResults() {
        // Arrange
        CommerceSector sector = CommerceSector.builder()
                .id(1L)
                .displayName("Technology")
                .build();

        Client client = Client.builder()
                .id(1L)
                .clientName("OmniTech Solutions")
                .sectorId(1L)
                .sector(sector)
                .contractType("RETAINER")
                .contractValue(BigDecimal.valueOf(45000))
                .contractStartDate(LocalDate.of(2025, 1, 15))
                .contractEndDate(LocalDate.of(2027, 12, 31))
                .outlookScore(92)
                .isActive(true)
                .build();

        Page<Client> clientPage = new PageImpl<>(Collections.singletonList(client));
        when(clientRepository.findByIsActiveTrue(any(Pageable.class))).thenReturn(clientPage);

        // Act
        Page<ClientDTO> result = clientListService.getClients(0, 10, "outlookScore", "desc");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());

        ClientDTO dto = result.getContent().get(0);
        assertEquals("OmniTech Solutions", dto.getClientName());
        assertEquals("Technology", dto.getSectorName());
        assertEquals("RETAINER", dto.getContractType());
        assertNotNull(dto.getContractDescription());
        assertTrue(dto.getContractDescription().contains("Retainer"));
        assertNotNull(dto.getRetentionDisplay());
        assertEquals(92, dto.getOutlookScore().intValue());

        // Verify cache was written
        verify(cacheService, times(1)).put(anyString(), any(), anyLong());
    }

    @Test
    void testGetClientsSortsBySectorName() {
        // Arrange
        Page<Client> emptyPage = new PageImpl<>(Collections.emptyList());
        when(clientRepository.findByIsActiveTrue(any(Pageable.class))).thenReturn(emptyPage);

        // Act
        Page<ClientDTO> result = clientListService.getClients(0, 10, "sectorName", "asc");

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void testGetClientsHandlesEmptyResult() {
        // Arrange
        Page<Client> emptyPage = new PageImpl<>(Collections.emptyList());
        when(clientRepository.findByIsActiveTrue(any(Pageable.class))).thenReturn(emptyPage);

        // Act
        Page<ClientDTO> result = clientListService.getClients(0, 10, "outlookScore", "desc");

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void testGetClientsReturnsFromCacheOnHit() {
        // Arrange - simulate cache hit
        ClientDTO cachedDto = ClientDTO.builder()
                .id(1L)
                .clientName("Cached Client")
                .sectorName("Technology")
                .outlookScore(85)
                .build();
        Page<ClientDTO> cachedPage = new PageImpl<>(Collections.singletonList(cachedDto));
        when(cacheService.get(anyString())).thenReturn(cachedPage);

        // Act
        Page<ClientDTO> result = clientListService.getClients(0, 10, "outlookScore", "desc");

        // Assert
        assertNotNull(result);
        assertEquals("Cached Client", result.getContent().get(0).getClientName());

        // Verify repository was never called (cache hit)
        verify(clientRepository, never()).findByIsActiveTrue(any(Pageable.class));
    }

    @Test
    void testGetClientsHandlesNullSectorGracefully() {
        // Arrange
        Client client = Client.builder()
                .id(1L)
                .clientName("No Sector Client")
                .sectorId(null)
                .sector(null)
                .contractType("HYBRID")
                .outlookScore(70)
                .isActive(true)
                .build();

        Page<Client> clientPage = new PageImpl<>(Collections.singletonList(client));
        when(clientRepository.findByIsActiveTrue(any(Pageable.class))).thenReturn(clientPage);

        // Act
        Page<ClientDTO> result = clientListService.getClients(0, 10, "outlookScore", "desc");

        // Assert
        assertNotNull(result);
        assertEquals("Unknown", result.getContent().get(0).getSectorName());
    }
}
