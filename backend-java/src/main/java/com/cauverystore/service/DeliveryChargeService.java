package com.cauverystore.service;

import com.cauverystore.entities.CartItem;
import com.cauverystore.entities.Product;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The delivery charge for a basket, charged on top of the product prices (which never go above
 * MRP). It follows the parcel's weight, because the courier charges by weight: a flat Rs 40 with
 * free delivery from Rs 500 left most orders paying less than the courier costs.
 *
 * Weight is the product's package weight when set, else its weight, else a default, times the
 * quantity. The charge is the first weight slab that fits, plus a fixed amount for every half
 * kilo above the last slab. The cart and the order both use this, so what the shopper sees is
 * what they are charged.
 */
@Service
public class DeliveryChargeService {

    private final List<double[]> slabs;
    private final double extraPerHalfKg;
    private final double defaultItemKg;
    private final double freeAbove;

    public DeliveryChargeService(
            @Value("${delivery.slabs:0.5:65,1:100,1.5:135}") String slabs,
            @Value("${delivery.extra-per-half-kg:35}") double extraPerHalfKg,
            @Value("${delivery.default-item-kg:0.5}") double defaultItemKg,
            @Value("${delivery.free-above:0}") double freeAbove) {
        this.slabs = parse(slabs);
        this.extraPerHalfKg = extraPerHalfKg;
        this.defaultItemKg = defaultItemKg;
        this.freeAbove = freeAbove;
    }

    /** The standard rates, for code built without Spring (tests). */
    public static DeliveryChargeService defaults() {
        return new DeliveryChargeService("0.5:65,1:100,1.5:135", 35, 0.5, 0);
    }

    private static List<double[]> parse(String text) {
        List<double[]> out = new ArrayList<>();
        for (String part : text.split(",")) {
            String[] kv = part.trim().split(":");
            if (kv.length != 2) throw new IllegalArgumentException("delivery.slabs entry '" + part + "' is not kg:charge");
            out.add(new double[]{Double.parseDouble(kv[0].trim()), Double.parseDouble(kv[1].trim())});
        }
        if (out.isEmpty()) throw new IllegalArgumentException("delivery.slabs is empty");
        out.sort((a, b) -> Double.compare(a[0], b[0]));
        return out;
    }

    /** The charge for a parcel of this many kilos. */
    public double chargeForWeight(double kg) {
        if (kg <= 0) return 0;
        for (double[] slab : slabs) {
            if (kg <= slab[0] + 1e-9) return slab[1];
        }
        double[] last = slabs.get(slabs.size() - 1);
        double halfKilosOver = Math.ceil((kg - last[0]) / 0.5 - 1e-9);
        return last[1] + halfKilosOver * extraPerHalfKg;
    }

    /** One unit of this product, in kilos, for the parcel weight. */
    public double unitWeightKg(Product p) {
        if (p != null) {
            if (p.getPackageWeight() != null && p.getPackageWeight() > 0) return p.getPackageWeight();
            if (p.getWeight() != null && p.getWeight() > 0) return p.getWeight();
        }
        return defaultItemKg;
    }

    /** Total parcel weight of the items being bought (saved-for-later items are left out). */
    public double weightKg(Collection<CartItem> items) {
        double kg = 0;
        for (CartItem item : items) {
            if (item.isSavedForLater()) continue;
            kg += unitWeightKg(item.getProduct()) * Math.max(item.getQuantity(), 0);
        }
        return kg;
    }

    /**
     * The charge for these items with this goods value. Nothing to send costs nothing, and a
     * free-delivery threshold applies only when one is configured (off by default).
     */
    public double chargeFor(Collection<CartItem> items, double goodsValue) {
        double kg = weightKg(items);
        if (kg <= 0) return 0;
        if (freeAbove > 0 && goodsValue >= freeAbove) return 0;
        return chargeForWeight(kg);
    }

    /** The free-delivery threshold, or 0 when there is none. */
    public double freeAbove() {
        return freeAbove;
    }
}
