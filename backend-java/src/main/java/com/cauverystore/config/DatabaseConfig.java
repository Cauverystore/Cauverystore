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
        if (url == null || url.isEmpty()) {
            url = env("DATABASE_URL");
        }

        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.postgresql.Driver");

        if (url != null && !url.isEmpty()) {
            String user = null;
            String pass = null;

            if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
                String prefix = url.startsWith("postgresql://") ? "postgresql://" : "postgres://";
                String rest = url.substring(prefix.length());
                int atIndex = rest.indexOf('@');
                if (atIndex > 0) {
                    String userPass = rest.substring(0, atIndex);
                    String hostPortDb = rest.substring(atIndex + 1);
                    int colonIndex = userPass.indexOf(':');
                    if (colonIndex < 0) {
                        user = userPass;
                    } else {
                        user = userPass.substring(0, colonIndex);
                        pass = userPass.substring(colonIndex + 1);
                    }
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
            config.setPassword("postgres");
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
