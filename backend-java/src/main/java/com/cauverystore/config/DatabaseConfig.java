package com.cauverystore.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource(Environment env) {
        String url = env.getProperty("SPRING_DATASOURCE_URL");
        if (url != null && !url.isEmpty()) {
            return DataSourceBuilder.create()
                .url(url)
                .driverClassName("org.postgresql.Driver")
                .build();
        }
        url = env.getProperty("DATABASE_URL");
        if (url != null && !url.isEmpty()) {
            return DataSourceBuilder.create()
                .url("jdbc:" + url)
                .driverClassName("org.postgresql.Driver")
                .build();
        }
        url = env.getProperty("spring.datasource.url",
                "jdbc:postgresql://localhost:5432/cauverystore");
        String username = env.getProperty("spring.datasource.username", "postgres");
        String password = env.getProperty("spring.datasource.password", "admin123");
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName("org.postgresql.Driver")
                .build();
    }
}
