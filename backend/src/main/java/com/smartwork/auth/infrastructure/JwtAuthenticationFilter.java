package com.smartwork.auth.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwork.common.api.ApiErrorResponse;
import com.smartwork.common.error.ApiException;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.common.tenancy.OrganizationContext;
import com.smartwork.common.web.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Establishes the caller from the bearer token and populates the tenant context and MDC.
 *
 * <p>Everything downstream - repository predicates, access evaluators, audit rows - reads
 * the organization from {@link OrganizationContext}, so this filter is the single point
 * where a request acquires a tenant.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            AuthPrincipal principal = jwtService.verify(header.substring(BEARER_PREFIX.length()).trim());

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.authorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            OrganizationContext.set(principal.organizationId());
            MDC.put("userId", principal.userId().toString());
            MDC.put("organizationId", principal.organizationId().toString());

            chain.doFilter(request, response);

        } catch (ApiException invalidToken) {
            // Written here rather than thrown: the exception handler advice does not run
            // for exceptions raised inside the filter chain.
            SecurityContextHolder.clearContext();
            writeError(request, response, invalidToken);
        } finally {
            SecurityContextHolder.clearContext();
            OrganizationContext.clear();
            MDC.remove("userId");
            MDC.remove("organizationId");
        }
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            ApiException exception) throws IOException {
        response.setStatus(exception.getCode().status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(
                exception, request.getRequestURI(), CorrelationId.current()));
    }
}
