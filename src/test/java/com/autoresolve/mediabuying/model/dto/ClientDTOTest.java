package com.autoresolve.mediabuying.model.dto;

import com.autoresolve.mediabuying.model.entity.Client;
import com.autoresolve.mediabuying.model.entity.CommerceSector;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ClientDTOTest {

    @Test
    void testFromFactoryWithValidEntity() {
        // Arrange
        CommerceSector sector = CommerceSector.builder()
                .id(1L)
                .displayName("Finance")
                .build();

        Client client = Client.builder()
                .id(42L)
                .clientName("FinCore Partners")
                .sectorId(2L)
                .sector(sector)
                .contractType("RETAINER")
                .contractValue(BigDecimal.valueOf(62000))
                .contractStartDate(LocalDate.of(2025, 1, 1))
                .contractEndDate(LocalDate.of(2027, 6, 30))
                .outlookScore(90)
                .isActive(true)
                .build();

        // Act
        ClientDTO dto = ClientDTO.from(client, "Finance");

        // Assert
        assertEquals(42L, dto.getId().longValue());
        assertEquals("FinCore Partners", dto.getClientName());
        assertEquals("Finance", dto.getSectorName());
        assertEquals("RETAINER", dto.getContractType());
        assertTrue(dto.getContractDescription().contains("Retainer"));
        assertTrue(dto.getContractDescription().contains("$62K/mo"));
        assertNotNull(dto.getRetentionPeriodMonths());
        assertTrue(dto.getRetentionDisplay().contains("months (ends"));
        assertEquals(90, dto.getOutlookScore().intValue());
        assertFalse(dto.isStale());
    }

    @Test
    void testFromFactoryWithNullContractValue() {
        // Arrange
        Client client = Client.builder()
                .id(1L)
                .clientName("Test Client")
                .contractType("PERFORMANCE")
                .contractValue(null)
                .outlookScore(50)
                .isActive(true)
                .build();

        // Act
        ClientDTO dto = ClientDTO.from(client, "Technology");

        // Assert
        assertEquals("Performance", dto.getContractDescription());
    }

    @Test
    void testIsStaleReturnsTrueForExpiredContract() {
        // Arrange
        Client client = Client.builder()
                .id(1L)
                .clientName("Expired Client")
                .contractEndDate(LocalDate.of(2020, 1, 1))
                .outlookScore(30)
                .isActive(true)
                .build();

        // Act
        ClientDTO dto = ClientDTO.from(client, "Retail");

        // Assert
        assertTrue(dto.isStale());
        assertTrue(dto.getRetentionDisplay().contains("Expired"));
    }

    @Test
    void testFormatContractWithSmallValue() {
        // Arrange
        Client client = Client.builder()
                .id(1L)
                .clientName("Small Client")
                .contractType("HYBRID")
                .contractValue(BigDecimal.valueOf(500))
                .outlookScore(60)
                .isActive(true)
                .build();

        // Act
        ClientDTO dto = ClientDTO.from(client, "Manufacturing");

        // Assert
        assertTrue(dto.getContractDescription().contains("$500/mo"));
    }
}
