package com.cauverystore.service;

import java.util.*;

/**
 * What a seller-team member may do, and the built-in roles that bundle those permissions.
 *
 * Kept in code rather than a table because each permission is checked by name in the code that
 * guards it; a permission added only to a table would guard nothing. Roles, and which
 * permissions each role holds, are in the database (seller_team_roles).
 */
public final class SellerPermissions {

    private SellerPermissions() {}

    public static final String DASHBOARD_VIEW = "dashboard.view";
    public static final String PRODUCTS_VIEW = "products.view";
    public static final String PRODUCTS_MANAGE = "products.manage";
    public static final String INVENTORY_MANAGE = "inventory.manage";
    public static final String ORDERS_VIEW = "orders.view";
    public static final String ORDERS_MANAGE = "orders.manage";
    public static final String RETURNS_MANAGE = "returns.manage";
    public static final String ANALYTICS_VIEW = "analytics.view";
    public static final String PAYOUTS_VIEW = "payouts.view";
    public static final String INVOICES_VIEW = "invoices.view";
    public static final String STORE_MANAGE = "store.manage";
    public static final String TAX_MANAGE = "tax.manage";
    public static final String TEAM_MANAGE = "team.manage";

    /** Permission code -> plain description, in the order the Team screen lists them. */
    public static final Map<String, String> ALL;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(DASHBOARD_VIEW, "See the dashboard and notifications");
        m.put(PRODUCTS_VIEW, "See products");
        m.put(PRODUCTS_MANAGE, "Add, edit and remove products");
        m.put(INVENTORY_MANAGE, "Change stock levels");
        m.put(ORDERS_VIEW, "See orders and returns");
        m.put(ORDERS_MANAGE, "Update orders, couriers and shipping labels");
        m.put(RETURNS_MANAGE, "Approve and update returns");
        m.put(ANALYTICS_VIEW, "See sales analytics");
        m.put(PAYOUTS_VIEW, "See payouts");
        // GST pages still read the signed-in person's own records, so for now this has effect only
        // for the account owner; it is offered so roles need no change when that moves over.
        m.put(INVOICES_VIEW, "See GST invoices (account owner only for now)");
        m.put(STORE_MANAGE, "Edit the store profile, policies and licences");
        m.put(TAX_MANAGE, "Edit GSTIN and PAN");
        m.put(TEAM_MANAGE, "Invite and remove team members, manage roles");
        ALL = Collections.unmodifiableMap(m);
    }

    /** A built-in role: code, name, description and its permissions. */
    public record BuiltInRole(String code, String name, String description, Set<String> permissions) {}

    private static Set<String> of(String... p) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(p)));
    }

    public static final List<BuiltInRole> BUILT_IN = List.of(
            new BuiltInRole("OWNER", "Owner", "Full access, including the team and tax details.", ALL.keySet()),
            new BuiltInRole("ADMIN", "Admin", "Runs the store day to day; cannot manage the team.",
                    of(DASHBOARD_VIEW, PRODUCTS_VIEW, PRODUCTS_MANAGE, INVENTORY_MANAGE, ORDERS_VIEW, ORDERS_MANAGE,
                            RETURNS_MANAGE, ANALYTICS_VIEW, PAYOUTS_VIEW, INVOICES_VIEW, STORE_MANAGE, TAX_MANAGE)),
            new BuiltInRole("PRODUCT_MANAGER", "Product manager", "Products and stock.",
                    of(DASHBOARD_VIEW, PRODUCTS_VIEW, PRODUCTS_MANAGE, INVENTORY_MANAGE)),
            new BuiltInRole("ORDER_MANAGER", "Order manager", "Orders, shipping and returns.",
                    of(DASHBOARD_VIEW, PRODUCTS_VIEW, ORDERS_VIEW, ORDERS_MANAGE, RETURNS_MANAGE)),
            new BuiltInRole("ACCOUNTANT", "Accountant", "Payouts, GST invoices and sales figures.",
                    of(DASHBOARD_VIEW, ORDERS_VIEW, ANALYTICS_VIEW, PAYOUTS_VIEW, INVOICES_VIEW)),
            new BuiltInRole("CUSTOMER_SUPPORT", "Customer support", "Looks up orders and handles returns.",
                    of(DASHBOARD_VIEW, PRODUCTS_VIEW, ORDERS_VIEW, RETURNS_MANAGE)),
            new BuiltInRole("WAREHOUSE_MANAGER", "Warehouse manager", "Stock, packing and dispatch.",
                    of(DASHBOARD_VIEW, PRODUCTS_VIEW, INVENTORY_MANAGE, ORDERS_VIEW, ORDERS_MANAGE)));

    public static boolean isKnown(String code) {
        return ALL.containsKey(code);
    }
}
