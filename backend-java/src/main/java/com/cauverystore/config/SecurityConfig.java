package com.cauverystore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfig()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/auth/register").permitAll()
                .requestMatchers("/api/auth/google").permitAll()
                .requestMatchers("/api/auth/refresh").permitAll()
                .requestMatchers("/api/auth/request-password-reset").permitAll()
                .requestMatchers("/api/auth/reset-password").permitAll()
                .requestMatchers("/api/auth/complete-forced-reset").permitAll()
                .requestMatchers("/api/auth/request-password-reset-link").permitAll()
                .requestMatchers("/api/auth/reset-password-link").permitAll()
                .requestMatchers("/api/auth/send-otp").permitAll()
                .requestMatchers("/api/auth/verify-otp").permitAll()
                .requestMatchers("/api/auth/login-2fa").permitAll()
                .requestMatchers("/api/user/login").permitAll()
                .requestMatchers("/api/user/register").permitAll()
                .requestMatchers("/api/seller/login").permitAll()
                .requestMatchers("/api/admin/login").permitAll()
                .requestMatchers("/api/super-admin/login").permitAll()
                .requestMatchers("/api/executive/login").permitAll()
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/api/otp/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/faqs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/settings/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                .requestMatchers("/api/admin/template.xlsx").permitAll()
                .requestMatchers("/api/seller/template.xlsx").permitAll()
                .requestMatchers("/api/newsletter/**").permitAll()
                .requestMatchers("/api/chat/**").permitAll()
                .requestMatchers("/api/support-tickets/**").authenticated()
                .requestMatchers("/api/payment-methods/**").authenticated()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/user/**").authenticated()
                .requestMatchers("/api/cart/**").hasAnyRole("CUSTOMER", "SUPER_ADMIN")
                .requestMatchers("/api/wishlist/**").hasAnyRole("CUSTOMER")
                .requestMatchers("/api/orders/**").authenticated()
                .requestMatchers("/api/seller/**").hasAnyRole("SELLER", "SUPER_ADMIN")
                .requestMatchers("/api/inventory/**").hasAnyRole("SELLER", "ADMIN", "SUPER_ADMIN", "EXECUTIVE")
                .requestMatchers(HttpMethod.PUT, "/api/admin/products/*/approval").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers("/api/admin/products/**").hasAnyRole("ADMIN", "SELLER", "EXECUTIVE", "SUPER_ADMIN")
                .requestMatchers("/api/admin/users/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers("/api/admin/settings/**").hasAnyRole("SUPER_ADMIN")
                .requestMatchers("/api/admin/audit/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers("/api/admin/reports/**").hasAnyRole("ADMIN", "EXECUTIVE", "SUPER_ADMIN")
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "EXECUTIVE", "SUPER_ADMIN")
                .requestMatchers("/api/gst/state-codes").permitAll()
                .requestMatchers("/api/gst/my-order-invoice/**").authenticated()
                .requestMatchers("/api/gst/**").hasAnyRole("SELLER", "ADMIN", "SUPER_ADMIN")
                .requestMatchers("/api/super-admin/**").hasAnyRole("SUPER_ADMIN")
                .anyRequest().authenticated()
            )
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Authentication required\"}");
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(403);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Access denied\"}");
                })
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfig() {
        CorsConfiguration c = new CorsConfiguration();
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        c.setAllowedOrigins(origins);
        c.setAllowedOriginPatterns(List.of("http://localhost:*"));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setExposedHeaders(List.of("Authorization", "Set-Cookie"));
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
        s.registerCorsConfiguration("/**", c);
        return s;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
