package com.autoresolve.mediabuying.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import javax.faces.render.FacesRenderer;
import javax.faces.render.Renderer;
import java.io.IOException;

/**
 * Custom renderer to mask KPI column values for non-MEDIA_ANALYST users.
 * Shows "---" for VIEWER role users.
 */
@Component
@FacesRenderer(componentFamily = "javax.faces.Output", rendererType = "kpiColumn")
public class KpiColumnMaskRenderer extends Renderer {

    private static final Logger log = LoggerFactory.getLogger(KpiColumnMaskRenderer.class);

    @Override
    public void encodeEnd(FacesContext context, UIComponent component) throws IOException {
        String userRole = SecurityContextHolder.getContext()
                .getAuthentication().getAuthorities().stream()
                .findFirst().map(GrantedAuthority::getAuthority).orElse("UNKNOWN");

        if ("ROLE_MEDIA_ANALYST".equals(userRole) || "ROLE_ADMIN".equals(userRole)) {
            // For ANALYST and ADMIN, render the actual value by delegating
            // to the default renderer for this component family
            Renderer defaultRenderer = context.getRenderKit()
                    .getRenderer(component.getFamily(), component.getRendererType());
            if (defaultRenderer != null && defaultRenderer != this) {
                defaultRenderer.encodeEnd(context, component);
            }
        } else {
            // Mask the value for VIEWER role
            ResponseWriter writer = context.getResponseWriter();
            writer.startElement("span", component);
            writer.writeAttribute("class", "kpi-masked", null);
            writer.writeText("---", null);
            writer.endElement("span");
        }
    }
}
