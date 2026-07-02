package com.autoresolve.mediabuying.controller.dashboard;

import com.autoresolve.mediabuying.model.dto.ClientDTO;
import com.autoresolve.mediabuying.service.ClientListService;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortMeta;
import org.primefaces.model.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * PrimeFaces LazyDataModel for the Client Portfolio Grid.
 * Prototype-scoped for thread safety.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ClientLazyDataModel extends LazyDataModel<ClientDTO> {

    private static final Logger log = LoggerFactory.getLogger(ClientLazyDataModel.class);
    private static final long serialVersionUID = 1L;

    @Autowired
    private transient ClientListService clientListService;

    public ClientLazyDataModel() {
        // Default constructor for proxy creation
    }

    public ClientLazyDataModel(ClientListService clientListService) {
        this.clientListService = clientListService;
    }

    @Override
    public List<ClientDTO> load(int first, int pageSize,
                                 Map<String, SortMeta> sortBy,
                                 Map<String, FilterMeta> filterBy) {
        // Extract sort field and order from SortMeta map (PrimeFaces 11 API)
        String sortField = "outlookScore";
        SortOrder sortOrder = SortOrder.DESCENDING;
        if (sortBy != null && !sortBy.isEmpty()) {
            SortMeta meta = sortBy.values().iterator().next();
            if (meta.getField() != null) {
                sortField = meta.getField();
            }
            if (meta.getOrder() != null) {
                sortOrder = meta.getOrder();
            }
        }

        int page = pageSize > 0 ? first / pageSize : 0;
        String sortDir = (sortOrder == SortOrder.ASCENDING) ? "asc" : "desc";

        log.debug("ClientLazyDataModel loading: page={}, size={}, sort={} {}",
                page, pageSize, sortField, sortDir);

        try {
            org.springframework.data.domain.Page<ClientDTO> result =
                    clientListService.getClients(page, pageSize, sortField, sortDir);

            this.setRowCount((int) result.getTotalElements());
            log.debug("ClientLazyDataModel loaded {} rows out of {} total",
                    result.getContent().size(), result.getTotalElements());

            return result.getContent();
        } catch (Exception e) {
            log.error("ClientLazyDataModel load failed", e);
            this.setRowCount(0);
            return java.util.Collections.emptyList();
        }
    }

    @Override
    public int count(Map<String, FilterMeta> filterBy) {
        try {
            org.springframework.data.domain.Page<ClientDTO> result =
                    clientListService.getClients(0, 1, "outlookScore", "desc");
            return (int) result.getTotalElements();
        } catch (Exception e) {
            log.error("ClientLazyDataModel count failed", e);
            return 0;
        }
    }
}
