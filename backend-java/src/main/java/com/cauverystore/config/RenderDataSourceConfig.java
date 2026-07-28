package com.cauverystore.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RenderDataSourceConfig {
    private static final Logger log = LoggerFactory.getLogger(RenderDataSourceConfig.class);

    @Bean
    @Primary
    public DataSource dataSource() {
        log.info("=== RenderDataSourceConfig: Checking env vars ===");
        log.info("PGHOST='{}'", System.getenv("PGHOST"));
        log.info("DATABASE_URL='{}'", System.getenv("DATABASE_URL"));
        log.info("PGPORT='{}'", System.getenv("PGPORT"));
        log.info("SPRING_DATASOURCE_URL='{}'", System.getenv("SPRING_DATASOURCE_URL"));
        log.info("PGDATABASE='{}'", System.getenv("PGDATABASE"));
        log.info("PGUSER='{}'", System.getenv("PGUSER"));

        log.info("=== All env vars with 'RAILWAY', 'POSTGRES', 'DATABASE', 'PG', 'SPRING' ===");
        for (Map.Entry<String, String> e : System.getenv().entrySet()) {
            String k = e.getKey().toUpperCase();
            if (k.contains("RAILWAY") || k.contains("POSTGRES") || k.contains("DATABASE") || k.contains("PG") || k.contains("SPRING")) {
                log.info("  {}={}", e.getKey(), e.getValue());
            }
        }

        String pghost = System.getenv("PGHOST");
        if (pghost != null && !pghost.isBlank()) {
            String pgport = System.getenv("PGPORT");
            String pgdatabase = System.getenv("PGDATABASE");
            String pguser = System.getenv("PGUSER");
            String pgpassword = System.getenv("PGPASSWORD");
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:postgresql://" + pghost + ":" + (pgport != null ? pgport : "5432") + "/" + (pgdatabase != null ? pgdatabase : "cauverystore") + "?sslmode=require");
            config.setUsername(pguser != null ? pguser : "postgres");
            config.setPassword(pgpassword != null ? pgpassword : "admin123");
            config.setDriverClassName("org.postgresql.Driver");
            return new HikariDataSource(config);
        }

        String rawUrl = System.getenv("SPRING_DATASOURCE_URL");
        if (rawUrl == null || rawUrl.isBlank()) {
            rawUrl = System.getenv("DATABASE_URL");
        }
        if (rawUrl == null || rawUrl.isBlank()) {
            rawUrl = "postgresql://localhost:5432/cauverystore";
        }

        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        String jdbcUrl;

        if (rawUrl.startsWith("postgres") || rawUrl.startsWith("jdbc:postgresql")) {
            try {
                String uriStr = rawUrl.startsWith("jdbc:") ? rawUrl.substring(5) : rawUrl;
                URI uri = new URI(uriStr);
                String host = uri.getHost();
                int port = uri.getPort();
                String path = uri.getPath();
                String db = path != null ? path.replaceFirst("^/", "") : "";
                if (username == null && uri.getUserInfo() != null) {
                    String[] parts = uri.getUserInfo().split(":", 2);
                    username = parts[0];
                    if (parts.length > 1) password = parts[1];
                }
                jdbcUrl = "jdbc:postgresql://" + host + (port > 0 ? ":" + port : ":5432") + "/" + db + "?sslmode=require";
            } catch (URISyntaxException e) {
                log.warn("Failed to parse URL '{}', using fallback", rawUrl, e);
                String host = System.getenv("PGHOST") != null ? System.getenv("PGHOST") : "localhost";
                String port = System.getenv("PGPORT") != null ? System.getenv("PGPORT") : "5432";
                String db = System.getenv("PGDATABASE") != null ? System.getenv("PGDATABASE") : "cauverystore";
                jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + db + "?sslmode=require";
                if (username == null) username = System.getenv("PGUSER");
                if (password == null) password = System.getenv("PGPASSWORD");
            }
        } else {
            jdbcUrl = rawUrl.startsWith("jdbc:") ? rawUrl : "jdbc:" + rawUrl;
        }

        log.info("=== RenderDataSourceConfig: Using JDBC URL = {} ===", jdbcUrl);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username != null ? username : "postgres");
        config.setPassword(password != null ? password : "");
        config.setDriverClassName("org.postgresql.Driver");
        return new HikariDataSource(config);
    }
}
