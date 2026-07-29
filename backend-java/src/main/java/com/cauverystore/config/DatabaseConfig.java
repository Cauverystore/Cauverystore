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
        String host = env("PGHOST");
        String port = env("PGPORT");
        String db = env("PGDATABASE");
        String user = env("PGUSER");
        String pass = env("PGPASSWORD");

        String url = null;

        if (host != null && port != null && db != null) {
            url = "jdbc:postgresql://" + host + ":" + port + "/" + db;
        } else {
            url = env("SPRING_DATASOURCE_URL");
            if (url != null && !url.isEmpty()) {
                if (user == null) user = env("SPRING_DATASOURCE_USERNAME");
                if (pass == null) pass = env("SPRING_DATASOURCE_PASSWORD");
            } else {
                String raw = env("DATABASE_URL");
                if (raw != null && !raw.isEmpty()) {
                    if (raw.startsWith("postgres://") || raw.startsWith("postgresql://")) {
                        String prefix = raw.startsWith("postgresql://") ? "postgresql://" : "postgres://";
                        String rest = raw.substring(prefix.length());
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
                }
            }
        }

        if (url == null || url.isEmpty()) {
            throw new IllegalStateException(
                "No database configuration found. Set PG* env vars (PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD) " +
                "or SPRING_DATASOURCE_URL/USERNAME/PASSWORD or DATABASE_URL.");
        }

        if (!url.contains("sslmode=")) {
            url += url.contains("?") ? "&sslmode=require" : "?sslmode=require";
        }

        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.postgresql.Driver");
        config.setJdbcUrl(url);
        if (user != null) config.setUsername(user);
        if (pass != null) config.setPassword(pass);

        log.info("Connecting to database at {}:{}/{}", host != null ? host : extractHost(url), port != null ? port : "5432", db != null ? db : extractDb(url));

        return new HikariDataSource(config);
    }

    private static String extractHost(String jdbcUrl) {
        String s = jdbcUrl.replace("jdbc:postgresql://", "");
        int colon = s.indexOf(':');
        int slash = s.indexOf('/');
        int end = colon > 0 ? colon : (slash > 0 ? slash : s.length());
        return s.substring(0, end);
    }

    private static String extractDb(String jdbcUrl) {
        String s = jdbcUrl.replace("jdbc:postgresql://", "");
        int slash = s.indexOf('/');
        if (slash < 0) return "?";
        String afterSlash = s.substring(slash + 1);
        int qmark = afterSlash.indexOf('?');
        return qmark > 0 ? afterSlash.substring(0, qmark) : afterSlash;
    }

    private static String env(String key) {
        String val = System.getenv(key);
        if (val != null && !val.isEmpty()) return val;
        val = System.getProperty(key);
        if (val != null && !val.isEmpty()) return val;
        return null;
    }
}
