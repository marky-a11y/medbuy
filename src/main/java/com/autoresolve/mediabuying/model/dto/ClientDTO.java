package com.autoresolve.mediabuying.model.dto;

import com.autoresolve.mediabuying.model.entity.Client;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * DTO for displaying client information in the Client Portfolio Grid.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientDTO {

    private Long id;
    private String clientName;
    private String sectorName;
    private String contractType;
    private String contractDescription;
    private Long retentionPeriodMonths;
    private String retentionDisplay;
    private Integer outlookScore;
    private LocalDate contractEndDate;

    /**
     * Creates a ClientDTO from a Client entity and sector display name.
     */
    public static ClientDTO from(Client entity, String sectorDisplayName) {
        return ClientDTO.builder()
                .id(entity.getId())
                .clientName(entity.getClientName())
                .sectorName(sectorDisplayName)
                .contractType(entity.getContractType())
                .contractDescription(formatContract(entity))
                .retentionPeriodMonths(computeRetentionMonths(entity.getContractEndDate()))
                .retentionDisplay(formatRetention(entity))
                .outlookScore(entity.getOutlookScore())
                .contractEndDate(entity.getContractEndDate())
                .build();
    }

    /**
     * Formats the contract description: "Retainer — $45K/mo"
     */
    private static String formatContract(Client c) {
        String type = c.getContractType() != null
                ? c.getContractType().substring(0, 1).toUpperCase()
                    + c.getContractType().substring(1).toLowerCase()
                : "Unknown";

        if (c.getContractValue() != null) {
            BigDecimal val = c.getContractValue();
            if (val.compareTo(BigDecimal.valueOf(1000)) >= 0) {
                String formatted = String.format("$%.0fK/mo", val.doubleValue() / 1000.0);
                return type + " — " + formatted;
            } else {
                return type + " — $" + val.intValue() + "/mo";
            }
        }
        return type;
    }

    /**
     * Computes the number of full months between now and the contract end date.
     * Returns null if end date is null.
     */
    private static Long computeRetentionMonths(LocalDate contractEndDate) {
        if (contractEndDate == null) return null;
        return ChronoUnit.MONTHS.between(LocalDate.now(), contractEndDate);
    }

    /**
     * Formats the retention period: "18 months (ends Dec 2027)"
     */
    private static String formatRetention(Client c) {
        if (c.getContractEndDate() == null) return "Ongoing";
        Long months = computeRetentionMonths(c.getContractEndDate());
        String endFormatted = c.getContractEndDate().format(DateTimeFormatter.ofPattern("MMM yyyy"));
        if (months == null) return "ends " + endFormatted;
        if (months < 0) return "Expired (" + endFormatted + ")";
        return months + " months (ends " + endFormatted + ")";
    }

    /**
     * Returns true if the contract has ended or is expiring very soon (< 0 months remaining).
     */
    public boolean isStale() {
        return contractEndDate != null && contractEndDate.isBefore(LocalDate.now());
    }
}
