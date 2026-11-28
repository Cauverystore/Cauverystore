package com.cauverystore.service;

import com.cauverystore.entities.CartItem;
import com.cauverystore.entities.Product;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeliveryChargeServiceTest {

    private final DeliveryChargeService rates = DeliveryChargeService.defaults();

    private static CartItem item(Double weight, Double packageWeight, int qty, boolean saved) {
        Product p = new Product();
        p.setWeight(weight);
        p.setPackageWeight(packageWeight);
        CartItem ci = new CartItem();
        ci.setProduct(p);
        ci.setQuantity(qty);
        ci.setSavedForLater(saved);
        return ci;
    }

    @Test
    void slabsFollowTheParcelWeight() {
        assertEquals(0, rates.chargeForWeight(0));
        assertEquals(65, rates.chargeForWeight(0.25));
        assertEquals(65, rates.chargeForWeight(0.5));
        assertEquals(100, rates.chargeForWeight(0.51));
        assertEquals(100, rates.chargeForWeight(1.0));
        assertEquals(135, rates.chargeForWeight(1.385));
        assertEquals(135, rates.chargeForWeight(1.5));
        assertEquals(170, rates.chargeForWeight(1.6), "first extra half kilo");
        assertEquals(170, rates.chargeForWeight(2.0));
        assertEquals(205, rates.chargeForWeight(2.1));
    }

    @Test
    void basketWeightUsesPackageWeightThenWeightThenDefault() {
        assertEquals(1.385, rates.weightKg(List.of(item(1.0, 1.385, 1, false))), 1e-9);
        assertEquals(0.75, rates.weightKg(List.of(item(0.25, null, 3, false))), 1e-9);
        assertEquals(0.5, rates.weightKg(List.of(item(null, null, 1, false))), 1e-9, "no weight counts as 0.5 kg");
        assertEquals(0.25, rates.weightKg(List.of(item(0.25, null, 1, false), item(5.0, null, 1, true))), 1e-9,
                "saved-for-later items are not sent");
    }

    @Test
    void chargeIsAlwaysAddedByDefault_evenForBigOrders() {
        assertEquals(100, rates.chargeFor(List.of(item(0.749, null, 1, false)), 5000));
        assertEquals(0, rates.chargeFor(List.of(), 100), "nothing to send");
    }

    @Test
    void freeDeliveryThresholdOnlyWhenConfigured() {
        DeliveryChargeService withThreshold = new DeliveryChargeService("0.5:65,1:100,1.5:135", 35, 0.5, 999);
        assertEquals(65, withThreshold.chargeFor(List.of(item(0.3, null, 1, false)), 998));
        assertEquals(0, withThreshold.chargeFor(List.of(item(0.3, null, 1, false)), 999));
    }

    @Test
    void badSlabSettingIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new DeliveryChargeService("0.5-65", 35, 0.5, 0));
    }
}
