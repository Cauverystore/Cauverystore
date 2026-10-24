package com.cauverystore.util;

import java.time.LocalDate;

/**
 * The marketplace's commission policy in one place, with no framework in it so the figures can
 * be checked on their own.
 *
 * Three tiers, set per category by an admin:
 *   ESSENTIALS  0%   for low-margin everyday goods
 *   STANDARD    10%  for mid-margin goods
 *   HIGH_VALUE  20%  for high-margin goods, but only on items priced at HIGH_VALUE_MIN_PRICE or
 *                    more - a cheaper item in a high-value category pays the standard 10%
 *
 * Every order also pays the same flat handling fee, whatever the tier. Payment costs
 * (Razorpay's gateway fee and its Route transfer fee) are worked out here so they can be taken
 * out of what the seller is paid.
 */
public final class CommissionPolicy {

    private CommissionPolicy() { }

    public enum Tier {
        ESSENTIALS(0.0),
        STANDARD(10.0),
        HIGH_VALUE(20.0);

        private final double percent;

        Tier(double percent) { this.percent = percent; }

        public double percent() { return percent; }
    }

    /** Flat fee per order, the same for every product and tier. */
    public static final double HANDLING_FEE = 10.0;

    /** Below this unit price a HIGH_VALUE category pays STANDARD_PERCENT instead. */
    public static final double HIGH_VALUE_MIN_PRICE = 1000.0;

    public static final double STANDARD_PERCENT = 10.0;

    /** A new seller's launch offer: no commission for this long, for the first N sellers. */
    public static final int LAUNCH_OFFER_DAYS = 60;
    public static final int LAUNCH_OFFER_SELLERS = 50;
    public static final String LAUNCH_OFFER_NOTE = "LAUNCH_OFFER";

    /** Sellers are paid this many days after delivery - a day after the 7-day return window. */
    public static final int PAYOUT_DELAY_DAYS = 8;

    /** Razorpay's platform fee, its marketplace transfer fee, and the GST on both. */
    public static final double GATEWAY_RATE = 0.02;
    public static final double TRANSFER_RATE = 0.0025;
    public static final double FEE_GST = 0.18;

    /** The percentage charged on one item of this tier at this unit price. */
    public static double percentFor(Tier tier, double unitPrice) {
        if (tier == Tier.HIGH_VALUE && unitPrice < HIGH_VALUE_MIN_PRICE) {
            return STANDARD_PERCENT;
        }
        return tier.percent();
    }

    /**
     * One percentage for an order of several items, weighted by what each item contributes.
     * Used so an order mixing a cheap and an expensive item is charged between the two rates
     * rather than at whichever item came first.
     *
     * @param itemValues  unit price x quantity of each item
     * @param itemPercents the percentage that applies to each item
     * @param fallback    used when the items carry no value to weigh by
     */
    public static double blendedPercent(double[] itemValues, double[] itemPercents, double fallback) {
        if (itemValues == null || itemPercents == null || itemValues.length != itemPercents.length) {
            return fallback;
        }
        double total = 0.0;
        double weighted = 0.0;
        for (int i = 0; i < itemValues.length; i++) {
            if (itemValues[i] <= 0) continue;
            total += itemValues[i];
            weighted += itemValues[i] * itemPercents[i];
        }
        return total <= 0 ? fallback : round(weighted / total);
    }

    /** Razorpay's platform fee on the full amount the customer paid, with GST. */
    public static double gatewayFee(double orderValue) {
        return round(orderValue * GATEWAY_RATE * (1 + FEE_GST));
    }

    /** Razorpay's transfer fee on the amount sent on to the seller, with GST. */
    public static double transferFee(double amountToSeller) {
        return round(Math.max(0.0, amountToSeller) * TRANSFER_RATE * (1 + FEE_GST));
    }

    /** What one order comes to for a seller, before any tax collected at source and refunds. */
    public record Settlement(double orderValue, double commission, double handlingFee,
                             double gatewayFee, double transferFee, double sellerNet) { }

    /**
     * @param commission  the percentage commission only, not the handling fee
     * @param handlingFee the flat per-order fee
     */
    public static Settlement settle(double orderValue, double commission, double handlingFee) {
        double toSeller = orderValue - commission - handlingFee;
        double gateway = gatewayFee(orderValue);
        double transfer = transferFee(toSeller);
        double net = round(orderValue - commission - handlingFee - gateway - transfer);
        return new Settlement(round(orderValue), round(commission), round(handlingFee),
                gateway, transfer, net);
    }

    /** The first day a seller can be paid for an order delivered on this date. */
    public static LocalDate payableFrom(LocalDate deliveredOn) {
        return deliveredOn == null ? null : deliveredOn.plusDays(PAYOUT_DELAY_DAYS);
    }

    public static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
