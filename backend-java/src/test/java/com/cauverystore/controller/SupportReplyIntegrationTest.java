package com.cauverystore.controller;

import com.cauverystore.entities.Faq;
import com.cauverystore.entities.InboundEmail;
import com.cauverystore.repository.FaqRepository;
import com.cauverystore.repository.InboundEmailRepository;
import com.cauverystore.service.EmailService;
import com.cauverystore.service.SupportReplyService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"SUPPORT_AUTOREPLY_LIVE=true", "support.autoreply.first-check-ms=3600000"})
@ActiveProfiles("test")
@Tag("integration")
class SupportReplyIntegrationTest {

    @Autowired SupportReplyService service;
    @Autowired InboundEmailRepository inbox;
    @Autowired FaqRepository faqs;
    @MockBean EmailService email;

    @Test
    void repliesExactlyOnce() {
        when(email.sendSupportReply(any(), any(), any())).thenReturn(true);
        Faq f = new Faq();
        f.setQuestion("Do you ship outside India?");
        f.setAnswer("We ship within India only.");
        faqs.save(f);

        InboundEmail e = new InboundEmail();
        e.setResendEmailId("support-reply-test");
        e.setFromAddress("asha@example.com");
        e.setToAddresses("support@cauverystore.in");
        e.setSubject("Question");
        e.setTextBody("Do you ship outside India?");
        e.setAuthSummary("SPF pass, DKIM pass, DMARC pass");
        e.setReceivedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
        e = inbox.save(e);

        InboundEmail stale = inbox.findById(e.getId()).orElseThrow();
        service.sweep();
        stale.setRead(true);
        inbox.save(stale); // an older copy saved after the reply must not clear the reply status
        service.sweep();

        verify(email, times(1)).sendSupportReply(eq("asha@example.com"), eq("Re: Question"), any());
        assertEquals(SupportReplyService.SENT, inbox.findById(e.getId()).orElseThrow().getAutoReplyStatus());
    }
}
