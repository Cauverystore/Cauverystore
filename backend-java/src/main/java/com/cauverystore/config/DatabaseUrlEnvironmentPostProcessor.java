package com.cauverystore.config;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String SOURCE_NAME = "databaseUrlFix";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty("SPRING_DATASOURCE_URL");
        if (url != null && !url.isEmpty()) {
            setUrl(environment, fixUrl(url));
            return;
        }

        url = environment.getProperty("DATABASE_URL");
        if (url != null && !url.isEmpty()) {
            setUrl(environment, fixUrl(url));
        }
    }

    private static String fixUrl(String url) {
        if (url.startsWith("jdbc:")) {
            return url;
        }
        if (url.startsWith("postgres://")) {
            return "jdbc:postgresql://" + url.substring("postgres://".length());
        }
        if (url.startsWith("postgresql://")) {
            return "jdbc:" + url;
        }
        return url;
    }

    private static void setUrl(ConfigurableEnvironment environment, String jdbcUrl) {
        environment.getPropertySources().addFirst(
            new MapPropertySource(SOURCE_NAME, Map.of("spring.datasource.url", jdbcUrl)));
    }
}
