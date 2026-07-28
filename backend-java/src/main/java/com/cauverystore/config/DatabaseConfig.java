package com.cauverystore.config;

import java.util.Collections;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;

@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @PostConstruct
    public void debugEnv() {
        log.info("========== DATABASE ENV DEBUG ==========");
        System.getenv().entrySet().stream()
            .filter(e -> e.getKey().toUpperCase().contains("DATABASE")
                      || e.getKey().toUpperCase().contains("DATASOURCE")
                      || e.getKey().toUpperCase().contains("SPRING_PROFILE"))
            .forEach(e -> log.info("ENV: {} = {}", e.getKey(), e.getValue()));
        log.info("========== END DEBUG ==========");
    }

    @Bean
    public DataSource dataSource(Environment env) {
        String url = env.getProperty("SPRING_DATASOURCE_URL");
        if (url != null && !url.isEmpty()) {
            log.info("Using SPRING_DATASOURCE_URL: {}", url);
            return buildDs(url);
        }
        url = env.getProperty("DATABASE_URL");
        if (url != null && !url.isEmpty()) {
            String jdbcUrl = "jdbc:" + url;
            log.info("Using DATABASE_URL -> jdbc:{}", url);
            return buildDs(jdbcUrl);
        }
        url = env.getProperty("spring.datasource.url",
                "jdbc:postgresql://localhost:5432/cauverystore");
        String username = env.getProperty("spring.datasource.username", "postgres");
        String password = env.getProperty("spring.datasource.password", "admin123");
        log.warn("Falling back to default URL: {}", url);
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    private DataSource buildDs(String url) {
        return DataSourceBuilder.create()
                .url(url)
                .driverClassName("org.postgresql.Driver")
                .build();
    }
}
