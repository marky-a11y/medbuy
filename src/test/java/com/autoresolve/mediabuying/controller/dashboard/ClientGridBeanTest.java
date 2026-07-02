package com.autoresolve.mediabuying.controller.dashboard;

import com.autoresolve.mediabuying.model.dto.ClientDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ClientGridBeanTest {

    private ClientGridBean clientGridBean;

    @BeforeEach
    void setUp() {
        clientGridBean = new ClientGridBean();
    }

    @Test
    void testGetOutlookCssClassHigh() {
        assertEquals("outlook-high", clientGridBean.getOutlookCssClass(70));
        assertEquals("outlook-high", clientGridBean.getOutlookCssClass(85));
        assertEquals("outlook-high", clientGridBean.getOutlookCssClass(100));
    }

    @Test
    void testGetOutlookCssClassMedium() {
        assertEquals("outlook-medium", clientGridBean.getOutlookCssClass(40));
        assertEquals("outlook-medium", clientGridBean.getOutlookCssClass(55));
        assertEquals("outlook-medium", clientGridBean.getOutlookCssClass(69));
    }

    @Test
    void testGetOutlookCssClassLow() {
        assertEquals("outlook-low", clientGridBean.getOutlookCssClass(0));
        assertEquals("outlook-low", clientGridBean.getOutlookCssClass(25));
        assertEquals("outlook-low", clientGridBean.getOutlookCssClass(39));
    }

    @Test
    void testGetContractBadgeClass() {
        assertEquals("contract-retainer", clientGridBean.getContractBadgeClass("RETAINER"));
        assertEquals("contract-performance", clientGridBean.getContractBadgeClass("PERFORMANCE"));
        assertEquals("contract-hybrid", clientGridBean.getContractBadgeClass("HYBRID"));
        assertEquals("contract-hybrid", clientGridBean.getContractBadgeClass(null));
        assertEquals("contract-hybrid", clientGridBean.getContractBadgeClass("UNKNOWN"));
    }

    @Test
    void testIsExpiringSoonReturnsTrueWhenUnder3Months() {
        ClientDTO dto = ClientDTO.builder()
                .id(1L)
                .retentionPeriodMonths(2L)
                .build();
        assertTrue(clientGridBean.isExpiringSoon(dto));
    }

    @Test
    void testIsExpiringSoonReturnsFalseWhenOver3Months() {
        ClientDTO dto = ClientDTO.builder()
                .id(1L)
                .retentionPeriodMonths(12L)
                .build();
        assertFalse(clientGridBean.isExpiringSoon(dto));
    }

    @Test
    void testIsExpiringSoonReturnsFalseForNull() {
        assertFalse(clientGridBean.isExpiringSoon(null));

        ClientDTO dto = ClientDTO.builder()
                .id(1L)
                .retentionPeriodMonths(null)
                .build();
        assertFalse(clientGridBean.isExpiringSoon(dto));
    }
}
