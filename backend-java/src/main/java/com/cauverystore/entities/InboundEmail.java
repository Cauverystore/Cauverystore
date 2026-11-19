package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * An email somebody sent to one of the store's own addresses (support@cauverystore.in and so
 * on), received by Resend and handed to InboundEmailService. Shown in Admin -> Inbox.
 */
@Entity
@Table(name = "inbound_emails", indexes = {
        @Index(name = "idx_inbound_emails_received_at", columnList = "received_at")
})
public class InboundEmail extends BaseEntity {

    /** Resend's id for the email. Unique, so a retried webhook cannot save it twice. */
    @Column(name = "resend_email_id", nullable = false, unique = true, length = 100)
    private String resendEmailId;

    /** Sender as written in the header, e.g. "Asha Kumar <asha@example.com>". */
    @Column(name = "from_header", length = 500)
    private String fromHeader;

    /** Bare sender address - where replies go. */
    @Column(name = "from_address", length = 320)
    private String fromAddress;

    /** Store addresses it was sent to, comma separated. */
    @Column(name = "to_addresses", length = 1000)
    private String toAddresses;

    @Column(name = "cc_addresses", length = 1000)
    private String ccAddresses;

    @Column(length = 1000)
    private String subject;

    @Column(name = "text_body", columnDefinition = "TEXT")
    private String textBody;

    @Column(name = "html_body", columnDefinition = "TEXT")
    private String htmlBody;

    /** Attachment file names, one per line. The files themselves stay with Resend. */
    @Column(name = "attachment_names", columnDefinition = "TEXT")
    private String attachmentNames;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    /** Where it was forwarded, or null if it was not. */
    @Column(name = "forwarded_to", length = 1000)
    private String forwardedTo;

    /** SPF / DKIM / DMARC results as Resend reported them, for spotting spoofed mail. */
    @Column(name = "auth_summary", length = 200)
    private String authSummary;

    public String getResendEmailId() { return resendEmailId; }
    public void setResendEmailId(String resendEmailId) { this.resendEmailId = resendEmailId; }
    public String getFromHeader() { return fromHeader; }
    public void setFromHeader(String fromHeader) { this.fromHeader = fromHeader; }
    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }
    public String getToAddresses() { return toAddresses; }
    public void setToAddresses(String toAddresses) { this.toAddresses = toAddresses; }
    public String getCcAddresses() { return ccAddresses; }
    public void setCcAddresses(String ccAddresses) { this.ccAddresses = ccAddresses; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getTextBody() { return textBody; }
    public void setTextBody(String textBody) { this.textBody = textBody; }
    public String getHtmlBody() { return htmlBody; }
    public void setHtmlBody(String htmlBody) { this.htmlBody = htmlBody; }
    public String getAttachmentNames() { return attachmentNames; }
    public void setAttachmentNames(String attachmentNames) { this.attachmentNames = attachmentNames; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public String getForwardedTo() { return forwardedTo; }
    public void setForwardedTo(String forwardedTo) { this.forwardedTo = forwardedTo; }
    public String getAuthSummary() { return authSummary; }
    public void setAuthSummary(String authSummary) { this.authSummary = authSummary; }
}
