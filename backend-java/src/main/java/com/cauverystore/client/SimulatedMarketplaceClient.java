package com.cauverystore.client;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class SimulatedMarketplaceClient implements MarketplaceClient {

    private static final List<String> SUPPORTED = List.of("AMAZON", "FLIPKART", "SHOPIFY", "WOOCOMMERCE");

    @Override
    public String getName() {
        return "MARKETPLACE_SIMULATED";
    }

    @Override
    public boolean isSimulated() {
        return true;
    }

    @Override
    public List<Map<String, Object>> fetchOrders(String channel, LocalDate from, LocalDate to, int limit) {
        validateChannel(channel);
        List<Map<String, Object>> orders = new ArrayList<>();
        for (int i = 1; i <= (limit > 0 ? limit : 5); i++) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("marketplaceOrderId", channel.toLowerCase(Locale.ROOT) + "-ORD-" + String.format("%08d", i));
            o.put("channel", channel);
            o.put("orderDate", LocalDate.now().minusDays(i).toString());
            o.put("buyerName", "Simulated Buyer " + i);
            o.put("buyerGstin", "");
            o.put("buyerState", "Tamil Nadu");
            o.put("items", List.of(Map.of(
                    "name", "Simulated Product " + i,
                    "sku", channel.toLowerCase(Locale.ROOT) + "-SKU-" + i,
                    "quantity", 1,
                    "unitPrice", 999.0 + i)));
            o.put("totalAmount", 999.0 + i);
            o.put("simulated", true);
            orders.add(o);
        }
        return orders;
    }

    @Override
    public List<Map<String, Object>> fetchProducts(String channel) {
        validateChannel(channel);
        List<Map<String, Object>> products = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("sku", channel.toLowerCase(Locale.ROOT) + "-SKU-" + i);
            p.put("name", "Simulated Product " + i);
            p.put("price", 999.0 + i);
            p.put("stock", 10 + i);
            p.put("channel", channel);
            p.put("simulated", true);
            products.add(p);
        }
        return products;
    }

    @Override
    public Map<String, Object> pushInventory(String channel, String sku, Integer stock) {
        validateChannel(channel);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("channel", channel);
        resp.put("sku", sku);
        resp.put("stock", stock);
        resp.put("status", "SYNCED");
        resp.put("syncedAt", LocalDate.now().toString());
        resp.put("simulated", true);
        return resp;
    }

    @Override
    public Map<String, Object> fetchSettlements(String channel, LocalDate from, LocalDate to) {
        validateChannel(channel);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("channel", channel);
        resp.put("from", from.toString());
        resp.put("to", to.toString());
        resp.put("totalSettlement", 0.0);
        resp.put("transactions", 0);
        resp.put("simulated", true);
        resp.put("message", "Settlement data fetched (simulated). Configure real credentials for live data.");
        return resp;
    }

    @Override
    public Map<String, Object> validateChannel(String channel) {
        if (channel == null || channel.trim().isEmpty()) {
            throw new IllegalArgumentException("Channel is required");
        }
        String normalized = channel.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported channel: " + channel
                    + ". Supported channels: " + SUPPORTED);
        }
        return Map.of("channel", normalized, "supported", true);
    }
}
