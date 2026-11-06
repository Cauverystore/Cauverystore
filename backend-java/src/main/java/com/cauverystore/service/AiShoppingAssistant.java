package com.cauverystore.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.cauverystore.entities.Faq;
import com.cauverystore.entities.Product;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shop chat's assistant. Claude answers every chat message in plain language: it searches
 * the catalogue, reads and changes the shopper's cart and wishlist, looks up their orders and
 * lists current offers, all through tools, so it only ever mentions real products, prices and
 * orders.
 *
 * Switched on by setting ANTHROPIC_API_KEY. Without it, or if a call fails, {@link #answer}
 * returns null and the existing keyword chat answers instead, so the chat never goes silent.
 */
@Service
public class AiShoppingAssistant {

    private static final Logger log = LoggerFactory.getLogger(AiShoppingAssistant.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_TOOL_ROUNDS = 5;

    /** Tools that act on the shopper's own account or list offers; ChatService runs them. */
    static final List<String> SHOP_TOOLS = List.of("view_cart", "add_to_cart", "remove_from_cart",
            "view_wishlist", "add_to_wishlist", "move_wishlist_to_cart", "view_orders", "track_order", "list_offers");

    /**
     * The shop as the assistant sees it, for one shopper. ChatService supplies it so product
     * ranking and account access live in one place, signed in as the person chatting.
     */
    public interface Shop {
        List<Product> search(String query, Double minPrice, Double maxPrice, String sort);

        /** Runs one of {@link #SHOP_TOOLS}; returns the result as text (often JSON) for Claude. */
        String run(String tool, Map<String, Object> input);
    }

    /** What Claude said, plus the products it looked at (shown as cards under the reply). */
    public record Reply(String text, List<Product> products) {}

    @Value("${ANTHROPIC_API_KEY:}")
    private String apiKey;

    @Value("${ai.chat.model:claude-opus-5-5}")
    private String model;

    private final FaqService faqService;
    private AnthropicClient client;

    public AiShoppingAssistant(FaqService faqService) {
        this.faqService = faqService;
    }

    @PostConstruct
    void init() {
        if (apiKey == null || apiKey.isBlank()) {
            log.info("AI shopping assistant off: ANTHROPIC_API_KEY is not set. The keyword chat answers instead.");
            return;
        }
        client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(45))
                .maxRetries(1)
                .build();
        log.info("AI shopping assistant on, model {}", model);
    }

    public boolean isEnabled() {
        return client != null;
    }

    /**
     * @param history earlier turns, oldest first, each {"role": "user"|"assistant", "text": ...}
     * @param shopper  one line about who is chatting, e.g. "signed in, first name Priya"
     * @return the reply, or null when the keyword chat should answer instead
     */
    public Reply answer(String message, List<Map<String, String>> history, Shop shop, String shopper) {
        if (!isEnabled()) return null;
        try {
            MessageCreateParams.Builder params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(16000L)
                    // A chat reply needs little thought; low effort keeps it quick and cheap.
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                    .systemOfTextBlockParams(List.of(
                            TextBlockParam.builder()
                                    .text(systemPrompt())
                                    .cacheControl(CacheControlEphemeral.builder().build())
                                    .build(),
                            // After the cached part, so it can change per shopper.
                            TextBlockParam.builder()
                                    .text("The shopper chatting now: " + shopper)
                                    .build()));
            for (Tool tool : tools()) params.addTool(tool);
            if (supportsServerFallback(model)) {
                // If a safety check declines a harmless question, the API retries it on another
                // model instead of returning nothing.
                params.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01");
                params.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
            }
            addConversation(params, history, message);

            Map<Long, Product> seen = new LinkedHashMap<>();
            for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
                Message response = client.messages().create(params.build());
                StopReason stop = response.stopReason().orElse(StopReason.END_TURN);

                if (stop.equals(StopReason.REFUSAL)) {
                    log.warn("AI assistant declined a chat message; keyword chat answered instead.");
                    return null;
                }
                if (stop.equals(StopReason.TOOL_USE) && round < MAX_TOOL_ROUNDS) {
                    // Send the whole reply back (thinking blocks included), then the tool results.
                    params.addMessage(response);
                    List<ContentBlockParam> results = new ArrayList<>();
                    for (ContentBlock block : response.content()) {
                        block.toolUse().ifPresent(use -> results.add(runTool(use, shop, seen)));
                    }
                    params.addUserMessageOfContentBlockParams(results);
                    continue;
                }

                String text = textOf(response);
                if (text.isBlank()) return null;
                return new Reply(text, new ArrayList<>(seen.values()));
            }
            return null;
        } catch (AnthropicException e) {
            log.warn("AI assistant call failed ({}); keyword chat answered instead.", e.getMessage());
            return null;
        } catch (RuntimeException e) {
            log.warn("AI assistant error; keyword chat answered instead.", e);
            return null;
        }
    }

    private static boolean supportsServerFallback(String model) {
        // Server-side fallback exists for these; Haiku has none and would reject the field.
        return model.startsWith("claude-opus-5") || model.startsWith("claude-fable-5")
                || model.equals("claude-sonnet-5-5");
    }

    static void addConversation(MessageCreateParams.Builder params, List<Map<String, String>> history, String message) {
        List<Map<String, String>> turns = new ArrayList<>();
        if (history != null) {
            // Only the last 10 turns: enough context, bounded cost.
            turns.addAll(history.subList(Math.max(0, history.size() - 10), history.size()));
        }
        turns.add(Map.of("role", "user", "text", message));
        // The conversation must start with the shopper and alternate; the widget's welcome
        // message comes first, so drop anything before the first shopper message and merge
        // repeats from the same side. It always ends on the shopper's new message.
        String pendingRole = null;
        StringBuilder pending = new StringBuilder();
        for (Map<String, String> turn : turns) {
            String role = "user".equals(turn.get("role")) ? "user" : "assistant";
            String text = turn.get("text") == null ? "" : turn.get("text").trim();
            if (text.isEmpty()) continue;
            if (text.length() > 1000) text = text.substring(0, 1000);
            if (pendingRole == null && role.equals("assistant")) continue;
            if (role.equals(pendingRole)) {
                pending.append("\n").append(text);
                continue;
            }
            flush(params, pendingRole, pending);
            pendingRole = role;
            pending = new StringBuilder(text);
        }
        flush(params, pendingRole, pending);
    }

    private static void flush(MessageCreateParams.Builder params, String role, StringBuilder text) {
        if (role == null || text.length() == 0) return;
        if (role.equals("user")) params.addUserMessage(text.toString());
        else params.addAssistantMessage(text.toString());
    }

    static List<Tool> tools() {
        Map<String, Object> productId = Map.of("type", "integer", "description", "Product id from search_products or another tool.");
        return List.of(
                tool("search_products",
                        "Search Cauvery Store's live catalogue. Use it for any question about products, "
                                + "prices, stock, or recommendations. Returns up to 6 products with id, name, brand, "
                                + "category, price in rupees (GST included), original price if discounted, stock "
                                + "status, link, and a short description. Use an empty query with a sort to browse. "
                                + "Write the query in English, even if the shopper wrote in another language.",
                        Map.of("query", Map.of("type", "string",
                                        "description", "Words to look for, e.g. 'cotton saree' or 'rice'. Empty to browse."),
                                "min_price", Map.of("type", "number", "description", "Lowest price in rupees, optional."),
                                "max_price", Map.of("type", "number", "description", "Highest price in rupees, optional."),
                                "sort", Map.of("type", "string",
                                        "enum", List.of("relevance", "price_low_to_high", "price_high_to_low",
                                                "best_sellers", "new_arrivals"),
                                        "description", "Order of results, optional.")),
                        List.of("query")),
                tool("view_cart",
                        "Show the shopper's cart: each line's cart_item_id, product id, name, quantity and "
                                + "subtotal, plus the item total, delivery charge and amount to pay.",
                        Map.of(), List.of()),
                tool("add_to_cart",
                        "Add a product to the shopper's cart. Only when the shopper asks for it.",
                        Map.of("product_id", productId,
                                "quantity", Map.of("type", "integer", "description", "How many, default 1.")),
                        List.of("product_id")),
                tool("remove_from_cart",
                        "Remove one line from the shopper's cart. Only when the shopper asks for it. "
                                + "Call view_cart first to get the cart_item_id.",
                        Map.of("cart_item_id", Map.of("type", "integer", "description", "cart_item_id from view_cart.")),
                        List.of("cart_item_id")),
                tool("view_wishlist",
                        "Show the products saved in the shopper's wishlist, with prices and stock.",
                        Map.of(), List.of()),
                tool("add_to_wishlist",
                        "Save a product to the shopper's wishlist. Only when the shopper asks for it.",
                        Map.of("product_id", productId), List.of("product_id")),
                tool("move_wishlist_to_cart",
                        "Move a product from the shopper's wishlist into the cart. Only when the shopper asks for it.",
                        Map.of("product_id", productId), List.of("product_id")),
                tool("view_orders",
                        "List the shopper's recent orders (newest first) with order number, date, status, "
                                + "amount and items.",
                        Map.of(), List.of()),
                tool("track_order",
                        "Full status of one of the shopper's orders: payment, delivery address city, courier, "
                                + "tracking number and link, expected delivery and recent tracking updates.",
                        Map.of("order_id", Map.of("type", "integer", "description", "The order number, e.g. 5 for order #5.")),
                        List.of("order_id")),
                tool("list_offers",
                        "Current coupon codes (with minimum order amounts) and products that are on discount.",
                        Map.of(), List.of()));
    }

    private static Tool tool(String name, String description, Map<String, Object> properties, List<String> required) {
        Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
        properties.forEach((key, schema) -> props.putAdditionalProperty(key, JsonValue.from(schema)));
        return Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(Tool.InputSchema.builder().properties(props.build()).required(required).build())
                .build();
    }

    private ContentBlockParam runTool(ToolUseBlock use, Shop shop, Map<Long, Product> seen) {
        ToolResultBlockParam.Builder result = ToolResultBlockParam.builder().toolUseId(use.id());
        try {
            Map<String, Object> in = use._input().convert(new TypeReference<Map<String, Object>>() {});
            if (in == null) in = Map.of();
            if (SHOP_TOOLS.contains(use.name())) {
                result.content(shop.run(use.name(), in));
                return ContentBlockParam.ofToolResult(result.build());
            }
            if (!"search_products".equals(use.name())) {
                result.content("Unknown tool.").isError(true);
                return ContentBlockParam.ofToolResult(result.build());
            }
            String query = in.get("query") == null ? "" : String.valueOf(in.get("query"));
            Double min = in.get("min_price") instanceof Number n ? n.doubleValue() : null;
            Double max = in.get("max_price") instanceof Number n ? n.doubleValue() : null;
            String sort = in.get("sort") == null ? "relevance" : String.valueOf(in.get("sort"));

            List<Product> found = shop.search(query, min, max, sort);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Product p : found) {
                seen.putIfAbsent(p.getId(), p);
                rows.add(describe(p));
            }
            result.content(rows.isEmpty() ? "No matching products." : JSON.writeValueAsString(rows));
        } catch (Exception e) {
            log.warn("AI assistant tool {} failed", use.name(), e);
            result.content("That didn't work just now. Apologise briefly and suggest trying again in a moment.").isError(true);
        }
        return ContentBlockParam.ofToolResult(result.build());
    }

    private static Map<String, Object> describe(Product p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        if (p.getBrand() != null && !p.getBrand().isBlank()) m.put("brand", p.getBrand());
        try {
            if (p.getCategory() != null) m.put("category", p.getCategory().getName());
        } catch (Exception ignored) {
            // category not loaded; leave it out
        }
        double price = p.getPrice() == null ? 0 : p.getPrice();
        if (p.getOfferPrice() != null && p.getOfferPrice() < price) {
            m.put("price_inr", p.getOfferPrice());
            m.put("original_price_inr", price);
        } else {
            m.put("price_inr", price);
        }
        int stock = p.getStock() == null ? 0 : p.getStock();
        m.put("stock", stock <= 0 ? "out of stock" : stock <= 5 ? "only " + stock + " left" : "in stock");
        m.put("link", "https://cauverystore.in/product/" + p.getId());
        if (p.getDescription() != null && !p.getDescription().isBlank()) {
            String d = p.getDescription().strip();
            m.put("description", d.length() > 300 ? d.substring(0, 300) + "..." : d);
        }
        return m;
    }

    private static String textOf(Message response) {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : response.content()) {
            block.text().ifPresent(t -> {
                if (sb.length() > 0) sb.append("\n");
                sb.append(t.text());
            });
        }
        return sb.toString().trim();
    }

    /** Built per call from the live FAQs; the text stays identical between calls, so it caches. */
    private String systemPrompt() {
        StringBuilder faqs = new StringBuilder();
        List<Faq> active = new ArrayList<>(faqService.getActive());
        active.sort(Comparator.comparing(f -> f.getId() == null ? Long.MAX_VALUE : f.getId()));
        for (Faq f : active) {
            if (f.getQuestion() == null || f.getAnswer() == null) continue;
            faqs.append("Q: ").append(f.getQuestion().strip()).append("\nA: ").append(f.getAnswer().strip()).append("\n\n");
        }
        if (faqs.length() == 0) faqs.append("(No FAQs published yet.)\n");

        return """
                You are the shopping assistant in the chat window of Cauvery Store (cauverystore.in), \
                an online store in India. You handle every message in this chat yourself: greetings, \
                finding and comparing products, the shopper's cart, wishlist and orders, offers, and \
                questions about the store.

                How to answer:
                - For anything about products, prices, stock or recommendations, call search_products \
                first. Mention only products and prices the tools returned; never make up products, \
                prices, offers, order details or delivery dates. If nothing matches, say so and suggest \
                different words.
                - For the cart, wishlist, orders or delivery status, use view_cart, view_wishlist, \
                view_orders and track_order. For offers, coupons or discounts, use list_offers.
                - You can add to or remove from the cart and wishlist, but only when the shopper clearly \
                asks. If it isn't clear which product they mean, ask first. After a change, say what \
                you did.
                - If a tool says the shopper isn't signed in, ask them to log in; Login and Create \
                Account buttons are shown under your reply.
                - You can't cancel orders, start returns, change addresses or take payments. For those, \
                point to My Orders (cancel before shipping, returns after delivery), checkout, or \
                Contact Us.
                - Prices are in rupees and already include GST. Write them like ₹499.
                - For store policies (delivery, returns, refunds, payment, cancellation), answer only \
                from the store information below. If it isn't covered, say you're not sure and point \
                to the Contact Us page or support@cauverystore.in.
                - Never ask for passwords, OTPs, or card or bank details.
                - If the question has nothing to do with shopping here, say briefly that you can only \
                help with Cauvery Store.
                - Reply in the language the shopper writes in.
                - Keep replies short: a few sentences, or a short list with one product per line. Plain \
                text only, no markdown, no asterisks or headings. Product cards and buttons (View \
                Cart, Checkout, Track Order and so on) are shown under your reply, so you don't need \
                to paste links. Write product names exactly as the tools give them.

                Store information (FAQs):
                """ + faqs;
    }
}
