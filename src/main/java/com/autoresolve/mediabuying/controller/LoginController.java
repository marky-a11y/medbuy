package com.autoresolve.mediabuying.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Serves the login page template for Spring Security form login,
 * and provides redirect mappings for the root and dashboard paths.
 */
@Controller
public class LoginController {

    /**
     * Redirect root URL to the main dashboard page.
     */
    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard.xhtml";
    }

    /**
     * Serve the Thymeleaf login template for Spring Security form login.
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /**
     * Suppress favicon.ico 404 warnings by returning empty content.
     */
    @GetMapping("/favicon.ico")
    @ResponseBody
    public ResponseEntity<Void> favicon() {
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
