package com.cauverystore.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource() {
        String url = env("SPRING_DATASOURCE_URL");
        String user = env("SPRING_DATASOURCE_USERNAME");
        String pass = env("SPRING_DATASOURCE_PASSWORD");

        if (url == null || url.isEmpty()) {
            String raw = env("DATABASE_URL");
            if (raw != null && !raw.isEmpty()) {
                url = raw;
            }
        }

        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.postgresql.Driver");

        if (url != null && !url.isEmpty()) {
            if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
                String prefix = url.startsWith("postgresql://") ? "postgresql://" : "postgres://";
                String rest = url.substring(prefix.length());
                int atIndex = rest.indexOf('@');
                if (atIndex > 0) {
                    String userPass = rest.substring(0, atIndex);
                    String hostPortDb = rest.substring(atIndex + 1);
                    int colonIndex = userPass.indexOf(':');
                    if (user == null) user = colonIndex < 0 ? userPass : userPass.substring(0, colonIndex);
                    if (pass == null) pass = colonIndex < 0 ? null : userPass.substring(colonIndex + 1);
                    url = "jdbc:postgresql://" + hostPortDb;
                } else {
                    url = "jdbc:postgresql://" + rest;
                }
            }
            if (!url.contains("sslmode=")) {
                url += url.contains("?") ? "&sslmode=require" : "?sslmode=require";
            }
            config.setJdbcUrl(url);
            if (user != null) config.setUsername(user);
            if (pass != null) config.setPassword(pass);
        } else {
            config.setJdbcUrl("jdbc:postgresql://localhost:5432/postgres?sslmode=require");
            config.setUsername("postgres");
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
