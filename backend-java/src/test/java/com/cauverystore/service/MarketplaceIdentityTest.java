package com.cauverystore.service;

import com.cauverystore.entities.GstConfiguration;
import com.cauverystore.repository.*;
import com.cauverystore.client.GstnClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Whether the marketplace can identify itself on an invoice and file a return.
 *
 * A different question from whether the catalogue is classified, and the one that has to be right
 * first: section 24(x) requires an operator collecting TCS to register whatever its turnover, and
 * without that registration recorded there is no supplier on any invoice and nothing to file
 * GSTR-8 under.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceIdentityTest {

    @Mock private ProductRepository productRepo;
    @Mock private HsnMasterRepository hsnRepo;
    @Mock private GstRateResolver rateResolver;
    @Mock private GstInvoiceRepository invoiceRepo;
    @Mock private GstConfigurationRepository configRepo;
    @Mock private GstRateFreshnessService freshnessService;
    @Mock private GstRateMasterRepository rateRepo;
    @Mock private SellerRegistrationRepository sellerRegRepo;
    @Mock private GstnClient gstnClient;

    private GstComplianceReadinessService service;

    /** Real check digits, so only the field under test is ever the reason something fails. */
    private static final String VALID_GSTIN = "27AAPFU0939F1ZV";
    private static final String VALID_PAN = "AAPFU0939F";
    /** A second real GSTIN. The TCS registration is a separate one - see the test below. */
    private static final String VALID_TCS_GSTIN = "27AAPFU0939F2ZU";

    @BeforeEach
    void setUp() {
        service = new GstComplianceReadinessService(productRepo, hsnRepo, rateResolver, invoiceRepo,
                configRepo, freshnessService, rateRepo, sellerRegRepo, gstnClient);
    }

    private GstConfiguration complete() {
        GstConfiguration c = new GstConfiguration();
        c.setGstin(VALID_GSTIN);
        c.setTcsGstin(VALID_TCS_GSTIN);
        c.setStateCode("27");
        c.setLegalName("Cauvery Store Private Limited");
        c.setAddress("Registered address");
        c.setPan(VALID_PAN);
        c.setCin("U52100TN2025PTC123456");
        c.setNodalAccountNumber("123456789012");
        return c;
    }

    private Map<String, Object> identityOf(GstConfiguration c) {
        when(configRepo.findAll()).thenReturn(c == null ? List.of() : List.of(c));
        return service.marketplaceIdentity();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> item(Map<String, Object> out, String field) {
        return ((List<Map<String, Object>>) out.get("items")).stream()
                .filter(i -> field.equals(i.get("field"))).findFirst().orElse(null);
    }

    @Test
    void noConfigurationAtAllIsSaidPlainlyRatherThanAsAnEmptyChecklist() {
        Map<String, Object> out = identityOf(null);

        assertEquals(false, out.get("configured"));
        assertEquals(false, out.get("readyToFile"));
    }

    @Test
    void aCompleteRegistrationIsReadyToFile() {
        Map<String, Object> out = identityOf(complete());

        assertEquals(true, out.get("readyToFile"));
        assertEquals(0L, out.get("outstanding"));
    }

    @Test
    void everyFieldIsReportedEvenWhenItPasses() {
        // The whole point. A list of complaints reads the same whether everything was checked and
        // passed or nothing was checked at all.
        Map<String, Object> out = identityOf(complete());

        assertEquals("OK", item(out, "gstin").get("status"));
        assertEquals("OK", item(out, "cin").get("status"));
        assertEquals("OK", item(out, "nodalAccountNumber").get("status"));
    }

    @Test
    void aMissingTcsRegistrationIsCalledOutBecauseGstr8DependsOnIt() {
        GstConfiguration c = complete();
        c.setTcsGstin(null);
        c.setGstin(null);

        Map<String, Object> out = identityOf(c);

        assertEquals("MISSING", item(out, "tcsGstin").get("status"));
        assertEquals(false, out.get("readyToFile"));
    }

    @Test
    void aTcsGstinIdenticalToTheRegularOneIsNotATcsRegistration() {
        // Learned from the code rather than assumed: resolveTcsGstin treats the two being equal
        // as no TCS registration at all, because GSTR-8 filed under the regular GSTIN is
        // rejected. Somebody copying the GSTIN into both boxes has not registered for TCS, and a
        // screen that accepted it would say ready and then fail at filing.
        GstConfiguration c = complete();
        c.setTcsGstin(c.getGstin());

        Map<String, Object> out = identityOf(c);

        assertEquals("MISSING", item(out, "tcsGstin").get("status"));
        assertEquals(false, out.get("readyToFile"));
    }

    @Test
    void aGstinThatCannotBeRealCountsAsInvalidNotAsDone() {
        // Populated and wrong. A screen that ticks this off is worse than one that leaves it blank.
        GstConfiguration c = complete();
        c.setGstin("33ABCDE1234F1Z5");

        Map<String, Object> out = identityOf(c);

        assertEquals("INVALID", item(out, "gstin").get("status"));
        assertEquals(false, out.get("readyToFile"));
    }

    @Test
    void aStateCodeThatContradictsTheGstinIsCaught() {
        // Both fields are filled in and they cannot both be true. The GSTIN decides whether a
        // supply is inter-state, so a stale state code silently mis-splits the tax.
        GstConfiguration c = complete();
        c.setStateCode("33");

        Map<String, Object> out = identityOf(c);

        assertEquals("INVALID", item(out, "stateCode").get("status"));
        assertTrue(String.valueOf(item(out, "stateCode").get("fix")).contains("27"));
    }

    @Test
    void aPanThatIsNotTheOneInsideTheGstinIsCaught() {
        // A GSTIN carries its holder's PAN at characters 3-12, so the two can be checked against
        // each other rather than stored as unrelated claims.
        GstConfiguration c = complete();
        c.setPan("ZZZZZ9999Z");

        Map<String, Object> out = identityOf(c);

        assertEquals("INVALID", item(out, "pan").get("status"));
    }

    @Test
    void theBankAccountIsNotEchoedBackInFull() {
        // The screen needs to say whether it is set, not what it is.
        Map<String, Object> out = identityOf(complete());

        String shown = String.valueOf(item(out, "nodalAccountNumber").get("value"));
        assertNotEquals("123456789012", shown);
        assertTrue(shown.endsWith("9012"), shown);
    }
}
