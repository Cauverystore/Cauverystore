package com.cauverystore.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RenderDataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource() {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        if (url == null || url.isBlank()) {
            url = "jdbc:postgresql://localhost:5432/cauverystore";
        }
        if (!url.startsWith("jdbc:")) {
            url = "jdbc:" + url;
        }

        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username != null ? username : "postgres");
        config.setPassword(password != null ? password : "admin123");
        config.setDriverClassName("org.postgresql.Driver");
        return new HikariDataSource(config);
    }
}
