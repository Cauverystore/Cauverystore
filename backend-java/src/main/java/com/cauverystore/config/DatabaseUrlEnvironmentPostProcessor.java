package com.cauverystore.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DatabaseUrlEnvironmentPostProcessor.class);
    private static final String SOURCE_NAME = "databaseUrlFix";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String rawUrl = environment.getProperty("SPRING_DATASOURCE_URL");
        if (rawUrl != null && !rawUrl.isEmpty()) {
            processUrl(environment, rawUrl);
            return;
        }

        rawUrl = environment.getProperty("DATABASE_URL");
        if (rawUrl != null && !rawUrl.isEmpty()) {
            processUrl(environment, rawUrl);
        }
    }

    private static void processUrl(ConfigurableEnvironment environment, String rawUrl) {
        String jdbcUrl = rawUrl;

        if (jdbcUrl.startsWith("jdbc:")) {
            jdbcUrl = jdbcUrl.substring(5);
        }

        try {
            URI uri = new URI(jdbcUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getPath();
            String userInfo = uri.getUserInfo();
            String query = uri.getQuery();

            if (host == null) {
                log.warn("Could not parse host from URL: {}", rawUrl);
                return;
            }

            String database = (path != null && path.startsWith("/")) ? path.substring(1) : "";
            String username = null;
            String password = null;

            if (userInfo != null) {
                int colonIdx = userInfo.indexOf(':');
                if (colonIdx >= 0) {
                    username = userInfo.substring(0, colonIdx);
                    password = userInfo.substring(colonIdx + 1);
                } else {
                    username = userInfo;
                }
            }

            String defaultPort = (port > 0) ? ":" + port : ":5432";
            String cleanUrl = "jdbc:postgresql://" + host + defaultPort + "/" + database;

            if (query != null && !query.isEmpty()) {
                cleanUrl += "?" + query;
            }

            Map<String, Object> props = new HashMap<>();
            props.put("spring.datasource.url", cleanUrl);

            if (username != null) {
                props.put("spring.datasource.username", username);
            }
            if (password != null) {
                props.put("spring.datasource.password", password);
            }

            environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, props));

            log.info("Set spring.datasource.url={}, username={}, password=[PROTECTED]", cleanUrl, username);

        } catch (URISyntaxException e) {
            log.warn("Failed to parse URL: {}", rawUrl, e);
        }
    }
}
