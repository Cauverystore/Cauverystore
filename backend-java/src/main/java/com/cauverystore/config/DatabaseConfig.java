package com.cauverystore.config;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    @Primary
    public DataSource dataSource() {
        log.info("DatabaseConfig.dataSource() initializing...");

        String host = env("PGHOST");
        String port = env("PGPORT");
        String db = env("PGDATABASE");
        String user = env("PGUSER");
        String pass = env("PGPASSWORD");

        if (host != null && port != null && db != null) {
            String url = "jdbc:postgresql://" + host + ":" + port + "/" + db;
            if (!url.contains("sslmode=")) {
                url += "?sslmode=require";
            }
            log.info("Using PG* env vars: {}/{}/{}", host, port, db);
            return buildDataSource(url, user, pass);
        }

        log.warn("PG* env vars not found (PGHOST={}, PGPORT={}, PGDATABASE={})", host, port, db);
        log.warn("Check that Railway PostgreSQL is linked to this service.");

        String springUrl = env("SPRING_DATASOURCE_URL");
        if (springUrl != null && !springUrl.isEmpty()) {
            if (springUrl.contains("localhost") || springUrl.contains("127.0.0.1")) {
                throw new IllegalStateException(
                    "SPRING_DATASOURCE_URL points to localhost (" + springUrl + "). " +
                    "Railway PostgreSQL is not linked to this service. " +
                    "Go to Railway dashboard -> Variables -> click 'Add Variable' -> reference Postgres plugin, " +
                    "or delete SPRING_DATASOURCE_URL/USERNAME/PASSWORD and link the PostgreSQL plugin properly."
                );
            }
            if (!springUrl.contains("sslmode=")) {
                springUrl += springUrl.contains("?") ? "&sslmode=require" : "?sslmode=require";
            }
            String springUser = user != null ? user : env("SPRING_DATASOURCE_USERNAME");
            String springPass = pass != null ? pass : env("SPRING_DATASOURCE_PASSWORD");
            log.info("Using SPRING_DATASOURCE_URL: {}", springUrl.replaceAll("//[^@]*@", "//***@***").replaceAll("sslmode=[^&]*", "sslmode=..."));
            return buildDataSource(springUrl, springUser, springPass);
        }

        String rawUrl = env("DATABASE_URL");
        if (rawUrl != null && !rawUrl.isEmpty()) {
            if (rawUrl.startsWith("postgres://") || rawUrl.startsWith("postgresql://")) {
                String prefix = rawUrl.startsWith("postgresql://") ? "postgresql://" : "postgres://";
                String rest = rawUrl.substring(prefix.length());
                int atIndex = rest.indexOf('@');
                if (atIndex > 0) {
                    String userPass = rest.substring(0, atIndex);
                    String hostPortDb = rest.substring(atIndex + 1);
                    int colonIndex = userPass.indexOf(':');
                    if (user == null) user = colonIndex < 0 ? userPass : userPass.substring(0, colonIndex);
                    if (pass == null) pass = colonIndex < 0 ? null : userPass.substring(colonIndex + 1);
                    rawUrl = "jdbc:postgresql://" + hostPortDb;
                } else {
                    rawUrl = "jdbc:postgresql://" + rest;
                }
            }
            if (!rawUrl.contains("sslmode=")) {
                rawUrl += rawUrl.contains("?") ? "&sslmode=require" : "?sslmode=require";
            }
            log.info("Using DATABASE_URL");
            return buildDataSource(rawUrl, user, pass);
        }

        throw new IllegalStateException(
            "No database configuration found. Link Railway PostgreSQL to this service (auto-sets PG* vars), " +
            "or set SPRING_DATASOURCE_URL/USERNAME/PASSWORD (host:port/db only, no embedded credentials)."
        );
    }

    private static DataSource buildDataSource(String url, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.postgresql.Driver");
        config.setJdbcUrl(url);
        if (username != null) config.setUsername(username);
        if (password != null) config.setPassword(password);
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
