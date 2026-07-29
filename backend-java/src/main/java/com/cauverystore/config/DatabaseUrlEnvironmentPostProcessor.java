package com.cauverystore.config;

import java.util.HashMap;
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
            setProperties(environment, fixUrl(url), null, null);
            return;
        }

        url = environment.getProperty("DATABASE_URL");
        if (url != null && !url.isEmpty()) {
            String[] parts = parseDatabaseUrl(url);
            setProperties(environment, fixUrl(parts[0]), parts[1], parts[2]);
        }
    }

    private static String[] parseDatabaseUrl(String url) {
        String rest = url;
        if (rest.startsWith("postgres://")) {
            rest = rest.substring("postgres://".length());
        } else if (rest.startsWith("postgresql://")) {
            rest = rest.substring("postgresql://".length());
        } else if (rest.startsWith("jdbc:postgresql://")) {
            return new String[]{url, null, null};
        } else {
            return new String[]{url, null, null};
        }

        int atIndex = rest.indexOf('@');
        if (atIndex < 0) {
            return new String[]{url, null, null};
        }

        String userPass = rest.substring(0, atIndex);
        String hostPortDb = rest.substring(atIndex + 1);

        String user = null, pass = null;
        int colonIndex = userPass.indexOf(':');
        if (colonIndex < 0) {
            user = userPass;
        } else {
            user = userPass.substring(0, colonIndex);
            pass = userPass.substring(colonIndex + 1);
        }

        String jdbcUrl = "jdbc:postgresql://" + hostPortDb;
        return new String[]{jdbcUrl, user, pass};
    }

    private static String fixUrl(String url) {
        if (url.startsWith("postgres://")) {
            url = "jdbc:postgresql://" + url.substring("postgres://".length());
        } else if (url.startsWith("postgresql://")) {
            url = "jdbc:" + url;
        }
        if (!url.contains("sslmode=")) {
            url += url.contains("?") ? "&sslmode=require" : "?sslmode=require";
        }
        return url;
    }

    private static void setProperties(ConfigurableEnvironment environment, String jdbcUrl, String username, String password) {
        Map<String, Object> map = new HashMap<>();
        map.put("spring.datasource.url", jdbcUrl);
        if (username != null) {
            map.put("spring.datasource.username", username);
        }
        if (password != null) {
            map.put("spring.datasource.password", password);
        }
        environment.getPropertySources().addFirst(
            new MapPropertySource(SOURCE_NAME, map));
    }
}
