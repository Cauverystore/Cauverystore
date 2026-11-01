package com.cauverystore.service;

import com.cauverystore.entities.GstAuditEvent;
import com.cauverystore.repository.GstAuditEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * The append-only GST audit trail: every invoice, note, IRN, cancellation, rate change and
 * registration change, each row hash-chained to the one before.
 *
 * An event is written only after the transaction that caused it commits, in a transaction of
 * its own. So a rolled-back invoice leaves no event behind, and a failure to write the event
 * never rolls back an invoice that has already been issued - it is logged instead.
 */
@Service
public class GstAuditService {

    private static final Logger log = LoggerFactory.getLogger(GstAuditService.class);

    /** prev_hash of the first event. */
    public static final String GENESIS = "0".repeat(64);

    public static final String INVOICE_ISSUED = "INVOICE_ISSUED";
    public static final String INVOICE_STATUS_CHANGED = "INVOICE_STATUS_CHANGED";
    public static final String IRN_GENERATED = "IRN_GENERATED";
    public static final String IRN_FAILED = "IRN_FAILED";
    public static final String CREDIT_NOTE_ISSUED = "CREDIT_NOTE_ISSUED";
    public static final String DEBIT_NOTE_ISSUED = "DEBIT_NOTE_ISSUED";
    public static final String RATE_CHANGE_ACCEPTED = "RATE_CHANGE_ACCEPTED";
    public static final String REGISTRATION_SAVED = "REGISTRATION_SAVED";

    private final GstAuditEventRepository repo;
    private final ObjectMapper json;
    private final TransactionTemplate ownTransaction;

    public GstAuditService(GstAuditEventRepository repo, ObjectMapper json,
                           PlatformTransactionManager txManager) {
        this.repo = repo;
        this.json = json;
        this.ownTransaction = new TransactionTemplate(txManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Records an event once the current transaction commits (at once if there is none).
     * The payload is what the event was about - amounts, numbers, old and new values.
     */
    public void record(String eventType, String entityType, String entityRef, Long sellerId,
                       Map<String, ?> payload) {
        String actor = currentActor();
        LocalDateTime at = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        String body;
        try {
            body = json.writeValueAsString(payload == null ? Map.of() : new TreeMap<>(payload));
        } catch (Exception e) {
            body = String.valueOf(payload);
        }
        final String payloadJson = body;
        Runnable write = () -> append(at, eventType, entityType, entityRef, sellerId, actor, payloadJson);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { write.run(); }
            });
        } else {
            write.run();
        }
    }

    private synchronized void append(LocalDateTime at, String eventType, String entityType,
                                     String entityRef, Long sellerId, String actor, String payload) {
        // The unique index on prev_hash turns a race with another app instance into a
        // constraint failure; read the new last row and try again.
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                ownTransaction.executeWithoutResult(status -> {
                    String prev = repo.findTopByOrderByIdDesc().map(GstAuditEvent::getHash).orElse(GENESIS);
                    GstAuditEvent e = new GstAuditEvent();
                    e.setOccurredAt(at);
                    e.setEventType(eventType);
                    e.setEntityType(entityType);
                    e.setEntityRef(entityRef);
                    e.setSellerId(sellerId);
                    e.setActor(actor);
                    e.setPayload(payload);
                    e.setPrevHash(prev);
                    e.setHash(hashOf(e));
                    repo.save(e);
                });
                return;
            } catch (DataIntegrityViolationException race) {
                log.warn("GST audit: chain race on attempt {}, retrying", attempt);
            } catch (Exception e) {
                log.error("GST audit: could not record {} {} {}: {}", eventType, entityType, entityRef, e.toString());
                return;
            }
        }
        log.error("GST audit: gave up recording {} {} {} after 3 races", eventType, entityType, entityRef);
    }

    /** SHA-256 over every stored field except the id and the hash itself. */
    public static String hashOf(GstAuditEvent e) {
        String material = String.join("\u001f",
                e.getPrevHash(),
                String.valueOf(e.getOccurredAt()),
                e.getEventType(),
                e.getEntityType(),
                String.valueOf(e.getEntityRef()),
                String.valueOf(e.getSellerId()),
                e.getActor(),
                String.valueOf(e.getPayload()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record ChainCheck(long checked, boolean intact, Long firstBadId, String problem) { }

    /**
     * Walks events in id order from {@code expectedPrev}. Catches an edited row (its hash no
     * longer matches) and a deleted or inserted row (the next row's prev_hash no longer links).
     */
    public static ChainCheck verify(Iterable<GstAuditEvent> events, String expectedPrev, long alreadyChecked) {
        long n = alreadyChecked;
        String prev = expectedPrev;
        for (GstAuditEvent e : events) {
            n++;
            if (!Objects.equals(e.getPrevHash(), prev)) {
                return new ChainCheck(n, false, e.getId(), "does not link to the event before it: one was removed or inserted");
            }
            if (!Objects.equals(hashOf(e), e.getHash())) {
                return new ChainCheck(n, false, e.getId(), "contents changed after it was recorded");
            }
            prev = e.getHash();
        }
        return new ChainCheck(n, true, null, null);
    }

    /** Checks the whole trail, a page at a time. */
    public ChainCheck verifyAll() {
        String prev = GENESIS;
        long checked = 0;
        int page = 0;
        while (true) {
            Page<GstAuditEvent> batch = repo.findAllByOrderByIdAsc(PageRequest.of(page++, 1000));
            ChainCheck c = verify(batch.getContent(), prev, checked);
            if (!c.intact()) return c;
            checked = c.checked();
            if (!batch.getContent().isEmpty()) prev = batch.getContent().get(batch.getContent().size() - 1).getHash();
            if (!batch.hasNext()) return new ChainCheck(checked, true, null, null);
        }
    }

    public Page<GstAuditEvent> recent(int page, int size) {
        return repo.findAllByOrderByIdDesc(PageRequest.of(page, Math.min(Math.max(size, 1), 200)));
    }

    public List<GstAuditEvent> history(String entityType, String entityRef) {
        return repo.findByEntityTypeAndEntityRefOrderByIdAsc(entityType, entityRef);
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null
                || "anonymousUser".equals(auth.getName())) {
            return "system";
        }
        return auth.getName();
    }

    /** Small helper so call sites can build payloads with nulls, which Map.of refuses. */
    public static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            m.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return m;
    }
}
