package com.autoresolve.mediabuying.controller.dashboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import java.io.IOException;
import java.io.Serializable;

/**
 * Request-scoped bean for CSV export functionality.
 * Redirects to the ExportApiController endpoint which generates and downloads the CSV.
 */
@Component
@Scope("request")
public class ExportBean implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(ExportBean.class);
    private static final long serialVersionUID = 1L;

    private Long platformId;
    private Long sectorId;

    /**
     * Triggers CSV download by redirecting to the export API endpoint.
     */
    public void downloadCsv() throws IOException {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        if (facesContext == null) return;

        ExternalContext externalContext = facesContext.getExternalContext();

        StringBuilder url = new StringBuilder();
        url.append(externalContext.getRequestContextPath());
        url.append("/api/export/csv");

        if (platformId != null || sectorId != null) {
            url.append("?");
            if (platformId != null) {
                url.append("platform=").append(platformId);
            }
            if (sectorId != null) {
                if (platformId != null) url.append("&");
                url.append("sector=").append(sectorId);
            }
        }

        log.info("CSV export redirect: {}", url);
        externalContext.redirect(url.toString());
    }

    // --- Getters and Setters ---

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }

    public Long getSectorId() {
        return sectorId;
    }

    public void setSectorId(Long sectorId) {
        this.sectorId = sectorId;
    }
}
