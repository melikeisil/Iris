package com.smartwork.common.web;

import com.smartwork.common.domain.Ids;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Accepts a client-supplied correlation ID or generates one, and echoes it back so the
 * caller can quote it in a bug report.
 *
 * <p>Runs before authentication so that even a rejected login is traceable.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader(CorrelationId.HEADER);
        String correlationId = isUsable(supplied) ? supplied : Ids.newId().toString();

        CorrelationId.set(correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            CorrelationId.clear();
        }
    }

    /** Client-supplied values are echoed into logs, so they are length- and charset-bounded. */
    private boolean isUsable(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
            return false;
        }
        return value.chars().allMatch(c ->
                Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ':' || c == '.');
    }
}
