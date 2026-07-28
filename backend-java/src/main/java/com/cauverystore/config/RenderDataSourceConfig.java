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
                jdbcUrl = "jdbc:postgresql://" + host + (port > 0 ? ":" + port : ":5432") + "/" + db;
            } catch (URISyntaxException e) {
                jdbcUrl = "jdbc:postgresql://localhost:5432/cauverystore";
            }
        } else {
            jdbcUrl = rawUrl.startsWith("jdbc:") ? rawUrl : "jdbc:" + rawUrl;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username != null ? username : "postgres");
        config.setPassword(password != null ? password : "admin123");
        config.setDriverClassName("org.postgresql.Driver");
        return new HikariDataSource(config);
    }
}
