package com.cauverystore.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource(
            @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/cauverystore}") String defaultUrl,
            @Value("${spring.datasource.username:postgres}") String username,
            @Value("${spring.datasource.password:admin123}") String password) {

        String databaseUrl = System.getenv("DATABASE_URL");
        String springDatasourceUrl = System.getenv("SPRING_DATASOURCE_URL");
        if (springDatasourceUrl != null && !springDatasourceUrl.isEmpty()) {
            return DataSourceBuilder.create()
                .url(springDatasourceUrl)
                .driverClassName("org.postgresql.Driver")
                .build();
        }
        if (databaseUrl != null && !databaseUrl.isEmpty()) {
            return DataSourceBuilder.create()
                .url("jdbc:" + databaseUrl)
                .driverClassName("org.postgresql.Driver")
                .build();
        }

        return DataSourceBuilder.create()
                .url(defaultUrl)
                .username(username)
                .password(password)
                .driverClassName("org.postgresql.Driver")
                .build();
    }
}
