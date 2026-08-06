package com.cauverystore.client;

import com.cauverystore.entities.GstInvoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the behaviour that matters even though the portal calls themselves cannot be tested
 * here: that this client never invents an IRN.
 *
 * An invoice carrying a fabricated IRN looks registered to everyone downstream - the buyer may
 * claim input credit against a number the portal has never heard of, and nobody finds out
 * until a reconciliation months later. A missing IRN is a visible problem that can be retried;
 * a fake one is an invisible one that cannot. So every failure path here must throw.
 */
class LiveGstnClientTest {

    private LiveGstnClient client;
    private GstInvoice invoice;

    @BeforeEach
    void setUp() {
        client = new LiveGstnClient();
        invoice = new GstInvoice();
        invoice.setInvoiceNumber("INV-1");
        invoice.setInvoiceDate(LocalDate.of(2026, 8, 7));
        invoice.setSellerGstin("33AABCU9603R1ZM");
        invoice.setTotalAmount(1000.0);
    }

    private void configure(String clientId, String secret, String user, String pass,
                           String gstin, String publicKey) {
        ReflectionTestUtils.setField(client, "clientId", clientId);
        ReflectionTestUtils.setField(client, "clientSecret", secret);
        ReflectionTestUtils.setField(client, "username", user);
        ReflectionTestUtils.setField(client, "password", pass);
        ReflectionTestUtils.setField(client, "gstin", gstin);
        ReflectionTestUtils.setField(client, "publicKeyBase64", publicKey);
    }

    @Test
    void shouldRefuseToRegister_whenCredentialsAreMissing() {
        // Half-configured must fail loudly. Quietly falling back to the simulator here would
        // put a made-up IRN on a real invoice, which is the whole thing to avoid.
        configure("id", "", "user", "pass", "33AABCU9603R1ZM", "key");

        LiveGstnClient.IrpException ex = assertThrows(
                LiveGstnClient.IrpException.class, () -> client.generateIrn(invoice));
        assertTrue(ex.getMessage().contains("not fully "),
                "the message should say what is missing, not just that it failed");
    }

    @Test
    void shouldNotReportItselfAsSimulated() {
        // ComplianceService surfaces this to the operator; getting it backwards would tell
        // someone their invoices were real when they were not, or the reverse.
        assertFalse(client.isSimulated());
        assertEquals("GSTN_LIVE", client.getName());
    }

    @Test
    void shouldRefuseFeaturesItHasNotImplementedRatherThanReturnSomething() {
        // These are separate portals with their own schemas. Returning an empty map would read
        // as "no e-way bill needed" instead of "never asked".
        configure("id", "secret", "user", "pass", "33AABCU9603R1ZM", "key");

        assertThrows(LiveGstnClient.IrpException.class, () -> client.generateEwayBill(invoice));
        assertThrows(LiveGstnClient.IrpException.class, () -> client.validateGstin("33AABCU9603R1ZM"));
        assertThrows(LiveGstnClient.IrpException.class, () -> client.fetchGstr2bData("33AABCU9603R1ZM", "082026"));
        assertThrows(LiveGstnClient.IrpException.class, () -> client.fileReturn("33AABCU9603R1ZM", "GSTR1", "082026"));
    }

    @Test
    void shouldStillAnswerFilingDueDates_whichAreStatutoryNotPortalCalls() {
        assertNotNull(client.getFilingDueDate("GSTR1", "082026"));
    }

    @Test
    void shouldFailRatherThanReachTheNetwork_whenNotConfigured() {
        // Nothing is configured at all, which is the state of every environment by default.
        assertThrows(LiveGstnClient.IrpException.class, () -> client.generateIrn(invoice));
    }
}
