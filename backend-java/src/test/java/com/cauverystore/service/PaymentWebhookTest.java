package com.cauverystore.service;

import com.cauverystore.entities.Payment;
import com.cauverystore.repository.OrderRepository;
import com.cauverystore.repository.PaymentRepository;
import com.cauverystore.repository.RefundRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * The Razorpay webhook is what tells us about a payment whose customer never came back to the
 * site. These pin down that only correctly signed webhooks are believed, that a captured payment
 * is recorded exactly once, and that an orphan payment is not refunded before the grace period.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentWebhookTest {

    private static final String SECRET = "whsec_test";

    @Mock private PaymentRepository paymentRepo;
    @Mock private OrderRepository orderRepo;
    @Mock private RefundRepository refundRepo;
    @Mock private AuthorizationService authorizationService;

    private PaymentService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new PaymentService(paymentRepo, orderRepo, refundRepo, authorizationService);
        ReflectionTestUtils.setField(service, "webhookSecret", SECRET);
        ReflectionTestUtils.setField(service, "unclaimedRefundAfterMinutes", 30L);
    }

    private static String sign(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private JsonNode captured(String paymentId) throws Exception {
        return mapper.readTree("{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":"
                + "{\"id\":\"" + paymentId + "\",\"order_id\":\"order_X\",\"amount\":52500,\"currency\":\"INR\"}}}}");
    }

    @Test
    void acceptsOnlyCorrectlySignedBodies() throws Exception {
        String body = "{\"event\":\"payment.captured\"}";
        assertTrue(service.webhookSignatureMatches(body, sign(SECRET, body)));
        assertFalse(service.webhookSignatureMatches(body, sign("wrong", body)));
        assertFalse(service.webhookSignatureMatches(body + " ", sign(SECRET, body)));
        assertFalse(service.webhookSignatureMatches(body, null));
    }

    @Test
    void unconfiguredSecretTrustsNothing() throws Exception {
        ReflectionTestUtils.setField(service, "webhookSecret", "placeholder");
        String body = "{}";
        assertFalse(service.webhookSignatureMatches(body, sign("placeholder", body)));
    }

    @Test
    void recordsCapturedPaymentOnce() throws Exception {
        when(paymentRepo.findByRazorpayPaymentId("pay_1")).thenReturn(Optional.empty());
        assertEquals("recorded pay_1", service.recordCapturedPayment(captured("pay_1")));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepo).save(saved.capture());
        assertEquals("CAPTURED", saved.getValue().getStatus());
        assertEquals(525.0, saved.getValue().getAmount());
        assertEquals("order_X", saved.getValue().getRazorpayOrderId());
        assertNull(saved.getValue().getOrderId());

        when(paymentRepo.findByRazorpayPaymentId("pay_1")).thenReturn(Optional.of(saved.getValue()));
        assertEquals("already recorded pay_1", service.recordCapturedPayment(captured("pay_1")));
        verify(paymentRepo, times(1)).save(any());
    }

    @Test
    void ignoresOtherEvents() throws Exception {
        assertTrue(service.recordCapturedPayment(mapper.readTree("{\"event\":\"payment.failed\"}")).startsWith("ignored"));
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void nothingToRefundMeansNoGatewayCall() {
        when(paymentRepo.findByOrderIdIsNullAndStatusInAndPaidAtBefore(anyCollection(), any())).thenReturn(List.of());
        service.refundUnclaimedPayments();
        verify(refundRepo, never()).save(any());
    }
}
