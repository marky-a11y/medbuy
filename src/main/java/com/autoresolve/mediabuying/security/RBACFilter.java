package com.autoresolve.mediabuying.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Order(1)
public class RBACFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RBACFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // Log API requests for audit
        if (path.startsWith("/api/")) {
            log.debug("API request: method={}, path={}, remoteAddr={}",
                    httpRequest.getMethod(), path, httpRequest.getRemoteAddr());
        }

        chain.doFilter(request, response);
    }
}
