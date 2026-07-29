package com.cauverystore.config;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    public DataSource dataSource() {
        String rawUrl = env("SPRING_DATASOURCE_URL");
        if (rawUrl == null || rawUrl.isEmpty()) {
            rawUrl = env("DATABASE_URL");
        }

        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.postgresql.Driver");

        if (rawUrl != null && !rawUrl.isEmpty()) {
            String jdbcUrl = rawUrl;
            String user = null;
            String pass = null;

            if (rawUrl.startsWith("postgres://") || rawUrl.startsWith("postgresql://")) {
                String prefix = rawUrl.startsWith("postgresql://") ? "postgresql://" : "postgres://";
                String rest = rawUrl.substring(prefix.length());
                int atIndex = rest.indexOf('@');
                if (atIndex > 0) {
                    String userPass = rest.substring(0, atIndex);
                    String hostPortDb = rest.substring(atIndex + 1);
                    int colonIndex = userPass.indexOf(':');
                    user = colonIndex < 0 ? userPass : userPass.substring(0, colonIndex);
                    pass = colonIndex < 0 ? null : userPass.substring(colonIndex + 1);
                    jdbcUrl = "jdbc:postgresql://" + hostPortDb;
                } else {
                    jdbcUrl = "jdbc:postgresql://" + rest;
                }
            } else if (rawUrl.startsWith("jdbc:postgresql://") && rawUrl.contains("@")) {
                String afterScheme = rawUrl.substring("jdbc:postgresql://".length());
                int atIndex = afterScheme.indexOf('@');
                if (atIndex > 0) {
                    String userPass = afterScheme.substring(0, atIndex);
                    String hostPortDb = afterScheme.substring(atIndex + 1);
                    int colonIndex = userPass.indexOf(':');
                    user = colonIndex < 0 ? userPass : userPass.substring(0, colonIndex);
                    pass = colonIndex < 0 ? null : userPass.substring(colonIndex + 1);
                    jdbcUrl = "jdbc:postgresql://" + hostPortDb;
                }
            }

            if (!jdbcUrl.contains("sslmode=")) {
                jdbcUrl += jdbcUrl.contains("?") ? "&sslmode=require" : "?sslmode=require";
            }

            config.setJdbcUrl(jdbcUrl);
            config.setUsername(user);
            config.setPassword(pass);

            log.info("DataSource configured: url={}, user={}", jdbcUrl.replaceAll("sslmode=[^&]*", "sslmode=..."), user);
        } else {
            config.setJdbcUrl("jdbc:postgresql://localhost:5432/postgres?sslmode=require");
            config.setUsername("postgres");
            config.setPassword("postgres");
            log.warn("No DATABASE_URL or SPRING_DATASOURCE_URL found, using localhost fallback");
        }

        return new HikariDataSource(config);
    }

    private static String env(String key) {
        String val = System.getenv(key);
        if (val != null && !val.isEmpty()) return val;
        val = System.getProperty(key);
        if (val != null && !val.isEmpty()) return val;
        return null;
    }
}
