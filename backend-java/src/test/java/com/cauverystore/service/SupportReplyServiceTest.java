package com.cauverystore.service;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupportReplyServiceTest {

    private final InboundEmailRepository inbox = mock(InboundEmailRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final FaqRepository faqs = mock(FaqRepository.class);
    private SupportReplyService service;
    private final User customer = new User();

    @BeforeEach
    void setUp() {
        service = new SupportReplyService(inbox, users, orders, faqs, mock(EmailService.class), mock(AuditService.class),
                mock(PlatformTransactionManager.class));
        when(inbox.countByFromAddressIgnoreCaseAndAutoReplyStatusAndAutoRepliedAtAfter(anyString(), anyString(), any())).thenReturn(0L);
        when(users.findByEmail("asha@example.com")).thenReturn(customer);
        when(orders.findByUserOrderByCreatedAtDesc(customer)).thenReturn(List.of(order(12L, "SHIPPED"), order(9L, "DELIVERED")));
        Faq f = new Faq();
        f.setQuestion("Do you ship outside India?");
        f.setAnswer("We ship within India only.");
        when(faqs.findByActiveTrueOrderBySortOrder()).thenReturn(List.of(f));
    }

    private static Order order(Long id, String status) {
        Order o = new Order();
        ReflectionTestUtils.setField(o, "id", id);
        o.setStatus(status);
        o.setCourier("Delhivery");
        o.setTrackingNumber("TRK123");
        return o;
    }

    private static InboundEmail mail(String subject, String body) {
        InboundEmail e = new InboundEmail();
        e.setFromAddress("asha@example.com");
        e.setToAddresses("support@cauverystore.in");
        e.setSubject(subject);
        e.setTextBody(body);
        e.setAuthSummary("SPF pass, DKIM pass, DMARC pass");
        return e;
    }

    @Test
    void answersOwnOrderWithoutPrivateDetails() {
        SupportReplyService.Plan p = service.plan(mail("Order status", "Where is my order 12?"));
        assertEquals(SupportReplyService.SENT, p.status());
        assertTrue(p.text().contains("Order #12"));
        assertTrue(p.text().contains("TRK123"));
        assertFalse(p.text().contains("Order #9"));
    }

    @Test
    void neverShowsSomeoneElsesOrder() {
        SupportReplyService.Plan p = service.plan(mail("Order status", "Where is my order 777?"));
        assertEquals(SupportReplyService.NEEDS_PERSON, p.status());
        assertFalse(p.text().contains("777"));
    }

    @Test
    void forgedSenderGetsNoOrderDetails() {
        InboundEmail e = mail("Order status", "Where is my order 12?");
        e.setAuthSummary("SPF fail, DKIM fail, DMARC fail");
        SupportReplyService.Plan p = service.plan(e);
        assertEquals(SupportReplyService.NEEDS_PERSON, p.status());
        assertFalse(p.text().contains("TRK123"));
    }

    @Test
    void missingSenderCheckGoesToAPerson() {
        InboundEmail e = mail("Order status", "Where is my order 12?");
        e.setAuthSummary(null);
        assertEquals(SupportReplyService.NEEDS_PERSON, service.plan(e).status());
    }

    @Test
    void refundsAndComplaintsGoToAPersonWithDraft() {
        for (String body : List.of("I want a refund for order 12", "The item arrived damaged", "Where is my order? Money was deducted twice")) {
            SupportReplyService.Plan p = service.plan(mail("Help", body));
            assertEquals(SupportReplyService.NEEDS_PERSON, p.status(), body);
            assertNotNull(p.text());
        }
    }

    @Test
    void closedOrderGoesToAPerson() {
        assertEquals(SupportReplyService.NEEDS_PERSON, service.plan(mail("Order status", "Where is my order 9?")).status());
    }

    @Test
    void answersCloseFaqOnly() {
        SupportReplyService.Plan yes = service.plan(mail("Question", "Do you ship outside India?"));
        assertEquals(SupportReplyService.SENT, yes.status());
        assertTrue(yes.text().contains("within India only"));
        assertEquals(SupportReplyService.NEEDS_PERSON, service.plan(mail("Question", "Do you sell blue curtains?")).status());
    }

    @Test
    void automaticMailNeverGetsAReply() {
        InboundEmail noreply = mail("Order status", "Where is my order 12?");
        noreply.setFromAddress("no-reply@shop.example");
        assertEquals(SupportReplyService.SKIPPED, service.plan(noreply).status());

        InboundEmail own = mail("Order status", "Where is my order 12?");
        own.setFromAddress("orders@cauverystore.in");
        assertEquals(SupportReplyService.SKIPPED, service.plan(own).status());

        InboundEmail flagged = mail("Order status", "Where is my order 12?");
        flagged.setAutomated(true);
        assertEquals(SupportReplyService.SKIPPED, service.plan(flagged).status());
    }

    @Test
    void repliesAttachmentsAndRepeatSendersGoToAPerson() {
        assertEquals(SupportReplyService.NEEDS_PERSON, service.plan(mail("Re: Order status", "Where is my order 12?")).status());
        InboundEmail attached = mail("Order status", "Where is my order 12?");
        attached.setAttachmentNames("photo.jpg");
        assertEquals(SupportReplyService.NEEDS_PERSON, service.plan(attached).status());
        when(inbox.countByFromAddressIgnoreCaseAndAutoReplyStatusAndAutoRepliedAtAfter(anyString(), anyString(), any())).thenReturn(2L);
        assertEquals(SupportReplyService.NEEDS_PERSON, service.plan(mail("Order status", "Where is my order 12?")).status());
    }

    @Test
    void quotedOldTextIsIgnored() {
        assertEquals("Thanks", SupportReplyService.latestText("Thanks\nOn Mon, Asha wrote:\n> refund please"));
    }
}
