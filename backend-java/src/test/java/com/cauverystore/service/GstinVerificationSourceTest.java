package com.cauverystore.service;

import com.cauverystore.client.SimulatedGstnClient;
import com.cauverystore.entities.SellerGstin;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.repository.SellerGstinRepository;
import com.cauverystore.repository.SellerRegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * What a simulated GSTIN check is allowed to claim.
 *
 * Seller approval refuses to activate an account without a VERIFIED GSTIN. With no GSP contract
 * that verification is a stand-in, and it used to be indistinguishable from a real one: the same
 * VERIFIED, a reference beginning GSTN- that no portal ever issued, and "SIMULATED LEGAL NAME"
 * written into the field meant for the registered name. An admin reading that screen had no way
 * to tell a checked seller from an unchecked one.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GstinVerificationSourceTest {

    @Mock private SellerGstinRepository gstinRepo;
    @Mock private SellerRegistrationRepository regRepo;
    @Mock private AuditService auditService;

    private GstinVerificationService service;
    private SellerRegistration reg;

    /** A GSTIN whose check digit is genuinely correct, so only the source is under test. */
    private static final String VALID = "27AAPFU0939F1ZV";

    @BeforeEach
    void setUp() {
        service = new GstinVerificationService(gstinRepo, regRepo, new SimulatedGstnClient(), auditService);
        reg = new SellerRegistration();
        when(regRepo.findByUserId(5L)).thenReturn(Optional.of(reg));
        when(regRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(gstinRepo.findBySellerIdAndGstin(anyLong(), any())).thenReturn(Optional.empty());
        when(gstinRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void aSimulatedCheckIsRecordedAsSimulated() {
        service.verifyGstin(5L, VALID);

        assertEquals("VERIFIED", reg.getGstinStatus());
        assertEquals("SIMULATED", reg.getGstinVerificationSource(),
                "an admin cannot tell a real check from a stand-in without this");
    }

    @Test
    void noLegalNameIsInventedWhenNobodyLookedItUp() {
        // "SIMULATED LEGAL NAME" sitting in the registered-name field is worse than a blank,
        // because a blank is obviously unknown and that is not.
        service.verifyGstin(5L, VALID);

        assertNull(reg.getGstinLegalName());
    }

    @Test
    void theReferenceDoesNotPretendToComeFromThePortal() {
        // A reference beginning GSTN- reads as something quotable back to the portal. For a
        // simulated check there is no such thing, and inventing one puts a fiction in a
        // compliance file.
        SellerGstin saved = captureRecord();

        assertTrue(saved.getVerificationRef().startsWith("SIM-"), saved.getVerificationRef());
        assertEquals("SIMULATED", saved.getSource());
    }

    @Test
    void theSellerIsToldTheirGstinWasNotActuallyChecked() {
        Map<String, Object> result = service.verifyGstin(5L, VALID);

        assertEquals(Boolean.TRUE, result.get("simulated"));
        assertTrue(String.valueOf(result.get("verificationNote")).contains("has not been confirmed"),
                String.valueOf(result.get("verificationNote")));
    }

    @Test
    void theStateCodeIsStillTrustedBecauseItIsReadOffTheNumber() {
        // Unlike the name, this is not looked up anywhere - it is the first two characters of the
        // GSTIN, so a simulator is as good a source as the portal.
        service.verifyGstin(5L, VALID);

        assertEquals("27", reg.getGstinStateCode());
    }

    @Test
    void aGstinThatFailsItsCheckDigitIsRefusedOutright() {
        assertThrows(IllegalArgumentException.class,
                () -> service.verifyGstin(5L, "33ABCDE1234F1Z5"));
    }

    private SellerGstin captureRecord() {
        org.mockito.ArgumentCaptor<SellerGstin> captor =
                org.mockito.ArgumentCaptor.forClass(SellerGstin.class);
        service.verifyGstin(5L, VALID);
        org.mockito.Mockito.verify(gstinRepo).save(captor.capture());
        return captor.getValue();
    }
}
