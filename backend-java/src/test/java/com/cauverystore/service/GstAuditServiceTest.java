package com.cauverystore.service;

import com.cauverystore.entities.GstAuditEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The trail is only worth having if a change to history shows. These build a chain the way the
 * service does and then tamper with it the three ways someone with database access could.
 */
class GstAuditServiceTest {

    private static List<GstAuditEvent> chain(int n) {
        List<GstAuditEvent> events = new ArrayList<>();
        String prev = GstAuditService.GENESIS;
        for (int i = 1; i <= n; i++) {
            GstAuditEvent e = new GstAuditEvent();
            e.setId((long) i);
            e.setOccurredAt(LocalDateTime.of(2026, 11, 1, 10, 0).plusMinutes(i));
            e.setEventType(GstAuditService.INVOICE_ISSUED);
            e.setEntityType("GstInvoice");
            e.setEntityRef("CS/2026/" + i);
            e.setSellerId(7L);
            e.setActor("system");
            e.setPayload("{\"total\":" + (100 * i) + "}");
            e.setPrevHash(prev);
            e.setHash(GstAuditService.hashOf(e));
            prev = e.getHash();
            events.add(e);
        }
        return events;
    }

    @Test
    void anUntouchedChainVerifies() {
        GstAuditService.ChainCheck c = GstAuditService.verify(chain(5), GstAuditService.GENESIS, 0);
        assertTrue(c.intact());
        assertEquals(5, c.checked());
    }

    @Test
    void editingAnAmountIsCaughtAtThatEvent() {
        List<GstAuditEvent> events = chain(5);
        events.get(2).setPayload("{\"total\":1}");

        GstAuditService.ChainCheck c = GstAuditService.verify(events, GstAuditService.GENESIS, 0);

        assertFalse(c.intact());
        assertEquals(3L, c.firstBadId());
        assertTrue(c.problem().contains("changed"));
    }

    @Test
    void deletingAnEventIsCaughtAtTheOneAfterIt() {
        List<GstAuditEvent> events = chain(5);
        events.remove(1);

        GstAuditService.ChainCheck c = GstAuditService.verify(events, GstAuditService.GENESIS, 0);

        assertFalse(c.intact());
        assertEquals(3L, c.firstBadId());
        assertTrue(c.problem().contains("removed"));
    }

    @Test
    void rehashingAnEditedEventStillBreaksTheLinkToTheNext() {
        List<GstAuditEvent> events = chain(5);
        GstAuditEvent edited = events.get(2);
        edited.setPayload("{\"total\":1}");
        edited.setHash(GstAuditService.hashOf(edited));

        GstAuditService.ChainCheck c = GstAuditService.verify(events, GstAuditService.GENESIS, 0);

        assertFalse(c.intact());
        assertEquals(4L, c.firstBadId());
    }

    @Test
    void theHashCoversWhoAndWhen() {
        GstAuditEvent e = chain(1).get(0);
        String original = e.getHash();
        e.setActor("someone-else");
        assertNotEquals(original, GstAuditService.hashOf(e));
        e.setActor("system");
        e.setOccurredAt(e.getOccurredAt().plusSeconds(1));
        assertNotEquals(original, GstAuditService.hashOf(e));
    }

    @Test
    void payloadKeepsNulls() {
        var p = GstAuditService.payload("irn", null, "total", 10.0);
        assertTrue(p.containsKey("irn"));
        assertEquals(2, p.size());
    }
}
