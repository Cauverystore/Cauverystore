package com.cauverystore.service;

import com.cauverystore.dto.ChatResponse;
import com.cauverystore.entities.Coupon;
import com.cauverystore.entities.Faq;
import com.cauverystore.entities.Product;
import com.cauverystore.entities.WishlistItem;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
public class ChatService {

    private final ProductService productService;
    private final FaqService faqService;
    private final CartService cartService;
    private final WishlistService wishlistService;
    private final OrderService orderService;
    private final CouponService couponService;
    private final AuthorizationService authorizationService;

    public ChatService(ProductService productService, FaqService faqService,
                       CartService cartService, WishlistService wishlistService,
                       OrderService orderService, CouponService couponService,
                       AuthorizationService authorizationService) {
        this.productService = productService;
        this.faqService = faqService;
        this.cartService = cartService;
        this.wishlistService = wishlistService;
        this.orderService = orderService;
        this.couponService = couponService;
        this.authorizationService = authorizationService;
    }

    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "is", "are", "am", "i", "you", "me", "my", "we", "our", "us",
            "to", "for", "of", "in", "on", "at", "do", "does", "did", "and", "or", "but",
            "please", "can", "could", "would", "should", "want", "like", "show", "see", "get",
            "give", "tell", "need", "what", "which", "who", "when", "where", "how", "why",
            "have", "has", "had", "this", "that", "these", "those", "it", "its", "with",
            "about", "from", "by", "as", "if", "be", "been", "will", "your", "yours", "them",
            "they", "their", "there", "here", "not", "so", "very", "some", "any", "also", "then"
    );

    private static final Set<String> GREETINGS = Set.of("hi", "hello", "hey", "namaste", "hola", "hai", "good", "morning", "evening", "afternoon");

    private static final List<String> FAQ_KEYWORDS = List.of(
            "refund", "return", "exchange", "shipping", "delivery", "courier", "payment", "cod",
            "upi", "card", "netbanking", "net banking", "warranty", "cancel", "account", "login",
            "password", "register", "otp", "coupon", "discount", "offer", "contact", "support", "policy", "when"
    );

    private static final List<String[]> BUILT_IN_FAQS = List.of(
            new String[]{"Return & Exchange",
                    "Returns and exchanges are available on eligible items. You can review the full policy and start a return right from the My Orders page; our team processes it within a few business days."},
            new String[]{"Shipping & Delivery",
                    "Standard delivery typically takes 2-5 business days depending on your location. Tracking details are emailed to you as soon as your order ships."},
            new String[]{"Payment Methods",
                    "We accept Cash on Delivery (on eligible pincodes), UPI, credit and debit cards, and net banking. Just pick your preferred method at checkout."},
            new String[]{"Warranty",
                    "Eligible products come with a manufacturer warranty. The warranty period and terms are shown on each product page."},
            new String[]{"Order Cancellation",
                    "You can cancel an order from the My Orders page as long as it hasn't shipped yet. Refunds for cancelled orders are processed back to your original payment method."},
            new String[]{"Coupons & Discounts",
                    "Active coupon codes are listed under Offers. You can apply a coupon at checkout when your order meets the minimum amount."},
            new String[]{"Account & Login",
                    "You can log in with your email and password or continue with Google. If you forget your password, use the \"Forgot Password\" link on the login page."},
            new String[]{"Contact Support",
                    "You can reach our support team through the Contact page, or raise a support ticket and we'll get back to you quickly."}
    );

    private static final List<String> PRICE_SORT_ASC = List.of(
            "lowest priced", "lowest price", "cheapest", "cheap", "least expensive", "most affordable",
            "affordable", "budget", "inexpensive", "low cost", "low priced", "value for money"
    );

    private static final List<String> PRICE_SORT_DESC = List.of(
            "most expensive", "highest priced", "highest price", "expensive", "costly", "pricey",
            "costliest", "high end", "premium", "top priced"
    );

    private static final List<String> PRICE_RANGE_WORDS = List.of(
            "under ", "below", "less than", "more than", "greater than", "over ", "above ",
            "between ", "up to", "upto", "within ", "at most", "at least", "minimum", "maximum", "max"
    );

    public ChatResponse respond(String authHeader, String message) {
        if (message == null || message.trim().isEmpty()) {
            return fallback();
        }
        String msg = message.trim().toLowerCase(Locale.ROOT);
        String intent = classify(msg);

        return switch (intent) {
            case "greeting" -> greeting(authHeader);
            case "help" -> help();
            case "thanks" -> thanks();
            case "contact" -> contact();
            case "cart" -> cartSummary(authHeader);
            case "wishlist" -> wishlistSummary(authHeader);
            case "orders" -> ordersSummary(authHeader);
            case "deals" -> deals();
            case "faq" -> faqAnswer(msg);
            case "product_details" -> productDetails(msg);
            case "product_search" -> productSearch(msg);
            default -> fallback();
        };
    }

    public ChatResponse performAction(String authHeader, Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return errorReply("I couldn't perform that action. Please try again.");
        }
        String type = str(payload.get("type"));
        if (type == null || type.isBlank()) {
            type = str(payload.get("action"));
        }
        if (type == null || type.isBlank()) {
            return errorReply("I couldn't perform that action. Please try again.");
        }

        return switch (type) {
            case "ADD_TO_CART" -> addToCart(authHeader, payload);
            case "REMOVE_FROM_CART" -> removeFromCart(authHeader, payload);
            case "MOVE_TO_CART" -> moveToCart(authHeader, payload);
            case "ADD_TO_WISHLIST" -> addToWishlist(authHeader, payload);
            case "VIEW_PRODUCT" -> viewProduct(payload);
            default -> errorReply("I couldn't perform that action. Please try again.");
        };
    }

    // ---------------------------------------------------------------
    // Intent classification
    // ---------------------------------------------------------------

    private String classify(String msg) {
        String cleaned = msg.replaceAll("[^a-z ]", " ").trim();
        if (!cleaned.isEmpty()) {
            boolean greetingOnly = Arrays.stream(cleaned.split("\\s+")).allMatch(GREETINGS::contains);
            if (greetingOnly) return "greeting";
        }

        if (matchesAny(msg, "help", "what can you do", "assist", "options", "features", "guide")) return "help";
        if (matchesAny(msg, "thank", "thanks", "great", "awesome", "nice", "perfect", "cool", "helpful", "good job")) return "thanks";
        if (matchesAny(msg, "contact", "talk to", "human", "agent", "support person", "reach", "phone number", "support team")) return "contact";

        if (matchesAny(msg, "wishlist", "saved items", "favorite", "favourite", "watchlist", "shortlist", "saved for later")) return "wishlist";
        if (matchesAny(msg, "cart", "bag", "basket", "add to cart", "buy now", "checkout")) return "cart";
        if (matchesAny(msg, "order", "track", "delivery status", "shipped", "delivered", "purchases", "bought", "my order")) return "orders";
        if (matchesAny(msg, "offer", "offers", "deal", "discount", "coupon", "promo", "sale", "savings", "festive")) return "deals";
        if (matchesAny(msg, "refund", "return", "exchange", "shipping", "delivery", "payment", "pay", "cod", "upi",
                "warranty", "cancel order", "how do i", "how to", "faq", "policy", "when will", "return policy", "what is the")) return "faq";
        if (containsAny(msg, PRICE_SORT_ASC) || containsAny(msg, PRICE_SORT_DESC) || containsAny(msg, PRICE_RANGE_WORDS)) return "product_search";
        if (matchesAny(msg, "details", "detail", "tell me more", "about this", "info", "information",
                "specification", "specs", "price", "in stock", "available", "stock", "describe")) return "product_details";

        return "product_search";
    }

    private boolean matchesAny(String msg, String... words) {
        for (String w : words) {
            if (w.length() <= 3) {
                if (Pattern.compile("\\b" + w + "\\b").matcher(msg).find()) return true;
            } else if (msg.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String msg, List<String> words) {
        for (String w : words) {
            if (w.endsWith(" ")) {
                if (msg.contains(w)) return true;
            } else if (w.length() <= 3) {
                if (Pattern.compile("\\b" + w + "\\b").matcher(msg).find()) return true;
            } else if (msg.contains(w)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------
    // Response builders
    // ---------------------------------------------------------------

    private ChatResponse greeting(String authHeader) {
        String name = null;
        if (isAuthed(authHeader)) {
            try {
                String full = authorizationService.getCurrentUser().getFullName();
                if (full != null && !full.isBlank()) name = full.split(" ")[0];
            } catch (Exception ignored) {
            }
        }
        String reply = "Welcome to Cauvery Store"
                + (name != null ? ", " + name : "")
                + "! I'm your shopping assistant. I can help you find products, check your cart and wishlist, track orders, and share the latest offers. What would you like to do?";
        return ChatResponse.create("greeting", reply)
                .quickReplies(List.of("Show me best sellers", "Any offers right now?", "Track my order", "Help"));
    }

    private ChatResponse help() {
        String reply = "Here's what I can do:\n"
                + "1. Find products - try \"show me mobile phones\"\n"
                + "2. Product details - try \"tell me about the Samsung TV\"\n"
                + "3. Your cart - try \"show my cart\"\n"
                + "4. Your wishlist - try \"show my wishlist\"\n"
                + "5. Track orders - try \"where is my order\"\n"
                + "6. Offers & coupons - try \"any offers?\"\n"
                + "7. FAQs - try \"what is your return policy?\"\n"
                + "Just type naturally and I'll do my best!";
        return ChatResponse.create("help", reply)
                .quickReplies(List.of("Show me best sellers", "Show my cart", "Track my order", "Any offers right now?"));
    }

    private ChatResponse thanks() {
        return ChatResponse.create("thanks", "You're welcome! Is there anything else I can help you with?")
                .quickReplies(List.of("Show me best sellers", "Show my cart", "Track my order", "Any offers right now?"));
    }

    private ChatResponse contact() {
        return ChatResponse.create("contact",
                        "You can reach our support team through the Contact Us page, or raise a support ticket and we'll get back to you quickly.")
                .actions(List.of(
                        nav("CONTACT", "Contact Us"),
                        nav("SUPPORT_TICKET", "Raise a Ticket")
                ));
    }

    private ChatResponse cartSummary(String authHeader) {
        if (!isAuthed(authHeader)) return loginPrompt("view your cart");
        try {
            Map<String, Object> cart = cartService.getCartWithDetails(authHeader);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) cart.getOrDefault("items", List.of());
            Object totalItemsObj = cart.get("totalItems");
            Object totalObj = cart.get("totalPrice");
            Object finalObj = cart.get("finalAmount");
            int totalItems = totalItemsObj instanceof Number ? ((Number) totalItemsObj).intValue() : items.size();
            double total = totalObj instanceof Number ? ((Number) totalObj).doubleValue() : 0.0;
            double finalAmount = finalObj instanceof Number ? ((Number) finalObj).doubleValue() : total;

            if (items.isEmpty()) {
                return ChatResponse.create("cart",
                                "Your cart is empty. Let me show you some great products to get started!")
                        .quickReplies(List.of("Show me best sellers", "Any offers right now?", "Help"));
            }

            StringBuilder sb = new StringBuilder("You have " + totalItems + " item(s) in your cart totaling " + inr(total) + ":\n");
            List<Map<String, Object>> actions = new ArrayList<>();
            actions.add(nav("VIEW_CART", "View Cart"));
            actions.add(nav("CHECKOUT", "Proceed to Checkout"));
            for (int i = 0; i < Math.min(items.size(), 3); i++) {
                Map<String, Object> item = items.get(i);
                @SuppressWarnings("unchecked")
                Map<String, Object> product = (Map<String, Object>) item.get("product");
                String pname = product != null ? str(product.get("name")) : "Item";
                Object qty = item.get("quantity");
                Object subtotal = item.get("subtotal");
                sb.append("\n").append(i + 1).append(". ").append(pname)
                        .append(" x ").append(qty instanceof Number ? qty : 1)
                        .append(" - ").append(inr(subtotal instanceof Number ? ((Number) subtotal).doubleValue() : 0));
            }
            sb.append("\n\nFinal amount (incl. tax & delivery): ").append(inr(finalAmount));
            return ChatResponse.create("cart", sb.toString()).actions(actions);
        } catch (Exception e) {
            return errorReply("I couldn't load your cart right now. " + safeMessage(e));
        }
    }

    private ChatResponse wishlistSummary(String authHeader) {
        if (!isAuthed(authHeader)) return loginPrompt("view your wishlist");
        try {
            List<WishlistItem> items = wishlistService.getWishlist(authHeader);
            if (items.isEmpty()) {
                return ChatResponse.create("wishlist",
                                "Your wishlist is empty. Browse the catalog and tap the heart to save items you love!")
                        .quickReplies(List.of("Show me best sellers", "Any offers right now?", "Help"));
            }
            StringBuilder sb = new StringBuilder("You have " + items.size() + " item(s) in your wishlist:\n");
            List<Map<String, Object>> actions = new ArrayList<>();
            actions.add(nav("VIEW_WISHLIST", "View Wishlist"));
            for (int i = 0; i < Math.min(items.size(), 5); i++) {
                WishlistItem item = items.get(i);
                Product p = item.getProduct();
                if (p == null) continue;
                String pname = p.getName();
                sb.append("\n").append(i + 1).append(". ").append(pname).append(" - ").append(priceLabel(p));
                actions.add(action("MOVE_TO_CART", "Move to Cart: " + pname, "productId", p.getId()));
            }
            return ChatResponse.create("wishlist", sb.toString()).actions(actions);
        } catch (Exception e) {
            return errorReply("I couldn't load your wishlist right now. " + safeMessage(e));
        }
    }

    private ChatResponse ordersSummary(String authHeader) {
        if (!isAuthed(authHeader)) return loginPrompt("track your orders");
        try {
            List<Map<String, Object>> orders = orderService.getOrdersByHeader(authHeader, null);
            if (orders.isEmpty()) {
                return ChatResponse.create("orders",
                                "You don't have any orders yet. When you place one, I'll be happy to help you track it!")
                        .quickReplies(List.of("Show me best sellers", "Any offers right now?", "Help"));
            }
            StringBuilder sb = new StringBuilder("You have " + orders.size() + " order(s). Most recent:\n");
            List<Map<String, Object>> actions = new ArrayList<>();
            actions.add(nav("VIEW_ORDERS", "All Orders"));
            for (int i = 0; i < Math.min(orders.size(), 3); i++) {
                Map<String, Object> o = orders.get(i);
                String oid = str(o.get("orderId"));
                String status = str(o.get("status"));
                Object amt = o.get("totalAmount");
                sb.append("\n").append(i + 1).append(". Order #").append(oid)
                        .append(" - ").append(status)
                        .append(" - ").append(inr(amt instanceof Number ? ((Number) amt).doubleValue() : 0));
                try {
                    actions.add(action("TRACK_ORDER", "Track Order #" + oid, "orderId", Long.valueOf(oid)));
                } catch (NumberFormatException ignored) {
                }
            }
            return ChatResponse.create("orders", sb.toString()).actions(actions);
        } catch (Exception e) {
            return errorReply("I couldn't load your orders right now. " + safeMessage(e));
        }
    }

    private ChatResponse deals() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Coupon> coupons = couponService.getAll().stream()
                    .filter(Coupon::isActive)
                    .filter(c -> c.getValidUntil() == null || c.getValidUntil().isAfter(now))
                    .filter(c -> c.getValidFrom() == null || !c.getValidFrom().isAfter(now))
                    .limit(4)
                    .toList();
            List<Product> offerProducts = productService.getActiveProducts().stream()
                    .filter(p -> p.getOfferPrice() != null && p.getOfferPrice() < p.getPrice())
                    .limit(3)
                    .toList();

            StringBuilder sb = new StringBuilder("Here are today's hot deals!\n");
            List<Map<String, Object>> actions = new ArrayList<>();
            actions.add(nav("VIEW_OFFERS", "Browse All Offers"));

            if (!coupons.isEmpty()) {
                sb.append("\nCoupon codes:");
                for (Coupon c : coupons) {
                    String discount = "PERCENTAGE".equalsIgnoreCase(c.getType())
                            ? Math.round(c.getValue()) + "% off"
                            : "Flat " + inr(c.getValue()) + " off";
                    String min = c.getMinOrderAmount() != null && c.getMinOrderAmount() > 0
                            ? " (min order " + inr(c.getMinOrderAmount()) + ")" : "";
                    sb.append("\n  ").append(c.getCode()).append(" - ").append(discount).append(min);
                }
            } else {
                sb.append("\nNo coupon codes active right now, but check back soon!");
            }

            if (!offerProducts.isEmpty()) {
                sb.append("\n\nDiscounted products:");
                for (Product p : offerProducts) {
                    sb.append("\n  ").append(p.getName()).append(" - ").append(priceLabel(p));
                    actions.add(action("VIEW_PRODUCT", "View: " + p.getName(), "productId", p.getId()));
                }
            }
            return ChatResponse.create("deals", sb.toString()).actions(actions);
        } catch (Exception e) {
            return errorReply("I couldn't fetch the latest deals right now. " + safeMessage(e));
        }
    }

    private ChatResponse faqAnswer(String msg) {
        List<String> keywords = FAQ_KEYWORDS.stream().filter(msg::contains).toList();
        String bestQ = null;
        String bestA = null;
        int bestScore = 0;
        for (Faq f : faqService.getActive()) {
            String q = f.getQuestion() == null ? "" : f.getQuestion().toLowerCase(Locale.ROOT);
            int score = 0;
            for (String k : keywords) {
                if (q.contains(k)) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                bestQ = f.getQuestion();
                bestA = f.getAnswer();
            }
        }
        for (String[] ba : BUILT_IN_FAQS) {
            String text = (ba[0] + " " + ba[1]).toLowerCase(Locale.ROOT);
            int score = 0;
            for (String k : keywords) {
                if (text.contains(k)) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                bestQ = ba[0];
                bestA = ba[1];
            }
        }
        if (bestQ != null && bestA != null) {
            return ChatResponse.create("faq", bestQ + "\n\n" + bestA)
                    .quickReplies(List.of("Any offers right now?", "Track my order", "Show my cart", "Help"));
        }
        if (!keywords.isEmpty()) {
            return ChatResponse.create("faq",
                            "I couldn't find an exact answer for that in our help center. Try asking more specifically, or reach out to our support team and we'll be happy to help!")
                    .actions(List.of(nav("CONTACT", "Contact Us"), nav("SUPPORT_TICKET", "Raise a Ticket")));
        }
        return fallback();
    }

    private ChatResponse productSearch(String msg) {
        boolean bestSeller = msg.contains("best seller") || msg.contains("bestseller");
        boolean trending = msg.contains("trending");
        boolean newArrival = msg.contains("new arrival") || msg.contains("new arrivals") || msg.contains("new in") || msg.contains("latest");
        boolean priceAsc = containsAny(msg, PRICE_SORT_ASC);
        boolean priceDesc = containsAny(msg, PRICE_SORT_DESC);
        double[] range = extractPriceRange(msg);
        double minPrice = range[0];
        double maxPrice = range[1];
        boolean priceMode = priceAsc || priceDesc || minPrice > 0 || maxPrice > 0;
        List<String> tokens = tokens(msg);

        List<Product> allActive = productService.getActiveProducts();
        List<Product> pool = allActive.stream()
                .filter(p -> !bestSeller || Boolean.TRUE.equals(p.getBestSeller()))
                .filter(p -> !trending || Boolean.TRUE.equals(p.getTrending()))
                .filter(p -> !newArrival || Boolean.TRUE.equals(p.getNewArrival()))
                .toList();
        if (bestSeller || trending || newArrival) {
            if (pool.isEmpty()) pool = allActive;
        }

        List<Product> ranked;
        String header;
        if (priceMode) {
            List<Product> pricePool = pool.stream()
                    .filter(p -> p.getPrice() != null)
                    .filter(p -> minPrice <= 0 || effectivePrice(p) >= minPrice)
                    .filter(p -> maxPrice <= 0 || effectivePrice(p) <= maxPrice)
                    .collect(Collectors.toList());
            Comparator<Product> byPrice = Comparator.comparingDouble(this::effectivePrice);
            if (priceDesc) pricePool.sort(byPrice.reversed());
            else pricePool.sort(byPrice);
            ranked = pricePool.size() > 6 ? pricePool.subList(0, 6) : pricePool;
            header = priceHeader(msg, priceAsc, priceDesc, minPrice, maxPrice);
        } else if (tokens.isEmpty() || bestSeller || trending || newArrival) {
            ranked = pool.size() > 6 ? pool.subList(0, 6) : pool;
            header = bestSeller ? "Here are our best sellers:\n"
                    : trending ? "Here's what's trending right now:\n"
                    : newArrival ? "Check out our latest arrivals:\n"
                    : "Here's what I found for \"" + msg + "\":\n";
        } else {
            ranked = pool.stream()
                    .filter(p -> scoreProduct(p, tokens) > 0)
                    .sorted((a, b) -> Integer.compare(scoreProduct(b, tokens), scoreProduct(a, tokens)))
                    .toList();
            if (ranked.size() > 6) ranked = ranked.subList(0, 6);
            header = "Here's what I found for \"" + msg + "\":\n";
        }

        if (ranked.isEmpty()) {
            return ChatResponse.create("no_results",
                            "I couldn't find any products matching that. Try a different word, or browse our latest offers!")
                    .quickReplies(List.of("Show me best sellers", "Any offers right now?", "Help"));
        }

        StringBuilder sb = new StringBuilder(header);
        List<Map<String, Object>> actions = new ArrayList<>();
        List<Map<String, Object>> productList = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            Product p = ranked.get(i);
            sb.append("\n").append(i + 1).append(". ").append(p.getName()).append(" - ").append(priceLabel(p));
            productList.add(compactProduct(p));
            if (i < 3) {
                actions.add(action("VIEW_PRODUCT", "View: " + p.getName(), "productId", p.getId()));
                actions.add(action("ADD_TO_CART", "Add to Cart: " + p.getName(), "productId", p.getId()));
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("products", productList);
        return ChatResponse.create("product_search", sb.toString())
                .data(data)
                .actions(actions);
    }

    private ChatResponse productDetails(String msg) {
        List<Product> active = productService.getActiveProducts();
        Product match = null;

        String digits = msg.replaceAll("[^0-9 ]", " ");
        for (String tok : digits.split("\\s+")) {
            if (tok.isEmpty()) continue;
            try {
                Long id = Long.valueOf(tok);
                Product p = productService.getProductById(id);
                if (p != null && p.isActive()) {
                    match = p;
                    break;
                }
            } catch (Exception ignored) {
            }
        }

        if (match == null) {
            List<String> tokens = tokens(msg);
            match = active.stream()
                    .filter(p -> scoreProduct(p, tokens) > 0)
                    .max((a, b) -> Integer.compare(scoreProduct(a, tokens), scoreProduct(b, tokens)))
                    .orElse(null);
        }

        if (match == null) {
            return ChatResponse.create("no_results",
                            "I couldn't find that product. Could you tell me the product name or its id?")
                    .quickReplies(List.of("Show me best sellers", "Any offers right now?", "Help"));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(match.getName());
        if (match.getBrand() != null && !match.getBrand().isBlank()) sb.append(" by ").append(match.getBrand());
        sb.append("\nPrice: ").append(priceLabel(match));
        if (categoryName(match) != null) sb.append("\nCategory: ").append(categoryName(match));
        int stock = match.getStock() == null ? 0 : match.getStock();
        sb.append("\nAvailability: ").append(stock <= 0 ? "Out of stock" : (stock <= 5 ? "Only " + stock + " left" : "In stock"));
        if (match.getDescription() != null && !match.getDescription().isBlank()) {
            String desc = match.getDescription().length() > 220 ? match.getDescription().substring(0, 220) + "..." : match.getDescription();
            sb.append("\n\n").append(desc);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("product", compactProduct(match));
        return ChatResponse.create("product_details", sb.toString())
                .data(data)
                .actions(List.of(
                        action("ADD_TO_CART", "Add to Cart", "productId", match.getId()),
                        action("VIEW_PRODUCT", "View Product", "productId", match.getId())
                ));
    }

    private ChatResponse fallback() {
        return ChatResponse.create("fallback",
                        "I didn't quite catch that. I can help you find products, check your cart and wishlist, track orders, and share offers. Try one of these:")
                .quickReplies(List.of("Show me best sellers", "Any offers right now?", "Track my order", "Help"));
    }

    private ChatResponse loginPrompt(String what) {
        return ChatResponse.create("auth_required",
                        "Please log in to " + what + ". Your cart, wishlist and orders are one login away!")
                .actions(List.of(nav("LOGIN", "Login"), nav("REGISTER", "Create Account")));
    }

    private ChatResponse errorReply(String text) {
        return ChatResponse.create("error", text);
    }

    // ---------------------------------------------------------------
    // Action handlers (server-side execution)
    // ---------------------------------------------------------------

    private ChatResponse addToCart(String authHeader, Map<String, Object> payload) {
        if (!isAuthed(authHeader)) return loginPrompt("add items to your cart");
        Long productId = longOf(payload.get("productId"));
        int qty = payload.get("quantity") instanceof Number ? ((Number) payload.get("quantity")).intValue() : 1;
        if (productId == null) return errorReply("I need a product to add to your cart.");
        try {
            Product p = productService.getProductById(productId);
            cartService.addItem(authHeader, productId, Math.max(1, qty));
            return cartTouched(authHeader, "Added " + p.getName() + " to your cart!");
        } catch (Exception e) {
            return errorReply("Couldn't add to your cart. " + safeMessage(e));
        }
    }

    private ChatResponse removeFromCart(String authHeader, Map<String, Object> payload) {
        if (!isAuthed(authHeader)) return loginPrompt("manage your cart");
        Long itemId = longOf(payload.get("itemId"));
        if (itemId == null) return errorReply("I need a cart item to remove.");
        try {
            cartService.removeItem(authHeader, itemId);
            return cartTouched(authHeader, "Removed that item from your cart.");
        } catch (Exception e) {
            return errorReply("Couldn't remove that item. " + safeMessage(e));
        }
    }

    private ChatResponse moveToCart(String authHeader, Map<String, Object> payload) {
        if (!isAuthed(authHeader)) return loginPrompt("move items to your cart");
        Long productId = longOf(payload.get("productId"));
        if (productId == null) return errorReply("I need a product to move to your cart.");
        try {
            Product p = productService.getProductById(productId);
            wishlistService.moveToCart(authHeader, productId);
            return cartTouched(authHeader, "Moved " + p.getName() + " to your cart!");
        } catch (Exception e) {
            return errorReply("Couldn't move that item to your cart. " + safeMessage(e));
        }
    }

    private ChatResponse addToWishlist(String authHeader, Map<String, Object> payload) {
        if (!isAuthed(authHeader)) return loginPrompt("save items to your wishlist");
        Long productId = longOf(payload.get("productId"));
        if (productId == null) return errorReply("I need a product to save.");
        try {
            Product p = productService.getProductById(productId);
            wishlistService.addToWishlist(authHeader, productId);
            return ChatResponse.create("wishlist", "Added " + p.getName() + " to your wishlist.")
                    .actions(List.of(nav("VIEW_WISHLIST", "View Wishlist")));
        } catch (Exception e) {
            return errorReply("Couldn't add to your wishlist. " + safeMessage(e));
        }
    }

    private ChatResponse viewProduct(Map<String, Object> payload) {
        Long productId = longOf(payload.get("productId"));
        if (productId == null) return errorReply("I need a product to show you.");
        try {
            Product p = productService.getProductById(productId);
            StringBuilder sb = new StringBuilder();
            sb.append(p.getName());
            sb.append("\nPrice: ").append(priceLabel(p));
            int stock = p.getStock() == null ? 0 : p.getStock();
            sb.append("\nAvailability: ").append(stock <= 0 ? "Out of stock" : (stock <= 5 ? "Only " + stock + " left" : "In stock"));
            if (p.getDescription() != null && !p.getDescription().isBlank()) {
                String desc = p.getDescription().length() > 220 ? p.getDescription().substring(0, 220) + "..." : p.getDescription();
                sb.append("\n\n").append(desc);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("product", compactProduct(p));
            return ChatResponse.create("product_details", sb.toString())
                    .data(data)
                    .actions(List.of(
                            action("ADD_TO_CART", "Add to Cart", "productId", p.getId()),
                            action("VIEW_PRODUCT", "View Product", "productId", p.getId())
                    ));
        } catch (Exception e) {
            return errorReply("I couldn't find that product. " + safeMessage(e));
        }
    }

    private ChatResponse cartTouched(String authHeader, String message) {
        try {
            Map<String, Object> cart = cartService.getCartWithDetails(authHeader);
            Object totalItemsObj = cart.get("totalItems");
            Object finalObj = cart.get("finalAmount");
            int totalItems = totalItemsObj instanceof Number ? ((Number) totalItemsObj).intValue() : 0;
            double finalAmount = finalObj instanceof Number ? ((Number) finalObj).doubleValue() : 0.0;
            message += "\nYour cart now has " + totalItems + " item(s) totaling " + inr(finalAmount) + ".";
        } catch (Exception ignored) {
        }
        return ChatResponse.create("cart", message)
                .actions(List.of(nav("VIEW_CART", "View Cart"), nav("CHECKOUT", "Checkout")));
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private boolean isAuthed(String authHeader) {
        return authHeader != null && !authHeader.isBlank();
    }

    private List<String> tokens(String msg) {
        String clean = msg.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ");
        return Arrays.stream(clean.split("\\s+"))
                .filter(w -> w.length() >= 3)
                .filter(w -> !STOPWORDS.contains(w))
                .distinct()
                .collect(Collectors.toList());
    }

    private int scoreProduct(Product p, List<String> tokens) {
        if (tokens.isEmpty()) return 0;
        try {
            String haystack = ((p.getName() == null ? "" : p.getName())
                    + " " + (p.getBrand() == null ? "" : p.getBrand())
                    + " " + (p.getDescription() == null ? "" : p.getDescription())
                    + " " + (p.getSubCategory() == null ? "" : p.getSubCategory())
                    + " " + (p.getColor() == null ? "" : p.getColor())
                    + " " + (p.getTags() == null ? "" : String.join(" ", p.getTags())))
                    .toLowerCase(Locale.ROOT);
            String name = (p.getName() == null ? "" : p.getName()).toLowerCase(Locale.ROOT);
            String brand = (p.getBrand() == null ? "" : p.getBrand()).toLowerCase(Locale.ROOT);
            String sub = (p.getSubCategory() == null ? "" : p.getSubCategory()).toLowerCase(Locale.ROOT);
            int score = 0;
            for (String t : tokens) {
                if (!haystack.contains(t)) continue;
                if (name.contains(t)) score += 5;
                else if (brand.contains(t)) score += 3;
                else if (sub.contains(t)) score += 2;
                else score += 1;
            }
            return score;
        } catch (Exception e) {
            return 0;
        }
    }

    private Map<String, Object> compactProduct(Product p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("brand", p.getBrand());
        m.put("price", p.getPrice());
        m.put("offerPrice", p.getOfferPrice());
        m.put("stock", p.getStock());
        m.put("inStock", p.getStock() != null && p.getStock() > 0);
        m.put("category", categoryName(p));
        m.put("image", productImage(p));
        return m;
    }

    private String categoryName(Product p) {
        try {
            if (p.getCategory() != null) return p.getCategory().getName();
        } catch (Exception ignored) {
        }
        return p.getSubCategory();
    }

    private String productImage(Product p) {
        try {
            if (p.getImages() != null && !p.getImages().isEmpty()) return p.getImages().get(0).getUrl();
        } catch (Exception ignored) {
        }
        return null;
    }

    private String priceLabel(Product p) {
        double price = p.getPrice() == null ? 0 : p.getPrice();
        if (p.getOfferPrice() != null && p.getOfferPrice() < price) {
            return inr(p.getOfferPrice()) + " (was " + inr(price) + ")";
        }
        return inr(price);
    }

    private String inr(double amount) {
        return "\u20B9" + String.format(Locale.ROOT, "%,.2f", amount);
    }

    private double effectivePrice(Product p) {
        double price = p.getPrice() == null ? 0 : p.getPrice();
        if (p.getOfferPrice() != null && p.getOfferPrice() < price) return p.getOfferPrice();
        return price;
    }

    private double[] extractPriceRange(String msg) {
        double min = 0;
        double max = 0;
        String clean = msg.replaceAll("[\\u20B9,]", " ");
        List<Double> nums = new ArrayList<>();
        java.util.regex.Matcher m = Pattern.compile("\\d+(?:\\.\\d+)?").matcher(clean);
        while (m.find()) {
            nums.add(Double.parseDouble(m.group()));
        }
        if (msg.contains("between") && nums.size() >= 2) {
            min = nums.get(0);
            max = nums.get(1);
            return new double[]{min, max};
        }
        if (msg.contains("under") || msg.contains("below") || msg.contains("less than")
                || msg.contains("beneath") || msg.contains("up to") || msg.contains("upto")
                || msg.contains("within") || msg.contains("at most") || msg.contains("not more than")
                || msg.contains("max") || msg.contains("maximum")) {
            for (Double d : nums) max = Math.max(max, d);
        }
        if (msg.contains("over") || msg.contains("above") || msg.contains("more than")
                || msg.contains("greater than") || msg.contains("at least") || msg.contains("minimum")
                || msg.contains("min")) {
            for (Double d : nums) min = Math.max(min, d);
        }
        return new double[]{min, max};
    }

    private String priceHeader(String msg, boolean asc, boolean desc, double min, double max) {
        if (desc) return "Here are the most expensive products:\n";
        if (asc) {
            if (msg.contains("budget") || msg.contains("affordable") || msg.contains("cheap")) {
                return "Here are our most affordable picks:\n";
            }
            return "Here are the lowest-priced products:\n";
        }
        StringBuilder b = new StringBuilder("Here are products ");
        if (min > 0 && max > 0) b.append("between ").append(inr(min)).append(" and ").append(inr(max));
        else if (min > 0) b.append("over ").append(inr(min));
        else b.append("under ").append(inr(max));
        b.append(":\n");
        return b.toString();
    }

    private String safeMessage(Exception e) {
        if (e == null) return "Something went wrong.";
        String m = e.getMessage();
        if (m == null || m.isBlank()) return "Something went wrong.";
        return m.length() > 140 ? m.substring(0, 140) + "..." : m;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private Long longOf(Object o) {
        if (o == null) return null;
        try {
            return Long.valueOf(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> nav(String type, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("label", label);
        return m;
    }

    private Map<String, Object> action(String type, String label, String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("label", label);
        m.put(key, value);
        return m;
    }
}
