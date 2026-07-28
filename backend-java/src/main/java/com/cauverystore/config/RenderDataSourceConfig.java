package com.cauverystore.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.URISyntaxException;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RenderDataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource() {
        System.out.println("=== RenderDataSourceConfig: Checking env vars ===");
        System.out.println("PGHOST='"+ System.getenv("PGHOST") + "'");
        System.out.println("DATABASE_URL='"+ System.getenv("DATABASE_URL") + "'");
        System.out.println("PGPORT='"+ System.getenv("PGPORT") + "'");
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

        if (rawUrl.startsWith("postgres") && !rawUrl.startsWith("jdbc:")) {
            try {
                URI uri = new URI(rawUrl.startsWith("jdbc:") ? rawUrl.substring(5) : rawUrl);
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
                jdbcUrl = "jdbc:postgresql://localhost:5432/cauverystore";
            }
        } else {
            jdbcUrl = rawUrl.startsWith("jdbc:") ? rawUrl : "jdbc:" + rawUrl;
        }

        System.out.println("=== RenderDataSourceConfig: Using JDBC URL = " + jdbcUrl);
        System.out.println("=== RenderDataSourceConfig: Username = " + (username != null ? username : "postgres"));
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username != null ? username : "postgres");
        config.setPassword(password != null ? password : "admin123");
        config.setDriverClassName("org.postgresql.Driver");
        return new HikariDataSource(config);
    }
}
