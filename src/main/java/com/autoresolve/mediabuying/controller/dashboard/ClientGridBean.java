package com.autoresolve.mediabuying.controller.dashboard;

import com.autoresolve.mediabuying.model.dto.ClientDTO;
import com.autoresolve.mediabuying.service.ClientListService;
import org.primefaces.model.LazyDataModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.Serializable;

/**
 * View-scoped managed bean for the Client Portfolio Grid section
 * of the dashboard. Manages the lazy data model for the client table.
 */
@Component
@Scope("view")
public class ClientGridBean implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(ClientGridBean.class);
    private static final long serialVersionUID = 1L;

    @Autowired
    private transient ClientLazyDataModel clientLazyDataModel;

    public ClientGridBean() {
    }

    @PostConstruct
    public void init() {
        log.debug("ClientGridBean initialized");
        // The lazy model is injected and ready for use
    }

    /**
     * Returns the lazy data model for the client portfolio table.
     */
    public LazyDataModel<ClientDTO> getClientModel() {
        return clientLazyDataModel;
    }

    /**
     * Returns a CSS class based on the outlook score value.
     */
    public String getOutlookCssClass(int score) {
        if (score >= 70) return "outlook-high";
        if (score >= 40) return "outlook-medium";
        return "outlook-low";
    }

    /**
     * Returns a CSS class based on the contract type.
     */
    public String getContractBadgeClass(String type) {
        if (type == null) return "contract-hybrid";
        switch (type.toUpperCase()) {
            case "RETAINER":    return "contract-retainer";
            case "PERFORMANCE": return "contract-performance";
            case "HYBRID":      return "contract-hybrid";
            default:            return "contract-hybrid";
        }
    }

    /**
     * Returns true if the client's contract is expiring within 3 months.
     */
    public boolean isExpiringSoon(ClientDTO client) {
        if (client == null || client.getRetentionPeriodMonths() == null) {
            return false;
        }
        return client.getRetentionPeriodMonths() < 3;
    }
}
