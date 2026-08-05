package com.cauverystore.client;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface MarketplaceClient {

    String getName();

    default boolean isSimulated() {
        return true;
    }

    List<Map<String, Object>> fetchOrders(String channel, LocalDate from, LocalDate to, int limit);

    List<Map<String, Object>> fetchProducts(String channel);

    Map<String, Object> pushInventory(String channel, String sku, Integer stock);

    Map<String, Object> fetchSettlements(String channel, LocalDate from, LocalDate to);

    Map<String, Object> validateChannel(String channel);
}
