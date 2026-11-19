package com.cauverystore.service;

import com.cauverystore.entities.Role;
import com.cauverystore.util.PublicIdFormatter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Hands out the next account ID for a role (Cust001, Seller001, ...).
 *
 * One counter row per prefix. Each call increments the row inside its own short transaction, so
 * two sign-ups at the same moment can never get the same number, and a rolled-back sign-up
 * leaves at worst a gap, never a duplicate. The numbers have no upper limit.
 */
@Service
public class PublicIdService {

    private static final Logger log = LoggerFactory.getLogger(PublicIdService.class);

    /** Set once the application is up; the JPA listener cannot take injected beans directly. */
    private static volatile PublicIdService instance;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public PublicIdService(JdbcTemplate jdbc, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @PostConstruct
    void init() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS public_id_counters ("
                + "prefix VARCHAR(16) PRIMARY KEY, next_value BIGINT NOT NULL)");
        instance = this;
    }

    public static PublicIdService get() {
        return instance;
    }

    public String nextFor(Role role) {
        String prefix = PublicIdFormatter.prefixFor(role);
        Long n = tx.execute(status -> increment(prefix));
        if (n == null) throw new IllegalStateException("could not allocate an account number");
        return PublicIdFormatter.format(role, n);
    }

    private long increment(String prefix) {
        int updated = jdbc.update("UPDATE public_id_counters SET next_value = next_value + 1 WHERE prefix = ?", prefix);
        if (updated == 0) {
            try {
                jdbc.update("INSERT INTO public_id_counters (prefix, next_value) VALUES (?, 1)", prefix);
                return 1L;
            } catch (DataIntegrityViolationException raced) {
                // Another sign-up created the row between our update and insert; take the normal path.
                jdbc.update("UPDATE public_id_counters SET next_value = next_value + 1 WHERE prefix = ?", prefix);
            }
        }
        // The update above holds this row's lock until we commit, so the read is ours alone.
        Long v = jdbc.queryForObject("SELECT next_value FROM public_id_counters WHERE prefix = ?", Long.class, prefix);
        return v == null ? 1L : v;
    }
}
