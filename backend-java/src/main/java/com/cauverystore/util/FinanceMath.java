package com.cauverystore.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money arithmetic for the seller ledger, in whole paise.
 *
 * Everything here is a long number of paise, never a double, so a ledger that has been adding and
 * subtracting for a year still adds up to the rupee. There is no Spring and no database in this
 * class on purpose: it is the part that has to be right, and the part that is easiest to test.
 *
 * The rates come from {@link CommissionPolicy} so the figures a seller is promised and the
 * figures the ledger books cannot drift apart.
 */
public final class FinanceMath {

    private FinanceMath() { }

    /** TCS under section 52 CGST. Confirm the current rate with the accountant before relying on it. */
    public static final double TCS_RATE_PERCENT = 0.5;

    /** GST charged on the marketplace's own fees (commission and handling). */
    public static final double FEE_GST_RATE = CommissionPolicy.FEE_GST;

    /** Used only when no GST invoice exists yet to take the real taxable value from. */
    public static final double ASSUMED_ORDER_GST = 0.18;

    public static long paise(double rupees) {
        return BigDecimal.valueOf(rupees).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static double rupees(long paise) {
        return BigDecimal.valueOf(paise).movePointLeft(2).doubleValue();
    }

    /** {@code paise * numerator / denominator}, rounded half up. */
    public static long scale(long paise, long numerator, long denominator) {
        if (denominator == 0) return 0;
        return BigDecimal.valueOf(paise).multiply(BigDecimal.valueOf(numerator))
                .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** {@code paise * rate}, rounded half up. {@code rate} is a fraction (0.02 means 2 per cent). */
    public static long times(long paise, double rate) {
        return BigDecimal.valueOf(paise).multiply(BigDecimal.valueOf(rate))
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** What one delivered order comes to for the seller. Every field except net is a positive deduction. */
    public record Breakdown(long sale, long commission, long handling, long paymentFees,
                            long gstOnFees, long tcs, long net) { }

    /**
     * @param sale       what the customer paid, in paise
     * @param commission the percentage commission only, not the handling fee
     * @param handling   the flat per-order fee
     * @param tcs        tax collected at source on this order
     */
    public static Breakdown breakdown(long sale, long commission, long handling, long tcs) {
        long gateway = times(sale, CommissionPolicy.GATEWAY_RATE * (1 + CommissionPolicy.FEE_GST));
        long toSeller = Math.max(0L, sale - commission - handling);
        long transfer = times(toSeller, CommissionPolicy.TRANSFER_RATE * (1 + CommissionPolicy.FEE_GST));
        long paymentFees = gateway + transfer;
        long gstOnFees = times(commission + handling, FEE_GST_RATE);
        long net = sale - commission - handling - paymentFees - gstOnFees - tcs;
        return new Breakdown(sale, commission, handling, paymentFees, gstOnFees, tcs, net);
    }

    /** TCS when the order has no GST invoice yet: the rate on the value before tax, assuming 18 per cent GST. */
    public static long estimateTcs(long sale) {
        long taxable = scale(sale, 100, 100 + Math.round(ASSUMED_ORDER_GST * 100));
        return times(taxable, TCS_RATE_PERCENT / 100.0);
    }

    /** The share of {@code amount} that a refund of {@code refund} out of an order of {@code orderValue} reverses. */
    public static long prorate(long amount, long refund, long orderValue) {
        if (orderValue <= 0) return 0;
        return scale(amount, Math.min(refund, orderValue), orderValue);
    }
}
