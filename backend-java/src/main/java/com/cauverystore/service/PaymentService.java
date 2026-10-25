package com.cauverystore.service;

import com.cauverystore.entities.Order;
import com.cauverystore.entities.Payment;
import com.cauverystore.entities.Refund;
import com.cauverystore.repository.OrderRepository;
import com.cauverystore.repository.PaymentRepository;
import com.cauverystore.repository.RefundRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    @Value("${razorpay.key_id}")
    private String keyId;

    @Value("${razorpay.key_secret}")
    private String keySecret;

    @Value("${razorpay.webhook_secret}")
    private String webhookSecret;

    /** How long a paid checkout may go without becoming an order before the money is sent back. */
    @Value("${payments.unclaimed-refund-after-minutes:30}")
    private long unclaimedRefundAfterMinutes;

    /** Payment statuses that mean money was taken: COMPLETED from the browser, CAPTURED from the webhook. */
    private static final List<String> TAKEN_STATUSES = List.of("COMPLETED", "CAPTURED");

    private final PaymentRepository paymentRepo;
    private final OrderRepository orderRepo;
    private final RefundRepository refundRepo;
    private final AuthorizationService authorizationService;

    public PaymentService(PaymentRepository paymentRepo, OrderRepository orderRepo, RefundRepository refundRepo, AuthorizationService authorizationService) {
        this.paymentRepo = paymentRepo;
        this.orderRepo = orderRepo;
        this.refundRepo = refundRepo;
        this.authorizationService = authorizationService;
    }

    public com.razorpay.Order createRazorpayOrder(String receipt, double amount) throws Exception {
        RazorpayClient razorpay = new RazorpayClient(keyId, keySecret);
        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", (int) (amount * 100));
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", receipt);
        return razorpay.orders.create(orderRequest);
    }

    public boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            return hmacSha256Hex(keySecret, orderId + "|" + paymentId).equals(signature);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Razorpay signs each webhook body with the secret typed into its webhook settings. Until
     * RAZORPAY_WEBHOOK_SECRET is set nothing matches, so an unconfigured deployment ignores
     * webhooks instead of trusting them.
     */
    public boolean webhookSignatureMatches(String rawBody, String signature) {
        if (rawBody == null || signature == null || webhookSecret == null
                || webhookSecret.isBlank() || "placeholder".equals(webhookSecret)) {
            return false;
        }
        try {
            byte[] expected = hmacSha256Hex(webhookSecret, rawBody).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private static String hmacSha256Hex(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Records a payment.captured webhook.
     *
     * <h2>Why this exists</h2>
     *
     * The order is only created when the customer's browser comes back from Razorpay. If the tab
     * closes, the phone dies or the connection drops after paying, the browser never comes back:
     * the money is taken and nothing on our side knows. The webhook is Razorpay telling us
     * directly, so every captured payment has a row here whether or not the browser returned.
     * A row that never gets an order is refunded by refundUnclaimedPayments.
     */
    @Transactional
    public String recordCapturedPayment(JsonNode event) {
        if (!"payment.captured".equals(event.path("event").asText())) {
            return "ignored: " + event.path("event").asText("no event");
        }
        JsonNode entity = event.path("payload").path("payment").path("entity");
        String paymentId = entity.path("id").asText(null);
        if (paymentId == null) {
            return "ignored: no payment id";
        }
        if (paymentRepo.findByRazorpayPaymentId(paymentId).isPresent()) {
            return "already recorded " + paymentId;
        }
        Payment payment = new Payment();
        payment.setRazorpayPaymentId(paymentId);
        payment.setRazorpayOrderId(entity.path("order_id").asText(null));
        payment.setAmount(entity.path("amount").asLong() / 100.0);
        payment.setCurrency(entity.path("currency").asText("INR"));
        payment.setStatus("CAPTURED");
        payment.setPaidAt(LocalDateTime.now());
        paymentRepo.save(payment);
        return "recorded " + paymentId;
    }

    /**
     * Sends back money that was taken but never became an order.
     *
     * A payment with no order after the grace period means the customer paid and the checkout
     * never finished - the browser did not return, or placing the order failed afterwards. We
     * cannot build the order ourselves (the cart and address only exist in the browser at that
     * point), so the honest outcome is a full refund. Each payment is tried once: a refund
     * Razorpay rejects is marked REFUND_FAILED_NO_ORDER and logged for someone to look at,
     * rather than retried every few minutes.
     */
    @Scheduled(fixedDelayString = "${payments.unclaimed-refund-check-ms:300000}",
               initialDelayString = "${payments.unclaimed-refund-initial-delay-ms:120000}")
    public void refundUnclaimedPayments() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(unclaimedRefundAfterMinutes);
        for (Payment payment : paymentRepo.findByOrderIdIsNullAndStatusInAndPaidAtBefore(TAKEN_STATUSES, cutoff)) {
            refundUnclaimed(payment);
        }
    }

    private void refundUnclaimed(Payment payment) {
        Refund refund = new Refund();
        refund.setAmount(payment.getAmount());
        refund.setRefundDate(LocalDate.now());
        refund.setRefundMethod("ORIGINAL_METHOD");
        refund.setReason("Paid but no order was placed (payment " + payment.getRazorpayPaymentId() + ")");
        try {
            RazorpayClient razorpay = new RazorpayClient(keyId, keySecret);
            JSONObject refundRequest = new JSONObject();
            refundRequest.put("amount", (int) Math.round(payment.getAmount() * 100));
            refundRequest.put("receipt", "NOORDER-" + payment.getRazorpayPaymentId());
            com.razorpay.Refund razorpayRefund = razorpay.payments.refund(payment.getRazorpayPaymentId(), refundRequest);
            refund.setGatewayRefundId(razorpayRefund.get("id"));
            refund.setStatus("processed".equals(razorpayRefund.get("status")) ? "COMPLETED" : "PENDING");
            refund.setExpectedCredit("5-7 business days to the original payment method");
            payment.setStatus("REFUNDED_NO_ORDER");
            log.warn("Refunded payment {} (Rs {}): paid but no order was placed",
                    payment.getRazorpayPaymentId(), payment.getAmount());
        } catch (Exception e) {
            refund.setStatus("FAILED");
            refund.setReason(refund.getReason() + " [Gateway refund failed: " + e.getMessage() + "]");
            payment.setStatus("REFUND_FAILED_NO_ORDER");
            log.error("Could not refund payment {} (Rs {}) that has no order - refund it by hand in Razorpay: {}",
                    payment.getRazorpayPaymentId(), payment.getAmount(), e.getMessage(), e);
        }
        refundRepo.save(refund);
        paymentRepo.save(payment);
    }

    public String getKeyId() {
        return keyId;
    }

    public Map<String, Object> createRazorpayOrder(String authHeader, Map<String, Object> body) {
        Object amountObj = body.get("amount");
        if (amountObj == null) {
            throw new IllegalArgumentException("amount is required");
        }
        double amount = Double.parseDouble(amountObj.toString());
        // No internal Order row exists yet at this point (it is created only after payment
        // verification succeeds), so the receipt is derived from the client-supplied orderId
        // when present, falling back to a timestamp-based reference otherwise.
        Object orderIdObj = body.get("orderId");
        String receipt = "order_rcpt_" + (orderIdObj != null ? orderIdObj.toString() : System.currentTimeMillis());
        try {
            com.razorpay.Order razorpayOrder = createRazorpayOrder(receipt, amount);
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("razorpayOrderId", razorpayOrder.get("id"));
            result.put("amount", razorpayOrder.get("amount"));
            result.put("currency", razorpayOrder.get("currency"));
            result.put("key", keyId);
            return result;
        } catch (Exception e) {
            log.error("Razorpay order creation failed (amount={}, receipt={}): {}", amount, receipt, e.getMessage(), e);
            throw new RuntimeException("Failed to create Razorpay order", e);
        }
    }

    @Transactional
    public Payment verifyPayment(Map<String, String> body) {
        String razorpayOrderId = body.get("razorpay_order_id");
        String razorpayPaymentId = body.get("razorpay_payment_id");
        String signature = body.get("razorpay_signature");

        // Cash on delivery chosen inside Razorpay's checkout: no money moves, so Razorpay sends no
        // signature. It is confirmed by asking Razorpay itself, never by trusting the browser.
        if (signature == null || signature.isBlank()) {
            return verifyCodPayment(razorpayOrderId, razorpayPaymentId);
        }

        if (!verifySignature(razorpayOrderId, razorpayPaymentId, signature)) {
            throw new RuntimeException("Payment verification failed");
        }

        // The signature only proves Razorpay genuinely processed *some* payment with this
        // order/payment id pair - it says nothing about which of our orders it was for or how
        // much was actually charged. Both of those are attacker-controlled in the request body,
        // so trust neither: fetch the authoritative charged amount from Razorpay directly, and
        // verify the caller actually owns the order before marking it paid.
        double verifiedAmountRupees;
        try {
            RazorpayClient razorpay = new RazorpayClient(keyId, keySecret);
            com.razorpay.Payment razorpayPayment = razorpay.payments.fetch(razorpayPaymentId);
            if (!"captured".equals(razorpayPayment.get("status")) && !"authorized".equals(razorpayPayment.get("status"))) {
                throw new RuntimeException("Payment has not been captured by Razorpay");
            }
            if (!razorpayOrderId.equals(razorpayPayment.get("order_id"))) {
                throw new RuntimeException("Payment does not belong to the given order");
            }
            int verifiedAmountPaise = razorpayPayment.get("amount");
            verifiedAmountRupees = verifiedAmountPaise / 100.0;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch payment {} from Razorpay for verification: {}", razorpayPaymentId, e.getMessage(), e);
            throw new RuntimeException("Unable to verify payment with Razorpay");
        }

        // The webhook may have recorded this payment already; reuse that row so the payment is
        // never stored twice. One that was already refunded for having no order stays refunded.
        Payment payment = paymentRepo.findByRazorpayPaymentId(razorpayPaymentId).orElseGet(Payment::new);
        if (payment.getStatus() != null && payment.getStatus().contains("NO_ORDER")) {
            throw new RuntimeException("This payment was refunded because no order was placed in time");
        }
        payment.setRazorpayOrderId(razorpayOrderId);
        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setRazorpaySignature(signature);
        payment.setStatus("COMPLETED");
        payment.setPaidAt(LocalDateTime.now());
        payment.setAmount(verifiedAmountRupees);
        if (body.containsKey("currency")) {
            payment.setCurrency(body.get("currency"));
        }
        if (body.containsKey("orderId")) {
            Long orderId = Long.valueOf(body.get("orderId"));

            Order order = orderRepo.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            Long currentUserId = authorizationService.getCurrentUserId();
            if (order.getUser() == null || !order.getUser().getId().equals(currentUserId)) {
                throw new com.cauverystore.exception.AccessDeniedException("This order does not belong to the current user");
            }
            if (order.isPaid()) {
                throw new RuntimeException("This order has already been paid for");
            }
            if (Math.abs(verifiedAmountRupees - order.getTotalAmount()) > 1.0) {
                throw new RuntimeException("Paid amount does not match the order total");
            }

            payment.setOrderId(orderId);
            order.setPaid(true);
            orderRepo.save(order);
        }

        return paymentRepo.save(payment);
    }

    /**
     * Confirms a cash-on-delivery choice made in Razorpay's checkout.
     *
     * The payment must exist at Razorpay, belong to this Razorpay order, be of method "cod" and be
     * pending. It is stored as COD_PENDING with no paid time, so the unclaimed-payment refund job
     * (which only looks at taken money) leaves it alone, and the order stays unpaid until
     * OrderService records the cash at delivery.
     */
    @Transactional
    public Payment verifyCodPayment(String razorpayOrderId, String razorpayPaymentId) {
        if (razorpayOrderId == null || razorpayOrderId.isBlank()
                || razorpayPaymentId == null || razorpayPaymentId.isBlank()) {
            throw new RuntimeException("Payment verification failed");
        }
        double amountRupees;
        try {
            RazorpayClient razorpay = new RazorpayClient(keyId, keySecret);
            com.razorpay.Payment rp = razorpay.payments.fetch(razorpayPaymentId);
            String method = rp.get("method");
            String status = rp.get("status");
            if (!"cod".equals(method) || !"pending".equals(status)) {
                throw new RuntimeException("Payment verification failed");
            }
            if (!razorpayOrderId.equals(rp.get("order_id"))) {
                throw new RuntimeException("Payment does not belong to the given order");
            }
            int amountPaise = rp.get("amount");
            amountRupees = amountPaise / 100.0;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch COD payment {} from Razorpay: {}", razorpayPaymentId, e.getMessage(), e);
            throw new RuntimeException("Unable to verify payment with Razorpay");
        }
        Payment payment = paymentRepo.findByRazorpayPaymentId(razorpayPaymentId).orElseGet(Payment::new);
        payment.setRazorpayOrderId(razorpayOrderId);
        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setAmount(amountRupees);
        payment.setCurrency("INR");
        if (payment.getStatus() == null) payment.setStatus("COD_PENDING");
        return paymentRepo.save(payment);
    }

    private static boolean isCodPayment(Payment p) {
        return p.getStatus() != null && p.getStatus().startsWith("COD");
    }

    // Creates the Refund bookkeeping record AND actually moves the money back via Razorpay's
    // refund API when the order was paid online. Never throws - a gateway failure marks the
    // record FAILED instead of blocking whatever order-lifecycle transaction called this
    // (mirrors how EmailService/GST-invoice failures are already swallowed elsewhere), so
    // customer-facing order cancellation can never be broken by a refund-side outage.
    /**
     * Refunds a return, back to whatever paid for it.
     *
     * <h2>Why this is not just processRefundForOrder with an extra argument</h2>
     *
     * A cancellation happens once. A return can be pressed twice - by two people looking at the
     * same queue, or by one person on a slow connection - and the only thing that stood between
     * that and paying a customer twice was nobody doing it. So this looks for a refund already
     * raised against the same return before it moves any money, and treats a previous FAILED
     * attempt as retryable while a successful one is final.
     *
     * <h2>Cash on delivery</h2>
     *
     * There is no payment to reverse, so nothing can be sent back down the rail it arrived on.
     * That is recorded as awaiting a manual transfer rather than quietly marked COMPLETED, which
     * would tell the books the customer had been paid when nobody had paid them.
     *
     * @param speedRequested Razorpay's wording - "optimum" tries the instant rail and falls back,
     *                       "normal" is the ordinary 5-7 working day route.
     */
    @Transactional
    public Refund processRefundForReturn(Long returnRequestId, Long orderId, double amountRupees,
                                         String reason, String speedRequested) {
        for (Refund existing : refundRepo.findByReturnRequestId(returnRequestId)) {
            if (!"FAILED".equals(existing.getStatus())) {
                log.info("Refund already raised for return {} (refund {}, status {}) - not repeating",
                        returnRequestId, existing.getId(), existing.getStatus());
                return existing;
            }
        }

        Refund refund = new Refund();
        refund.setReturnRequestId(returnRequestId);
        refund.setOrderId(orderId);
        refund.setAmount(amountRupees);
        refund.setRefundDate(LocalDate.now());
        refund.setReason(reason);

        Optional<Payment> paymentOpt = paymentRepo.findByOrderId(orderId);
        if (paymentOpt.isEmpty() || paymentOpt.get().getRazorpayPaymentId() == null
                || isCodPayment(paymentOpt.get())) {
            // Cash on delivery, or a linkage that has broken. Either way there is nothing to
            // reverse, and saying so is the only honest outcome - somebody has to pay this by
            // hand, and the queue is how they find out.
            refund.setStatus("AWAITING_MANUAL_TRANSFER");
            refund.setRefundMethod("MANUAL_BANK_TRANSFER");
            refund.setExpectedCredit("Awaiting manual bank transfer - no online payment to reverse");
            return refundRepo.save(refund);
        }

        Payment payment = paymentOpt.get();
        String speed = ("optimum".equalsIgnoreCase(speedRequested)) ? "optimum" : "normal";
        refund.setRefundMethod("ORIGINAL_METHOD");
        try {
            RazorpayClient razorpay = new RazorpayClient(keyId, keySecret);
            JSONObject refundRequest = new JSONObject();
            refundRequest.put("amount", (int) Math.round(amountRupees * 100));
            refundRequest.put("speed", speed);
            // Razorpay rejects a repeat of the same key, so a retry that crossed with a response
            // we never saw cannot become a second payout.
            JSONObject notes = new JSONObject();
            notes.put("return_request_id", String.valueOf(returnRequestId));
            refundRequest.put("notes", notes);
            refundRequest.put("receipt", "RET-" + returnRequestId);

            com.razorpay.Refund razorpayRefund = razorpay.payments.refund(payment.getRazorpayPaymentId(), refundRequest);
            String gatewayStatus = razorpayRefund.get("status");
            refund.setGatewayRefundId(razorpayRefund.get("id"));
            // Razorpay reports the speed it actually managed, which is not always the one asked
            // for - an instant refund is only possible on some rails.
            String speedProcessed = safeString(razorpayRefund, "speed_processed");
            refund.setSpeed(speedProcessed != null ? speedProcessed : speed);
            refund.setStatus("processed".equals(gatewayStatus) ? "COMPLETED" : "PENDING");
            refund.setExpectedCredit("instant".equals(refund.getSpeed())
                    ? "Credited immediately to the original payment method"
                    : "5-7 business days to the original payment method");
        } catch (Exception e) {
            log.error("Razorpay refund failed for return {} (orderId={}, paymentId={}, amount={}): {}",
                    returnRequestId, orderId, payment.getRazorpayPaymentId(), amountRupees, e.getMessage(), e);
            refund.setStatus("FAILED");
            refund.setExpectedCredit("Refund could not be sent - being retried");
            refund.setReason((reason != null ? reason + " " : "") + "[Gateway refund failed: " + e.getMessage() + "]");
        }
        return refundRepo.save(refund);
    }

    /** Razorpay omits fields it has no value for, and reading one absent throws. */
    private String safeString(com.razorpay.Refund r, String field) {
        try {
            return r.get(field);
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public Refund processRefundForOrder(Long orderId, double amountRupees, String reason) {
        Refund refund = new Refund();
        refund.setOrderId(orderId);
        refund.setAmount(amountRupees);
        refund.setRefundDate(LocalDate.now());
        refund.setReason(reason);

        Optional<Payment> paymentOpt = paymentRepo.findByOrderId(orderId);
        if (paymentOpt.isPresent() && isCodPayment(paymentOpt.get())) {
            // Cash was collected at the door: nothing to reverse at Razorpay, so it is paid back by hand.
            refund.setStatus("AWAITING_MANUAL_TRANSFER");
            refund.setRefundMethod("MANUAL_BANK_TRANSFER");
            refund.setExpectedCredit("Awaiting manual bank transfer - cash on delivery has no online payment to reverse");
            return refundRepo.save(refund);
        }
        if (paymentOpt.isEmpty() || paymentOpt.get().getRazorpayPaymentId() == null) {
            // A paid order should always have a Razorpay payment linked to it (placeOrder links the
            // captured payment to the order). Every caller only invokes this after order.isPaid(),
            // so reaching here without a linked payment means the linkage is broken - do NOT mark
            // the refund COMPLETED, or the customer's money is never actually returned while the
            // books claim it was. Surface it as FAILED for manual intervention instead.
            refund.setStatus("FAILED");
            refund.setReason((reason != null ? reason + " " : "")
                    + "[No linked Razorpay payment found for paid order - refund not executed]");
            return refundRepo.save(refund);
        }

        Payment payment = paymentOpt.get();
        try {
            RazorpayClient razorpay = new RazorpayClient(keyId, keySecret);
            JSONObject refundRequest = new JSONObject();
            refundRequest.put("amount", (int) Math.round(amountRupees * 100));
            com.razorpay.Refund razorpayRefund = razorpay.payments.refund(payment.getRazorpayPaymentId(), refundRequest);
            String gatewayStatus = razorpayRefund.get("status");
            refund.setGatewayRefundId(razorpayRefund.get("id"));
            refund.setStatus("processed".equals(gatewayStatus) ? "COMPLETED" : "PENDING");
        } catch (Exception e) {
            log.error("Razorpay refund failed for order {} (paymentId={}, amount={}): {}",
                    orderId, payment.getRazorpayPaymentId(), amountRupees, e.getMessage(), e);
            refund.setStatus("FAILED");
            refund.setReason((reason != null ? reason + " " : "") + "[Gateway refund failed: " + e.getMessage() + "]");
        }
        return refundRepo.save(refund);
    }
}
