package com.autoresolve.mediabuying.controller.dashboard;

import com.autoresolve.mediabuying.model.dto.SectorDTO;
import com.autoresolve.mediabuying.service.PlatformSectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.faces.context.FacesContext;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Session-scoped bean for global sector filter state.
 * The selected sector persists via URL params.
 * Uses Spring @Component with session scope (registered by Joinfaces).
 */
@Component
@Scope("session")
public class SectorFilterBean implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(SectorFilterBean.class);
    private static final long serialVersionUID = 1L;

    @Autowired
    private transient PlatformSectorService platformSectorService;

    private Long selectedSectorId;
    private List<SectorDTO> sectors;

    @PostConstruct
    public void init() {
        // Read initial sector from request parameter
        FacesContext facesContext = FacesContext.getCurrentInstance();
        if (facesContext != null) {
            String sectorParam = facesContext.getExternalContext()
                    .getRequestParameterMap().get("sector");
            if (sectorParam != null && !sectorParam.isEmpty()) {
                try {
                    selectedSectorId = Long.parseLong(sectorParam);
                } catch (NumberFormatException e) {
                    selectedSectorId = null;
                }
            }
        }

        loadSectors();
    }

    private void loadSectors() {
        try {
            if (platformSectorService != null) {
                sectors = platformSectorService.getAllSectors().stream()
                        .map(s -> SectorDTO.builder()
                                .id(s.getId())
                                .name(s.getName())
                                .displayName(s.getDisplayName())
                                .isActive(s.getIsActive())
                                .build())
                        .collect(Collectors.toList());
            } else {
                sectors = new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("Failed to load sectors", e);
            sectors = new ArrayList<>();
        }
    }

    /**
     * Called when user changes the sector filter. Redirects to dashboard with sector param.
     */
    public void onSectorChange() {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        if (facesContext == null) return;

        String redirectUrl = "/dashboard.xhtml";
        if (selectedSectorId != null) {
            redirectUrl += "?sector=" + selectedSectorId;
        }

        try {
            facesContext.getExternalContext().redirect(
                    facesContext.getExternalContext().getRequestContextPath() + redirectUrl);
        } catch (IOException e) {
            log.error("Failed to redirect on sector change", e);
        }
    }

    // --- Getters and Setters ---

    public Long getSelectedSectorId() {
        return selectedSectorId;
    }

    public void setSelectedSectorId(Long selectedSectorId) {
        this.selectedSectorId = selectedSectorId;
    }

    public List<SectorDTO> getSectors() {
        return sectors;
    }
}
