package com.cauverystore.service;

import com.cauverystore.client.MarketplaceClient;
import com.cauverystore.entities.Product;
import com.cauverystore.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MarketplaceImportService {

    private final MarketplaceClient marketplaceClient;
    private final ProductRepository productRepo;

    public MarketplaceImportService(MarketplaceClient marketplaceClient, ProductRepository productRepo) {
        this.marketplaceClient = marketplaceClient;
        this.productRepo = productRepo;
    }

    public Map<String, Object> importOrders(String channel, LocalDate from, LocalDate to, int limit) {
        String normalized = normalize(channel);
        List<Map<String, Object>> orders = marketplaceClient.fetchOrders(normalized,
                from != null ? from : LocalDate.now().minusDays(7),
                to != null ? to : LocalDate.now(),
                limit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("channel", normalized);
        result.put("adapter", marketplaceClient.getName());
        result.put("simulated", marketplaceClient.isSimulated());
        result.put("ordersImported", orders.size());
        result.put("orders", orders);
        result.put("message", "Orders fetched from " + normalized + " (simulated). Real credentials required for live sync.");
        return result;
    }

    @Transactional
    public Map<String, Object> syncInventory(String channel, Long sellerId) {
        String normalized = normalize(channel);
        List<Product> products = new ArrayList<>();
        if (sellerId != null) {
            List<Product> byChannel = productRepo.findByChannelAndActiveTrue(normalized);
            List<Product> byAll = productRepo.findByChannelAndActiveTrue("ALL");
            for (Product p : byChannel) {
                if (sellerId.equals(p.getSellerId())) products.add(p);
            }
            for (Product p : byAll) {
                if (sellerId.equals(p.getSellerId())) products.add(p);
            }
        } else {
            products.addAll(productRepo.findByChannelAndActiveTrue(normalized));
            products.addAll(productRepo.findByChannelAndActiveTrue("ALL"));
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Product p : products) {
            String sku = p.getSku() != null && !p.getSku().isBlank() ? p.getSku() : p.getProductCode();
            results.add(marketplaceClient.pushInventory(normalized, sku, p.getStock()));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("channel", normalized);
        result.put("adapter", marketplaceClient.getName());
        result.put("simulated", marketplaceClient.isSimulated());
        result.put("productsSynced", results.size());
        result.put("results", results);
        return result;
    }

    public Map<String, Object> fetchProducts(String channel) {
        String normalized = normalize(channel);
        List<Map<String, Object>> products = marketplaceClient.fetchProducts(normalized);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("channel", normalized);
        result.put("adapter", marketplaceClient.getName());
        result.put("simulated", marketplaceClient.isSimulated());
        result.put("products", products);
        return result;
    }

    public Map<String, Object> fetchSettlements(String channel, LocalDate from, LocalDate to) {
        String normalized = normalize(channel);
        return marketplaceClient.fetchSettlements(normalized,
                from != null ? from : LocalDate.now().minusMonths(1),
                to != null ? to : LocalDate.now());
    }

    private String normalize(String channel) {
        if (channel == null || channel.trim().isEmpty()) {
            throw new IllegalArgumentException("Channel is required");
        }
        return channel.trim().toUpperCase(Locale.ROOT);
    }
}
