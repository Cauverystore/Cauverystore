package com.cauverystore.config;

import com.cauverystore.service.RateLimiterService;
import com.cauverystore.util.ClientIp;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * The older sign-in addresses (/api/user/login, /api/seller/login, /api/admin/login) still work
 * but had no "too many attempts" limit, unlike /api/auth/login. They share the same per-address
 * count as /api/auth/login, so switching address does not buy more guesses.
 */
@Component
public class LegacyLoginRateLimitFilter extends OncePerRequestFilter {

    static final Set<String> PATHS = Set.of("/api/user/login", "/api/seller/login", "/api/admin/login");

    private final RateLimiterService rateLimiter;

    @Value("${auth.rate-limit.login-ip-max:20}")
    private int loginIpMax = 20;

    public LegacyLoginRateLimitFilter(RateLimiterService rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!rateLimiter.allow("login:ip:" + ClientIp.of(request), loginIpMax, 60_000L)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many login attempts. Try again later.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
