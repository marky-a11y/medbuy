package com.autoresolve.mediabuying.controller.dashboard;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Redirecting controller for the dashboard.
 * The main dashboard is now served via JSF at /dashboard.xhtml.
 * This controller redirects /dashboard requests to the JSF page.
 */
@Controller
public class DashboardController {

    @GetMapping("/dashboard")
    public String redirectToJsfDashboard() {
        return "redirect:/dashboard.xhtml";
    }
}
