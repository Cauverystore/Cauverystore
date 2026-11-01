package com.cauverystore.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Runs Flyway after Hibernate has built the schema, not before.
 *
 * The schema has always come from {@code ddl-auto=update}, and V2-V6 were written to be run by
 * hand against tables Hibernate had already made. Spring Boot's own Flyway setup runs before
 * Hibernate, so on a new database V2 would fail on a table that does not exist yet. Running it
 * here instead, with the database baselined at V6, means:
 *   - V2-V6 are recorded as already applied and never run (they were applied by hand or by
 *     Hibernate long ago);
 *   - V7 onwards run once each, in order, on every database - for what Hibernate cannot express,
 *     such as triggers.
 *
 * PostgreSQL only: the tests' H2 database has no PL/pgSQL. Switch off with
 * {@code app.flyway.enabled=false}; nothing else depends on it having run.
 */
@Configuration
public class FlywayAfterHibernateConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayAfterHibernateConfig.class);

    /** Last migration that predates Flyway being switched on. */
    static final String BASELINE_VERSION = "6";

    @Bean
    @DependsOn("entityManagerFactory")
    public InitializingBean flywayAfterHibernate(DataSource dataSource,
                                                 @Value("${app.flyway.enabled:true}") boolean enabled) {
        return () -> {
            if (!enabled) {
                log.info("Flyway: switched off (app.flyway.enabled=false)");
                return;
            }
            String product;
            try (Connection c = dataSource.getConnection()) {
                product = c.getMetaData().getDatabaseProductName();
            }
            if (!"PostgreSQL".equalsIgnoreCase(product)) {
                log.info("Flyway: skipped on {}", product);
                return;
            }
            MigrateResult result = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion(BASELINE_VERSION)
                    .baselineDescription("schema built by Hibernate; V2-V6 applied by hand")
                    .load()
                    .migrate();
            log.info("Flyway: {} migration(s) applied, schema now at {}",
                    result.migrationsExecuted, result.targetSchemaVersion);
        };
    }
}
