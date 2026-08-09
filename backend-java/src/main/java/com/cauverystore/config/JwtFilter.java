package com.cauverystore.config;

import com.cauverystore.entities.User;
import com.cauverystore.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepo;

    public JwtFilter(JwtUtil jwtUtil, ObjectMapper objectMapper, UserRepository userRepo) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.userRepo = userRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = jwtUtil.extractAllClaims(token);
                String username = claims.get("username", String.class);
                String role = claims.get("role", String.class);
                @SuppressWarnings("unchecked")
                List<String> roles = claims.get("roles", List.class);
                if (roles == null || roles.isEmpty()) {
                    roles = role != null ? List.of(role) : List.of();
                }
                Long userId = claims.get("userId", Long.class);
                Integer tokenVersion = claims.get("tokenVersion", Integer.class);

                if (userId != null) {
                    User user = userRepo.findById(userId).orElse(null);
                    int currentVersion = (user != null && user.getTokenVersion() != null) ? user.getTokenVersion() : 0;
                    int presentedVersion = tokenVersion != null ? tokenVersion : 0;
                    if (user == null || presentedVersion != currentVersion) {
                        sendError(response, "Session has been invalidated. Please log in again.");
                        return;
                    }
                    // A suspension has to take effect on the next request, not whenever the
                    // access token happens to expire. Bumping the token version at the point of
                    // suspension already achieves that; this is the second line, and it catches
                    // an account stopped by any route at all - a direct database change, or some
                    // future code path that forgets to invalidate. The user row is already
                    // loaded here, so it costs nothing.
                    if (!user.isActive() || !"ACTIVE".equals(user.getStatus())) {
                        sendError(response, "This account is not active. Please contact support.");
                        return;
                    }
                }

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    List<SimpleGrantedAuthority> authorities = roles.stream()
                            .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                            .collect(java.util.stream.Collectors.toList());
                    var auth = new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            authorities
                    );
                    auth.setDetails(userId);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (ExpiredJwtException e) {
                sendError(response, "Token expired");
                return;
            } catch (Exception e) {
                sendError(response, "Invalid or expired token");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), Map.of("error", message));
    }
}
