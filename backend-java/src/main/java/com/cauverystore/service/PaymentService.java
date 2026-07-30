package com.cauverystore.service;

import com.cauverystore.entities.Order;
import com.cauverystore.entities.Payment;
import com.cauverystore.repository.OrderRepository;
import com.cauverystore.repository.PaymentRepository;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class PaymentService {

    @Value("${razorpay.key_id}")
    private String keyId;

    @Value("${razorpay.key_secret}")
    private String keySecret;

    private final PaymentRepository paymentRepo;
    private final OrderRepository orderRepo;

    public PaymentService(PaymentRepository paymentRepo, OrderRepository orderRepo) {
        this.paymentRepo = paymentRepo;
        this.orderRepo = orderRepo;
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

        Payment payment = new Payment();
        payment.setRazorpayOrderId(razorpayOrderId);
        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setRazorpaySignature(signature);
        payment.setStatus("COMPLETED");
        payment.setPaidAt(LocalDateTime.now());

        if (body.containsKey("amount")) {
            payment.setAmount(Double.parseDouble(body.get("amount")));
        }
        if (body.containsKey("currency")) {
            payment.setCurrency(body.get("currency"));
        }
        if (body.containsKey("orderId")) {
            Long orderId = Long.valueOf(body.get("orderId"));
            payment.setOrderId(orderId);

            Order order = orderRepo.findById(orderId).orElse(null);
            if (order != null) {
                order.setPaid(true);
                orderRepo.save(order);
            }
        }

        return paymentRepo.save(payment);
    }
}
