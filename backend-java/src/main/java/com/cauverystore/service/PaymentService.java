package com.cauverystore.service;

import com.cauverystore.entities.Order;
import com.cauverystore.entities.Payment;
import com.cauverystore.entities.Refund;
import com.cauverystore.repository.OrderRepository;
import com.cauverystore.repository.PaymentRepository;
import com.cauverystore.repository.RefundRepository;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    @Value("${razorpay.key_id}")
    private String keyId;

    @Value("${razorpay.key_secret}")
    private String keySecret;

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
            String data = orderId + "|" + paymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().equals(signature);
        } catch (Exception e) {
            return false;
        }
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

        Payment payment = new Payment();
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

    // Creates the Refund bookkeeping record AND actually moves the money back via Razorpay's
    // refund API when the order was paid online. Never throws - a gateway failure marks the
    // record FAILED instead of blocking whatever order-lifecycle transaction called this
    // (mirrors how EmailService/GST-invoice failures are already swallowed elsewhere), so
    // customer-facing order cancellation can never be broken by a refund-side outage.
    @Transactional
    public Refund processRefundForOrder(Long orderId, double amountRupees, String reason) {
        Refund refund = new Refund();
        refund.setOrderId(orderId);
        refund.setAmount(amountRupees);
        refund.setRefundDate(LocalDate.now());
        refund.setReason(reason);

        Optional<Payment> paymentOpt = paymentRepo.findByOrderId(orderId);
        if (paymentOpt.isEmpty() || paymentOpt.get().getRazorpayPaymentId() == null) {
            // Order was never actually charged through Razorpay (e.g. COD) - nothing for the
            // gateway to reverse, so the bookkeeping record alone is the whole story.
            refund.setStatus("COMPLETED");
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
