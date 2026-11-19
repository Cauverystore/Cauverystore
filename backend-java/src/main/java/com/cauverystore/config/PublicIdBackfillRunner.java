package com.cauverystore.config;

import com.cauverystore.entities.Role;
import com.cauverystore.service.PublicIdService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Gives an account ID to every user saved before IDs existed, oldest first, so the earliest
 * customer becomes Cust001. Runs on every start and skips users who already have one, so a
 * second run changes nothing.
 */
@Component
public class PublicIdBackfillRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PublicIdBackfillRunner.class);

    private final JdbcTemplate jdbc;
    private final PublicIdService ids;

    public PublicIdBackfillRunner(JdbcTemplate jdbc, PublicIdService ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public void run(String... args) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, role FROM users WHERE public_id IS NULL ORDER BY id");
            for (Map<String, Object> r : rows) {
                Role role;
                try {
                    role = r.get("role") == null ? Role.CUSTOMER : Role.valueOf(String.valueOf(r.get("role")));
                } catch (IllegalArgumentException e) {
                    role = Role.CUSTOMER;
                }
                jdbc.update("UPDATE users SET public_id = ? WHERE id = ? AND public_id IS NULL",
                        ids.nextFor(role), r.get("id"));
            }
            if (!rows.isEmpty()) log.info("Assigned account IDs to {} existing users", rows.size());
        } catch (Exception e) {
            log.warn("Account ID backfill skipped: {}", e.getMessage());
        }
    }
}
