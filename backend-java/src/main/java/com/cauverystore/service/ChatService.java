package com.cauverystore.service;

import com.cauverystore.dto.ChatResponse;
import com.cauverystore.entities.Coupon;
import com.cauverystore.entities.Faq;
import com.cauverystore.entities.Product;
import com.cauverystore.entities.WishlistItem;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    // findAndRegisterModules: order dates may be java.time values.
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private final ProductService productService;
    private final FaqService faqService;
    private final CartService cartService;
    private final WishlistService wishlistService;
    private final OrderService orderService;
    private final CouponService couponService;
    private final AuthorizationService authorizationService;
    private final AiShoppingAssistant aiAssistant;
    private final RateLimiterService rateLimiter;

    /** AI replies allowed per visitor address per hour; after that the keyword chat answers. */
    private static final int AI_REPLIES_PER_HOUR = 30;

    public ChatService(ProductService productService, FaqService faqService,
                       CartService cartService, WishlistService wishlistService,
                       OrderService orderService, CouponService couponService,
                       AuthorizationService authorizationService,
                       AiShoppingAssistant aiAssistant, RateLimiterService rateLimiter) {
        this.productService = productService;
        this.faqService = faqService;
        this.cartService = cartService;
        this.wishlistService = wishlistService;
        this.orderService = orderService;
        this.couponService = couponService;
        this.authorizationService = authorizationService;
        this.aiAssistant = aiAssistant;
        this.rateLimiter = rateLimiter;
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

    private static final Set<String> GREETINGS = Set.of("hi", "hello", "hey", "namaste", "hola", "hai", "good", "morning",
            "evening", "afternoon", "vanakkam", "namaskar", "namaskaram", "hii", "hiii", "helo", "hello there", "there");

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
        return respond(authHeader, message, null, null, List.of());
    }

    public ChatResponse respond(String authHeader, String message, List<Map<String, String>> history, String clientIp) {
        return respond(authHeader, message, history, clientIp, List.of());
    }

    /**
     * Every message goes to the AI assistant when it is switched on; it can search products and
     * read and change the shopper's cart, wishlist and orders through tools. If the AI is off,
     * over the hourly limit, or fails, the store's own assistant below answers: it understands
     * commands ("add the second one to my cart"), order numbers, small typos and follow-ups.
     *
     * @param shownProductIds products in the chat's last list, in order, so "the second one" works
     */
    public ChatResponse respond(String authHeader, String message, List<Map<String, String>> history,
                                String clientIp, List<Long> shownProductIds) {
        if (message == null || message.trim().isEmpty()) {
            return fallback();
        }
        if (message.length() > 1000) message = message.substring(0, 1000);
        if (aiAssistant.isEnabled()
                && rateLimiter.allow("chat-ai:" + (clientIp == null ? "unknown" : clientIp), AI_REPLIES_PER_HOUR, 3_600_000L)) {
            AssistantShop shop = new AssistantShop(authHeader);
            AiShoppingAssistant.Reply ai = aiAssistant.answer(message.trim(), history, shop, shopperNote(authHeader));
            if (ai != null) return aiReply(ai, shop);
        }

        String msg = message.trim().toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        ChatResponse smart = smartAnswer(authHeader, msg, shownProductIds == null ? List.of() : shownProductIds);
        if (smart != null) return smart;
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
        // Only short messages: "nice cotton saree" is a search, not thanks.
        if (msg.split("\\s+").length <= 4
                && matchesAny(msg, "thank", "thanks", "great", "awesome", "nice", "perfect", "cool", "helpful", "good job")) return "thanks";
        if (matchesAny(msg, "contact", "talk to", "human", "agent", "support person", "reach", "phone number", "support team")) return "contact";

        if (matchesAny(msg, "wishlist", "saved items", "favorite", "favourite", "watchlist", "shortlist", "saved for later")) return "wishlist";
        if (matchesAny(msg, "cart", "my bag", "basket", "checkout")) return "cart";
        // "I want to order a saree" is a search; these mean the shopper's own orders.
        if (matchesAny(msg, "my order", "track", "delivery status", "shipped", "delivered", "purchases", "bought",
                "order status", "order history", "past order", "previous order", "recent order", "where is my")
                || msg.matches("^(show\\s+)?(my\\s+)?orders?[?.!]*$")) return "orders";
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
            List<String> what = tokens.stream().filter(w -> !PRICE_NOISE.contains(w) && !w.matches("\\d+")).toList();
            if (!what.isEmpty()) {
                // "sarees under 1000": only sarees, then by price.
                pricePool = pricePool.stream().filter(p -> scoreProduct(p, what) > 0).collect(Collectors.toList());
            }
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

    private ChatResponse aiReply(AiShoppingAssistant.Reply ai, AssistantShop shop) {
        List<Map<String, Object>> actions = new ArrayList<>();
        if (shop.loginNeeded) {
            actions.add(nav("LOGIN", "Login"));
            actions.add(nav("REGISTER", "Create Account"));
        }
        // Buttons from the account tools the assistant used (View Cart, Track Order, ...).
        for (Map<String, Object> a : shop.actions) {
            if (actions.size() >= 6) break;
            if (actions.stream().noneMatch(a::equals)) actions.add(a);
        }
        List<Map<String, Object>> productList = new ArrayList<>();
        List<Product> products = ai.products().size() > 6 ? ai.products().subList(0, 6) : ai.products();
        for (Product p : products) {
            // Cards and buttons only for products the reply names; Claude may have looked at more.
            if (p.getName() == null || !ai.text().contains(p.getName())) continue;
            productList.add(compactProduct(p));
            if (actions.size() < 8) {
                actions.add(action("VIEW_PRODUCT", "View: " + p.getName(), "productId", p.getId()));
                actions.add(action("ADD_TO_CART", "Add to Cart: " + p.getName(), "productId", p.getId()));
            }
        }
        if (actions.isEmpty() && ai.text().contains("Contact Us")) actions.add(nav("CONTACT", "Contact Us"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("products", productList);
        return ChatResponse.create("ai", ai.text()).data(data).actions(actions);
    }

    private String shopperNote(String authHeader) {
        if (!isAuthed(authHeader)) return "not signed in.";
        try {
            String full = authorizationService.getCurrentUser().getFullName();
            if (full != null && !full.isBlank()) return "signed in, first name " + full.strip().split("\\s+")[0] + ".";
        } catch (Exception ignored) {
            // name unavailable; still signed in
        }
        return "signed in.";
    }

    /**
     * The assistant's tools for one chat message, acting as the signed-in shopper. Collects the
     * buttons to show under the reply (View Cart, Track Order, Login...).
     */
    final class AssistantShop implements AiShoppingAssistant.Shop {
        private final String authHeader;
        final List<Map<String, Object>> actions = new ArrayList<>();
        boolean loginNeeded;

        AssistantShop(String authHeader) {
            this.authHeader = authHeader;
        }

        @Override
        public List<Product> search(String query, Double minPrice, Double maxPrice, String sort) {
            return searchForAssistant(query, minPrice, maxPrice, sort);
        }

        @Override
        public String run(String tool, Map<String, Object> in) {
            if ("list_offers".equals(tool)) return offersForAssistant(this);
            if (!isAuthed(authHeader)) {
                loginNeeded = true;
                return "The shopper is not signed in, so this can't be done. Ask them to log in or create an account.";
            }
            try {
                return switch (tool) {
                    case "view_cart" -> cartForAssistant(this);
                    case "add_to_cart" -> {
                        Product p = activeProduct(in.get("product_id"));
                        if (p == null) yield "No product with that id is on sale.";
                        int qty = in.get("quantity") instanceof Number n ? Math.max(1, Math.min(10, n.intValue())) : 1;
                        cartService.addItem(authHeader, p.getId(), qty);
                        cartButtons(this);
                        yield "Added " + qty + " x " + p.getName() + " to the cart.\n" + cartForAssistant(this);
                    }
                    case "remove_from_cart" -> {
                        Long itemId = longOf(in.get("cart_item_id"));
                        if (itemId == null) yield "Give the cart_item_id from view_cart.";
                        cartService.removeItem(authHeader, itemId);
                        yield "Removed.\n" + cartForAssistant(this);
                    }
                    case "view_wishlist" -> wishlistForAssistant(this);
                    case "add_to_wishlist" -> {
                        Product p = activeProduct(in.get("product_id"));
                        if (p == null) yield "No product with that id is on sale.";
                        wishlistService.addToWishlist(authHeader, p.getId());
                        actions.add(nav("VIEW_WISHLIST", "View Wishlist"));
                        yield "Saved " + p.getName() + " to the wishlist.";
                    }
                    case "move_wishlist_to_cart" -> {
                        Product p = activeProduct(in.get("product_id"));
                        if (p == null) yield "No product with that id is on sale.";
                        wishlistService.moveToCart(authHeader, p.getId());
                        cartButtons(this);
                        yield "Moved " + p.getName() + " from the wishlist to the cart.\n" + cartForAssistant(this);
                    }
                    case "view_orders" -> ordersForAssistant(this);
                    case "track_order" -> trackForAssistant(this, longOf(in.get("order_id")));
                    default -> "Unknown tool.";
                };
            } catch (Exception e) {
                // Stock limits, wrong ids and similar: tell Claude what went wrong so it can explain.
                return "That didn't work: " + safeMessage(e);
            }
        }
    }

    private Product activeProduct(Object id) {
        Long productId = longOf(id);
        if (productId == null) return null;
        try {
            Product p = productService.getProductById(productId);
            return p != null && p.isActive() ? p : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void cartButtons(AssistantShop shop) {
        shop.actions.add(nav("VIEW_CART", "View Cart"));
        shop.actions.add(nav("CHECKOUT", "Checkout"));
    }

    @SuppressWarnings("unchecked")
    private String cartForAssistant(AssistantShop shop) throws Exception {
        Map<String, Object> cart = cartService.getCartWithDetails(shop.authHeader);
        List<Map<String, Object>> items = (List<Map<String, Object>>) cart.getOrDefault("items", List.of());
        if (items.isEmpty()) return "The cart is empty.";
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Map<String, Object> product = (Map<String, Object>) item.get("product");
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("cart_item_id", item.get("id"));
            if (product != null) {
                line.put("product_id", product.get("id"));
                line.put("name", product.get("name"));
                line.put("stock", product.get("stockStatus"));
            }
            line.put("quantity", item.get("quantity"));
            line.put("subtotal_inr", item.get("subtotal"));
            lines.add(line);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", lines);
        out.put("items_total_inr", cart.get("totalPrice"));
        out.put("delivery_inr", cart.get("deliveryCharge"));
        out.put("amount_to_pay_inr", cart.get("finalAmount"));
        cartButtons(shop);
        return JSON.writeValueAsString(out);
    }

    private String wishlistForAssistant(AssistantShop shop) throws Exception {
        List<WishlistItem> items = wishlistService.getWishlist(shop.authHeader);
        if (items.isEmpty()) return "The wishlist is empty.";
        List<Map<String, Object>> rows = new ArrayList<>();
        for (WishlistItem item : items) {
            Product p = item.getProduct();
            if (p == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("product_id", p.getId());
            row.put("name", p.getName());
            row.put("price_inr", effectivePrice(p));
            int stock = p.getStock() == null ? 0 : p.getStock();
            row.put("stock", stock <= 0 ? "out of stock" : "in stock");
            rows.add(row);
            if (rows.size() >= 20) break;
        }
        shop.actions.add(nav("VIEW_WISHLIST", "View Wishlist"));
        return JSON.writeValueAsString(rows);
    }

    @SuppressWarnings("unchecked")
    private String ordersForAssistant(AssistantShop shop) throws Exception {
        List<Map<String, Object>> orders = orderService.getOrdersByHeader(shop.authHeader, null);
        if (orders.isEmpty()) return "The shopper has no orders yet.";
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> o : orders.subList(0, Math.min(orders.size(), 8))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("order_id", o.get("id"));
            row.put("placed_at", o.get("createdAt"));
            row.put("status", o.get("status"));
            row.put("amount_inr", o.get("totalAmount"));
            row.put("payment", o.get("paymentMethod"));
            List<String> names = new ArrayList<>();
            for (Map<String, Object> item : (List<Map<String, Object>>) o.getOrDefault("items", List.of())) {
                names.add(item.get("quantity") + " x " + item.get("productName"));
            }
            row.put("items", names);
            rows.add(row);
        }
        shop.actions.add(nav("VIEW_ORDERS", "My Orders"));
        return JSON.writeValueAsString(rows);
    }

    private String trackForAssistant(AssistantShop shop, Long orderId) throws Exception {
        if (orderId == null) return "Which order? Ask for the order number, or call view_orders.";
        Map<String, Object> detail;
        try {
            detail = orderService.getOrderDetail(orderId, shop.authHeader);
        } catch (Exception e) {
            return "There is no order #" + orderId + " on this shopper's account.";
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("order_id", orderId);
        out.put("status", detail.get("status"));
        out.put("placed_at", detail.get("createdAt"));
        out.put("amount_inr", detail.get("totalAmount"));
        out.put("payment_method", detail.get("paymentMethod"));
        out.put("payment_status", detail.get("paymentStatus"));
        if (detail.get("shippingAddress") instanceof Map<?, ?> addr) out.put("delivering_to_city", addr.get("city"));
        try {
            Map<String, Object> tracking = orderService.getOrderTracking(orderId, shop.authHeader);
            out.put("courier", tracking.get("courier"));
            out.put("tracking_number", tracking.get("trackingNumber"));
            out.put("tracking_link", tracking.get("trackingUrl"));
            out.put("expected_delivery", tracking.get("estimatedDelivery"));
            if (tracking.get("events") instanceof List<?> events) {
                out.put("recent_updates", events.size() > 5 ? events.subList(0, 5) : events);
            }
        } catch (Exception ignored) {
            // No courier details yet; the status above still answers the question.
        }
        shop.actions.add(action("TRACK_ORDER", "Track Order #" + orderId, "orderId", orderId));
        return JSON.writeValueAsString(out);
    }

    private String offersForAssistant(AssistantShop shop) {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Map<String, Object>> coupons = new ArrayList<>();
            for (Coupon c : couponService.getAll()) {
                if (!c.isActive()) continue;
                if (c.getValidUntil() != null && !c.getValidUntil().isAfter(now)) continue;
                if (c.getValidFrom() != null && c.getValidFrom().isAfter(now)) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code", c.getCode());
                row.put("discount", "PERCENTAGE".equalsIgnoreCase(c.getType())
                        ? Math.round(c.getValue()) + "% off" : "flat " + inr(c.getValue()) + " off");
                if (c.getMinOrderAmount() != null && c.getMinOrderAmount() > 0) row.put("min_order_inr", c.getMinOrderAmount());
                if (c.getValidUntil() != null) row.put("valid_until", c.getValidUntil().toLocalDate().toString());
                coupons.add(row);
                if (coupons.size() >= 8) break;
            }
            List<Map<String, Object>> discounted = new ArrayList<>();
            for (Product p : productService.getActiveProducts()) {
                if (p.getOfferPrice() == null || p.getPrice() == null || p.getOfferPrice() >= p.getPrice()) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("product_id", p.getId());
                row.put("name", p.getName());
                row.put("price_inr", p.getOfferPrice());
                row.put("original_price_inr", p.getPrice());
                discounted.add(row);
                if (discounted.size() >= 6) break;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("coupon_codes", coupons);
            out.put("discounted_products", discounted);
            shop.actions.add(nav("VIEW_OFFERS", "All Offers"));
            return JSON.writeValueAsString(out);
        } catch (Exception e) {
            return "Offers couldn't be loaded just now.";
        }
    }

    /** The AI assistant's product search tool, using the same matching as the keyword search. */
    List<Product> searchForAssistant(String query, Double minPrice, Double maxPrice, String sort) {
        List<Product> pool = productService.getActiveProducts().stream()
                .filter(p -> p.getPrice() != null)
                .filter(p -> minPrice == null || effectivePrice(p) >= minPrice)
                .filter(p -> maxPrice == null || effectivePrice(p) <= maxPrice)
                .collect(Collectors.toList());
        if ("best_sellers".equals(sort)) {
            List<Product> best = pool.stream().filter(p -> Boolean.TRUE.equals(p.getBestSeller())).toList();
            if (!best.isEmpty()) pool = new ArrayList<>(best);
        } else if ("new_arrivals".equals(sort)) {
            List<Product> fresh = pool.stream().filter(p -> Boolean.TRUE.equals(p.getNewArrival())).toList();
            if (!fresh.isEmpty()) pool = new ArrayList<>(fresh);
        }
        List<String> words = tokens(query == null ? "" : query);
        if (!words.isEmpty()) {
            pool = pool.stream()
                    .filter(p -> scoreProduct(p, words) > 0)
                    .sorted((a, b) -> Integer.compare(scoreProduct(b, words), scoreProduct(a, words)))
                    .collect(Collectors.toList());
        }
        if ("price_low_to_high".equals(sort)) pool.sort(Comparator.comparingDouble(this::effectivePrice));
        if ("price_high_to_low".equals(sort)) pool.sort(Comparator.comparingDouble(this::effectivePrice).reversed());
        return pool.size() > 6 ? new ArrayList<>(pool.subList(0, 6)) : pool;
    }

    // ---------------------------------------------------------------
    // Understanding commands and follow-ups (no outside AI needed)
    // ---------------------------------------------------------------

    /** Words in a command that don't name the product ("add 2 of the ... to my cart"). */
    private static final Set<String> COMMAND_NOISE = Set.of(
            "add", "put", "buy", "save", "cart", "basket", "wishlist", "wish", "list", "remove", "delete",
            "move", "take", "out", "drop", "one", "ones", "item", "items", "product", "products", "pls",
            "quantity", "qty", "pcs", "piece", "pieces", "nos", "units", "two", "three", "four", "five",
            "six", "seven", "eight", "nine", "ten", "first", "second", "third", "fourth", "fifth", "sixth",
            "last", "1st", "2nd", "3rd", "4th", "5th", "6th", "again", "more", "too", "now", "just",
            "same", "into", "favourite", "favorite", "later", "ill", "cheapest", "cheaper", "costliest");

    /** Words in a price search that don't name the product ("sarees under 500 rupees"). */
    private static final Set<String> PRICE_NOISE = Set.of(
            "under", "below", "above", "over", "less", "more", "than", "between", "cheap", "cheaper",
            "cheapest", "expensive", "costly", "costliest", "pricey", "budget", "price", "priced", "prices",
            "rupees", "rupee", "inr", "affordable", "lowest", "highest", "low", "high", "most", "least",
            "premium", "within", "upto", "max", "maximum", "min", "minimum", "range", "cost", "value",
            "money", "end", "top", "inexpensive", "beneath", "greater", "atleast", "products", "items");

    private static final Pattern BYE = Pattern.compile("^(bye|goodbye|good bye|see you|see ya|tata|bye bye|good night)\\b.*");
    private static final Pattern OK_ONLY = Pattern.compile("^(ok|okay|k|kk|fine|alright|all right|hmm+|got it|sure|yes|no|nope)[.! ]*$");
    private static final Pattern THANKS_START = Pattern.compile("^(thanks?|thank you|thankyou|thx|ty|nandri|dhanyavad|dhanyavaad|shukriya)\\b.*");
    private static final Pattern REMOVE_START = Pattern.compile("^(please\\s+|pls\\s+)?(remove|delete|take out|drop)\\b.*");
    private static final Pattern MOVE_START = Pattern.compile("^(please\\s+|pls\\s+)?move\\b.*");
    private static final Pattern ADD_START = Pattern.compile(
            "^(please\\s+|pls\\s+|can you\\s+|could you\\s+|kindly\\s+)?(add|put|buy|save|i'?ll take|i will take)\\b.*");

    /**
     * Commands and follow-ups the keyword intents can't handle: "add the second one",
     * "remove the towel", "where is order 12", "is it in stock". Null when the message is
     * something else, so the usual intents answer.
     */
    private ChatResponse smartAnswer(String authHeader, String msg, List<Long> shown) {
        int words = msg.split("\\s+").length;
        if (BYE.matcher(msg).matches()) {
            return ChatResponse.create("bye", "Thanks for visiting Cauvery Store! Come back any time.");
        }
        if (THANKS_START.matcher(msg).matches() && words <= 6) return thanks();
        if (OK_ONLY.matcher(msg).matches()) {
            return ChatResponse.create("ok", "Is there anything else I can help you with?")
                    .quickReplies(List.of("Show me best sellers", "Any offers right now?", "Show my cart", "Track my order"));
        }
        if (matchesAny(msg, "who are you", "are you a bot", "are you human", "are you a robot", "are you real", "your name")) {
            return ChatResponse.create("about",
                            "I'm Cauvery Store's shopping assistant, an automated helper. I can find products, "
                                    + "add them to your cart or wishlist, show your orders and track deliveries, and share "
                                    + "offers. For anything I can't sort out, our support team is on the Contact Us page.")
                    .actions(List.of(nav("CONTACT", "Contact Us")));
        }

        Long orderNo = ChatText.orderNumber(msg);
        if (orderNo != null && (matchesAny(msg, "order", "track", "where", "status", "deliver", "ship") || msg.contains("#"))) {
            return trackOrderReply(authHeader, orderNo);
        }
        if (matchesAny(msg, "cancel") && !matchesAny(msg, "policy")) return cancelHelp();
        if (matchesAny(msg, "damaged", "broken", "defective", "wrong item", "wrong product", "missing item", "not working", "torn")) {
            return damagedHelp();
        }

        boolean wishlistWord = matchesAny(msg, "wishlist", "wish list", "save for later", "favourite", "favorite");
        if (REMOVE_START.matcher(msg).matches()) return removeCommand(authHeader, msg, wishlistWord);
        if (MOVE_START.matcher(msg).matches()) return moveCommand(authHeader, msg);
        if (ADD_START.matcher(msg).matches() || matchesAny(msg, "add to cart", "add to my cart", "add it", "add this", "add that")) {
            return addCommand(authHeader, msg, shown, wishlistWord || msg.startsWith("save"));
        }

        if (!shown.isEmpty()) {
            Product ref = referencedProduct(msg, shown);
            if (ref != null && matchesAny(msg, "more", "detail", "about", "stock", "price", "cost", "how much",
                    "available", "describe", "spec", "tell", "show", "view", "size", "colour", "color")) {
                return viewProduct(Map.of("productId", ref.getId()));
            }
        }
        return null;
    }

    /** "the second one", "the cheapest one", or "it" when only one product was shown. */
    private Product referencedProduct(String msg, List<Long> shown) {
        if (shown.isEmpty()) return null;
        List<Product> listed = new ArrayList<>();
        for (Long id : shown) {
            Product p = activeProduct(id);
            if (p != null) listed.add(p);
        }
        if (listed.isEmpty()) return null;
        int idx = ChatText.ordinal(msg, listed.size());
        if (idx >= 0) return idx < listed.size() ? listed.get(idx) : null;
        if (matchesAny(msg, "cheapest", "cheaper", "lowest price", "least expensive")) {
            return listed.stream().min(Comparator.comparingDouble(this::effectivePrice)).orElse(null);
        }
        if (matchesAny(msg, "costliest", "most expensive", "priciest", "dearest")) {
            return listed.stream().max(Comparator.comparingDouble(this::effectivePrice)).orElse(null);
        }
        if (listed.size() == 1 && (ChatText.pointsBack(msg) || productWords(msg).isEmpty())) return listed.get(0);
        return null;
    }

    private List<String> productWords(String msg) {
        return tokens(msg).stream()
                .filter(w -> !COMMAND_NOISE.contains(w))
                .filter(w -> !w.matches("\\d+"))
                .toList();
    }

    private ChatResponse addCommand(String authHeader, String msg, List<Long> shown, boolean toWishlist) {
        if (!isAuthed(authHeader)) return loginPrompt(toWishlist ? "save items to your wishlist" : "add items to your cart");
        Product p = referencedProduct(msg, shown);
        List<Product> options = new ArrayList<>();
        if (p == null) {
            List<String> words = productWords(msg);
            if (words.isEmpty()) {
                for (Long id : shown) {
                    Product s = activeProduct(id);
                    if (s != null && options.size() < 4) options.add(s);
                }
            } else {
                List<Product> ranked = productService.getActiveProducts().stream()
                        .filter(x -> scoreProduct(x, words) > 0)
                        .sorted((a, b) -> Integer.compare(scoreProduct(b, words), scoreProduct(a, words)))
                        .limit(4)
                        .toList();
                if (ranked.size() == 1
                        || (ranked.size() > 1 && scoreProduct(ranked.get(0), words) > scoreProduct(ranked.get(1), words))) {
                    p = ranked.get(0);
                } else {
                    options.addAll(ranked);
                }
            }
        }
        if (p == null && options.isEmpty()) {
            return ChatResponse.create("no_results",
                            "I couldn't tell which product you mean. Try \"add cotton towel to cart\", or search first and then say \"add the first one\".")
                    .quickReplies(List.of("Show me best sellers", "Any offers right now?"));
        }
        if (p == null) {
            StringBuilder sb = new StringBuilder("Which one would you like?\n");
            List<Map<String, Object>> actions = new ArrayList<>();
            List<Map<String, Object>> cards = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) {
                Product o = options.get(i);
                sb.append("\n").append(i + 1).append(". ").append(o.getName()).append(" - ").append(priceLabel(o));
                cards.add(compactProduct(o));
                actions.add(toWishlist
                        ? action("ADD_TO_WISHLIST", "Save: " + o.getName(), "productId", o.getId())
                        : action("ADD_TO_CART", "Add: " + o.getName(), "productId", o.getId()));
            }
            sb.append("\n\nTap one, or say \"the second one\".");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("products", cards);
            return ChatResponse.create("choose_product", sb.toString()).data(data).actions(actions);
        }

        if (toWishlist) {
            ChatResponse r = addToWishlist(authHeader, Map.of("productId", p.getId()));
            return r.data(Map.of("wishlistChanged", true));
        }
        int stock = p.getStock() == null ? 0 : p.getStock();
        if (stock <= 0) {
            return ChatResponse.create("out_of_stock",
                            p.getName() + " is out of stock right now. Save it to your wishlist and check back soon.")
                    .actions(List.of(action("ADD_TO_WISHLIST", "Save to Wishlist", "productId", p.getId())));
        }
        int qty = ChatText.quantity(msg);
        try {
            cartService.addItem(authHeader, p.getId(), qty);
            return cartTouched(authHeader, "Added " + (qty > 1 ? qty + " x " : "") + p.getName() + " to your cart.")
                    .data(Map.of("cartChanged", true));
        } catch (Exception e) {
            return errorReply("Couldn't add " + p.getName() + " to your cart. " + safeMessage(e));
        }
    }

    @SuppressWarnings("unchecked")
    private ChatResponse removeCommand(String authHeader, String msg, boolean fromWishlist) {
        if (!isAuthed(authHeader)) return loginPrompt("manage your " + (fromWishlist ? "wishlist" : "cart"));
        List<String> words = productWords(msg);
        try {
            if (fromWishlist) {
                List<Product> saved = wishlistService.getWishlist(authHeader).stream()
                        .map(WishlistItem::getProduct).filter(x -> x != null).toList();
                Product target = pickFrom(saved, msg, words);
                if (target == null) return chooseFrom(saved, "remove", "REMOVE_FROM_WISHLIST", fromWishlist);
                wishlistService.removeFromWishlist(authHeader, target.getId());
                return ChatResponse.create("wishlist", "Removed " + target.getName() + " from your wishlist.")
                        .actions(List.of(nav("VIEW_WISHLIST", "View Wishlist")))
                        .data(Map.of("wishlistChanged", true));
            }
            Map<String, Object> cart = cartService.getCartWithDetails(authHeader);
            List<Map<String, Object>> items = (List<Map<String, Object>>) cart.getOrDefault("items", List.of());
            if (items.isEmpty()) return ChatResponse.create("cart", "Your cart is already empty.");
            if (matchesAny(msg, "everything", "all items", "all of them", "whole cart", "entire cart") || msg.matches(".*\\ball\\b.*")) {
                cartService.clearCart(authHeader);
                return ChatResponse.create("cart", "Done, your cart is now empty.")
                        .quickReplies(List.of("Show me best sellers", "Show my wishlist"))
                        .data(Map.of("cartChanged", true));
            }
            List<Product> inCart = new ArrayList<>();
            Map<Long, Long> itemIdByProduct = new LinkedHashMap<>();
            for (Map<String, Object> item : items) {
                Map<String, Object> pm = (Map<String, Object>) item.get("product");
                Long pid = pm == null ? null : longOf(pm.get("id"));
                if (pid == null) continue;
                Product shell = new Product();
                shell.setId(pid);
                shell.setName(str(pm.get("name")));
                shell.setBrand(str(pm.get("brand")));
                inCart.add(shell);
                itemIdByProduct.put(pid, longOf(item.get("id")));
            }
            Product target = pickFrom(inCart, msg, words);
            if (target == null) {
                StringBuilder sb = new StringBuilder("Which item should I remove?\n");
                List<Map<String, Object>> actions = new ArrayList<>();
                for (int i = 0; i < inCart.size() && i < 6; i++) {
                    Product x = inCart.get(i);
                    sb.append("\n").append(i + 1).append(". ").append(x.getName());
                    actions.add(action("REMOVE_FROM_CART", "Remove: " + x.getName(), "itemId", itemIdByProduct.get(x.getId())));
                }
                return ChatResponse.create("choose_item", sb.toString()).actions(actions);
            }
            cartService.removeItem(authHeader, itemIdByProduct.get(target.getId()));
            return cartTouched(authHeader, "Removed " + target.getName() + " from your cart.")
                    .data(Map.of("cartChanged", true));
        } catch (Exception e) {
            return errorReply("Couldn't remove that. " + safeMessage(e));
        }
    }

    private ChatResponse moveCommand(String authHeader, String msg) {
        if (!isAuthed(authHeader)) return loginPrompt("move items to your cart");
        try {
            List<Product> saved = wishlistService.getWishlist(authHeader).stream()
                    .map(WishlistItem::getProduct).filter(x -> x != null).toList();
            if (saved.isEmpty()) return ChatResponse.create("wishlist", "Your wishlist is empty, so there's nothing to move.");
            if (matchesAny(msg, "everything", "all of them", "all items") || msg.matches(".*\\ball\\b.*")) {
                int moved = 0;
                List<String> failed = new ArrayList<>();
                for (Product x : saved) {
                    try {
                        wishlistService.moveToCart(authHeader, x.getId());
                        moved++;
                    } catch (Exception e) {
                        failed.add(x.getName());
                    }
                }
                String text = "Moved " + moved + " item(s) from your wishlist to your cart."
                        + (failed.isEmpty() ? "" : " Couldn't move: " + String.join(", ", failed) + " (likely out of stock).");
                return cartTouched(authHeader, text).data(Map.of("cartChanged", true, "wishlistChanged", true));
            }
            Product target = pickFrom(saved, msg, productWords(msg));
            if (target == null) return chooseFrom(saved, "move to your cart", "MOVE_TO_CART", true);
            return moveToCart(authHeader, Map.of("productId", target.getId()))
                    .data(Map.of("cartChanged", true, "wishlistChanged", true));
        } catch (Exception e) {
            return errorReply("Couldn't move that. " + safeMessage(e));
        }
    }

    /** One product from a short list by position, by name (typos allowed), or the only one. */
    private Product pickFrom(List<Product> list, String msg, List<String> words) {
        if (list.isEmpty()) return null;
        int idx = ChatText.ordinal(msg, list.size());
        if (idx >= 0) return idx < list.size() ? list.get(idx) : null;
        if (!words.isEmpty()) {
            List<Product> ranked = list.stream()
                    .filter(x -> scoreProduct(x, words) > 0)
                    .sorted((a, b) -> Integer.compare(scoreProduct(b, words), scoreProduct(a, words)))
                    .toList();
            if (ranked.size() == 1) return ranked.get(0);
            if (ranked.size() > 1 && scoreProduct(ranked.get(0), words) > scoreProduct(ranked.get(1), words)) return ranked.get(0);
            return null;
        }
        return list.size() == 1 ? list.get(0) : null;
    }

    private ChatResponse chooseFrom(List<Product> list, String verb, String actionType, boolean wishlist) {
        if (list.isEmpty()) {
            return ChatResponse.create(wishlist ? "wishlist" : "cart", "Your " + (wishlist ? "wishlist" : "cart") + " is empty.");
        }
        StringBuilder sb = new StringBuilder("Which item should I " + verb + "?\n");
        List<Map<String, Object>> actions = new ArrayList<>();
        for (int i = 0; i < list.size() && i < 6; i++) {
            Product x = list.get(i);
            sb.append("\n").append(i + 1).append(". ").append(x.getName());
            if ("MOVE_TO_CART".equals(actionType)) {
                actions.add(action(actionType, "Move: " + x.getName(), "productId", x.getId()));
            }
        }
        if (actions.isEmpty()) actions.add(nav(wishlist ? "VIEW_WISHLIST" : "VIEW_CART", wishlist ? "View Wishlist" : "View Cart"));
        return ChatResponse.create("choose_item", sb.toString()).actions(actions);
    }

    private ChatResponse trackOrderReply(String authHeader, Long orderId) {
        if (!isAuthed(authHeader)) return loginPrompt("track your orders");
        Map<String, Object> detail;
        try {
            detail = orderService.getOrderDetail(orderId, authHeader);
        } catch (Exception e) {
            return ChatResponse.create("orders", "I couldn't find order #" + orderId + " on your account. Here are your orders:")
                    .actions(List.of(nav("VIEW_ORDERS", "My Orders")));
        }
        String status = friendlyStatus(str(detail.get("status")));
        StringBuilder sb = new StringBuilder("Order #").append(orderId).append(" is ").append(status).append(".");
        Object amount = detail.get("totalAmount");
        if (amount instanceof Number n) sb.append("\nAmount: ").append(inr(n.doubleValue()));
        String placed = str(detail.get("createdAt"));
        if (placed != null && placed.length() >= 10) sb.append("\nPlaced on: ").append(placed, 0, 10);
        try {
            Map<String, Object> t = orderService.getOrderTracking(orderId, authHeader);
            if (t.get("courier") != null) sb.append("\nCourier: ").append(t.get("courier"));
            if (t.get("trackingNumber") != null) sb.append("\nTracking number: ").append(t.get("trackingNumber"));
            if (t.get("estimatedDelivery") != null) sb.append("\nExpected delivery: ").append(t.get("estimatedDelivery"));
            if (t.get("events") instanceof List<?> events && !events.isEmpty()) {
                Object latest = t.get("courier") != null ? events.get(0) : events.get(events.size() - 1);
                if (latest instanceof Map<?, ?> ev) {
                    String what = str(ev.get("description"));
                    if (what == null || what.isBlank()) what = friendlyStatus(str(ev.get("status")));
                    String when = str(ev.get("timestamp"));
                    sb.append("\nLatest update: ").append(what)
                            .append(when == null || when.isBlank() ? "" : " (" + when + ")");
                }
            }
        } catch (Exception ignored) {
            // No courier details yet; the status above still answers the question.
        }
        return ChatResponse.create("orders", sb.toString())
                .actions(List.of(action("TRACK_ORDER", "Open Order #" + orderId, "orderId", orderId),
                        nav("VIEW_ORDERS", "All Orders")));
    }

    private String friendlyStatus(String status) {
        if (status == null || status.isBlank()) return "being processed";
        return status.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private ChatResponse cancelHelp() {
        return ChatResponse.create("cancel",
                        "You can cancel an order yourself until it ships: open My Orders, pick the order and tap Cancel. "
                                + "If you paid online, the refund goes back to the same payment method. "
                                + "If it has already shipped, you can return it after delivery.")
                .actions(List.of(nav("VIEW_ORDERS", "My Orders"), nav("CONTACT", "Contact Us")));
    }

    private ChatResponse damagedHelp() {
        return ChatResponse.create("damaged",
                        "Sorry about that! Damaged, defective, wrong or missing items get a free return pickup. "
                                + "Open My Orders, pick the order, tap Return, and choose \"Damaged product\" (or the reason that fits). "
                                + "Photos help us sort it out faster.")
                .actions(List.of(nav("VIEW_ORDERS", "My Orders"), nav("CONTACT", "Contact Us")));
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
                .filter(w -> w.length() >= 3 || ChatText.SHORT_PRODUCT_WORDS.contains(w))
                .filter(w -> !STOPWORDS.contains(w))
                .distinct()
                .collect(Collectors.toList());
    }

    private int scoreProduct(Product p, List<String> tokens) {
        if (tokens.isEmpty()) return 0;
        try {
            StringBuilder hay = new StringBuilder()
                    .append(p.getName() == null ? "" : p.getName()).append(" ")
                    .append(p.getBrand() == null ? "" : p.getBrand()).append(" ")
                    .append(p.getDescription() == null ? "" : p.getDescription()).append(" ")
                    .append(p.getSubCategory() == null ? "" : p.getSubCategory()).append(" ")
                    .append(p.getColor() == null ? "" : p.getColor()).append(" ");
            try {
                if (p.getTags() != null) hay.append(String.join(" ", p.getTags()));
            } catch (Exception ignored) {
            }
            String haystack = hay.toString().toLowerCase(Locale.ROOT);
            String name = (p.getName() == null ? "" : p.getName()).toLowerCase(Locale.ROOT);
            String brand = (p.getBrand() == null ? "" : p.getBrand()).toLowerCase(Locale.ROOT);
            String sub = (p.getSubCategory() == null ? "" : p.getSubCategory()).toLowerCase(Locale.ROOT);
            Set<String> hayWords = new java.util.HashSet<>(Arrays.asList(haystack.split("[^a-z0-9]+")));
            String[] nameWords = name.split("[^a-z0-9]+");
            int score = 0;
            for (String t : tokens) {
                int best = 0;
                // The word, its singular, and everyday synonyms ("mobiles" finds "phone").
                for (String v : ChatText.variants(t)) {
                    boolean found = v.length() <= 3 ? hayWords.contains(v) : haystack.contains(v);
                    if (!found) continue;
                    int s = (v.length() <= 3 ? Arrays.asList(nameWords).contains(v) : name.contains(v)) ? 5
                            : brand.contains(v) ? 3 : sub.contains(v) ? 2 : 1;
                    best = Math.max(best, s);
                }
                if (best == 0) {
                    // Small typos: "towl" still finds "towel".
                    for (String w : nameWords) {
                        if (ChatText.closeEnough(t, w)) { best = 4; break; }
                    }
                    if (best == 0) {
                        for (String w : hayWords) {
                            if (ChatText.closeEnough(t, w)) { best = 1; break; }
                        }
                    }
                }
                score += best;
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
