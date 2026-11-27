package com.cauverystore.service;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The store's side of Postmaster, the separate AI mail agent. The AI is treated as untrusted,
 * because anyone can write anything in an email. It gets masked mail plus facts about that sender
 * only, and it can only suggest a reply. These rules decide whether the suggestion is sent.
 */
@Service
public class PostmasterService {

    public static final String CLAIMED = "POSTMASTER";
    public static final String SENDING = "SENDING";

    private static final Logger log = LoggerFactory.getLogger(PostmasterService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int MAX_PER_SENDER_PER_DAY = 3;
    private static final int MAX_REPLY_CHARS = 1500;
    private static final int MAX_ADMIN_CHARS = 5000;
    private static final double MIN_CONFIDENCE = 0.85;
    private static final int STALE_MINUTES = 20;
    private static final Set<String> SIMPLE_INTENTS = Set.of("ORDER_STATUS", "DELIVERY", "PRODUCT", "POLICY");

    /** Checked on the customer's words, whatever the AI says. These always go to a person. */
    private static final Pattern HIGH_RISK = Pattern.compile(
            "\\b(refunds?|refunded|complain|complaints?|fraud|scam|cheat|cheated|legal|court|consumer forum|police|lawyer|advocate"
                    + "|legal notice|charged|deducted|debited|chargeback|damaged?|broken|defective|wrong|missing|not received"
                    + "|never received|did ?n.?t receive|not delivered|worst|angry|privacy|delete my|change my|change address"
                    + "|cancel|cancell?ed|cancellation|wholesale|bulk|supplier|partnership)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROMISE = Pattern.compile(
            "\\b(we|i)(\\s+will|'ll|\\s+can|\\s+shall)\\s+(refund|replace|compensate|reimburse|waive|cancel|exchange)\\b"
                    + "|\\bcompensat|\\bcoupon|\\bcashback|\\bvoucher|\\bguarantee", Pattern.CASE_INSENSITIVE);
    private static final Pattern MASK_TOKEN = Pattern.compile("\\[(email|phone|pincode|number|name|address)]", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)*");
    private static final Pattern WEB = Pattern.compile(
            "(?:https?://|www\\.)[^\\s)>\\]]+|\\b[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.(?:com|in|net|org|co|io|xyz|ly|me|info|biz|app|link)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STORE_HOST = Pattern.compile(
            "^(?:https?://)?(?:[a-z0-9-]+\\.)*cauverystore\\.in(?:[/?#].*)?$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> STOP_WORDS = Set.of("about", "available", "please", "price", "product", "order", "delivery",
            "when", "where", "what", "which", "have", "does", "your", "with", "from", "this", "that", "there", "will", "hello",
            "thanks", "thank", "store", "cauvery", "would", "could", "like", "want", "need");

    record Facts(List<Map<String, Object>> orders, List<Map<String, Object>> faqs, List<Map<String, Object>> products, Set<String> numbers) {}

    private final InboundEmailRepository inbox;
    private final UserRepository users;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final FaqRepository faqs;
    private final ProductRepository products;
    private final EmailService email;
    private final AuditService audit;
    private final TransactionTemplate tx;

    @Value("${postmaster.enabled:false}")
    boolean enabled = false;

    @Value("${postmaster.live:false}")
    boolean live = false;

    public PostmasterService(InboundEmailRepository inbox, UserRepository users, OrderRepository orders, OrderItemRepository orderItems,
                             FaqRepository faqs, ProductRepository products, EmailService email, AuditService audit,
                             PlatformTransactionManager transactions) {
        this.inbox = inbox;
        this.users = users;
        this.orders = orders;
        this.orderItems = orderItems;
        this.faqs = faqs;
        this.products = products;
        this.email = email;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactions);
    }

    /** Takes new mail once each. Mail that must not reach the AI is settled here and never leaves the store. */
    public Map<String, Object> claim(int limit) {
        if (!enabled) return Map.of("enabled", false, "live", live, "mail", List.of());
        recoverStale();
        LocalDateTime now = now();
        List<Map<String, Object>> mail = new ArrayList<>();
        int size = Math.max(1, Math.min(limit, 25));
        for (InboundEmail e : inbox.findByAutoReplyStatusIsNullAndReceivedAtAfterOrderByIdAsc(now.minusHours(24), PageRequest.of(0, size))) {
            Integer claimed = tx.execute(s -> inbox.claimForPostmaster(e.getId(), CLAIMED, now));
            if (claimed == null || claimed != 1) continue;
            try {
                if (!settledWithoutAi(e)) mail.add(packet(e));
            } catch (Exception ex) {
                log.warn("Postmaster could not prepare inbound email {}: {}", e.getId(), ex.getMessage());
                finish(e.getId(), SupportReplyService.NEEDS_PERSON, "The store could not prepare this email.", SupportReplyService.draft(""));
            }
        }
        return Map.of("enabled", true, "live", live, "mail", mail);
    }

    private boolean settledWithoutAi(InboundEmail e) {
        String from = e.getFromAddress() == null ? "" : e.getFromAddress().trim();
        String skip = SupportReplyService.automatedReason(e, from);
        if (skip != null) {
            finish(e.getId(), SupportReplyService.SKIPPED, skip, null);
            return true;
        }
        String all = customerText(e);
        String hold = null;
        if (e.getToAddresses() == null || !e.getToAddresses().toLowerCase(Locale.ROOT).contains("support@cauverystore.in")) {
            hold = "It was not sent to the support address.";
        } else if (e.getAttachmentNames() != null && !e.getAttachmentNames().isBlank()) {
            hold = "It has an attachment.";
        } else if (SupportReplyService.latestText(e.getTextBody()).isBlank()) {
            hold = "It has no plain text to read.";
        } else if (!SupportReplyService.senderVerified(e.getAuthSummary())) {
            hold = "The sender's address could not be verified.";
        }
        if (hold == null) return false;
        finish(e.getId(), SupportReplyService.NEEDS_PERSON, hold, SupportReplyService.draft(all));
        return true;
    }

    private Map<String, Object> packet(InboundEmail e) {
        Facts f = tx.execute(s -> facts(e));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("subject", mask(e.getSubject()));
        m.put("message", mask(clip(SupportReplyService.latestText(e.getTextBody()), 4000)));
        m.put("orders", f.orders());
        m.put("faqs", f.faqs());
        m.put("products", f.products());
        m.put("history", history(e));
        return m;
    }

    /** Earlier mail from this same verified sender, so Postmaster can follow a conversation. */
    private List<Map<String, Object>> history(InboundEmail e) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (InboundEmail old : inbox.findTop3ByFromAddressIgnoreCaseAndIdLessThanOrderByIdDesc(e.getFromAddress(), e.getId())) {
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("customer", mask(clip(SupportReplyService.latestText(old.getTextBody()), 800)));
            if (SupportReplyService.SENT.equals(old.getAutoReplyStatus())) h.put("ourReply", mask(clip(old.getAutoReplyText(), 800)));
            out.add(h);
        }
        return out;
    }

    /** Only this sender's own orders, the public FAQ and products on sale. No delivery address is included. */
    private Facts facts(InboundEmail e) {
        Set<String> numbers = new HashSet<>();
        List<Map<String, Object>> orderFacts = new ArrayList<>();
        String from = e.getFromAddress() == null ? "" : e.getFromAddress().trim();
        User user = users.findByEmail(from);
        if (user == null) user = users.findByEmail(from.toLowerCase(Locale.ROOT));
        if (user != null) {
            for (Order o : orders.findByUserOrderByCreatedAtDesc(user).stream().limit(5).toList()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("orderNumber", o.getId());
                m.put("status", o.getStatus());
                m.put("courier", o.getCourier());
                m.put("trackingNumber", o.getTrackingNumber());
                m.put("total", o.getTotalAmount());
                List<String> items = new ArrayList<>();
                for (OrderItem i : orderItems.findByOrder_Id(o.getId())) {
                    if (i.getProduct() != null) items.add(i.getProduct().getName());
                }
                m.put("items", items);
                orderFacts.add(m);
                remember(numbers, o.getId(), o.getTrackingNumber(), o.getTotalAmount());
                items.forEach(n -> remember(numbers, n));
            }
        }
        List<Map<String, Object>> faqFacts = new ArrayList<>();
        for (Faq q : faqs.findByActiveTrueOrderBySortOrder().stream().limit(40).toList()) {
            if (q.getQuestion() == null || q.getAnswer() == null) continue;
            faqFacts.add(Map.of("question", q.getQuestion(), "answer", q.getAnswer()));
            remember(numbers, q.getQuestion(), q.getAnswer());
        }
        Map<Long, Map<String, Object>> found = new LinkedHashMap<>();
        for (String word : searchWords(customerText(e))) {
            for (Product p : products.searchProducts(word, null, null, PageRequest.of(0, 3)).getContent()) {
                if (found.size() >= 6 || !ProductService.isAvailableToShoppers(p)) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", p.getName());
                m.put("price", p.getPrice());
                m.put("mrp", p.getMrp());
                m.put("inStock", p.getStock() != null && p.getStock() > 0);
                found.put(p.getId(), m);
                remember(numbers, p.getName(), p.getPrice(), p.getMrp());
            }
        }
        return new Facts(orderFacts, faqFacts, new ArrayList<>(found.values()), numbers);
    }

    /** The agent's suggestion. It is sent only if every rule passes; otherwise it becomes your draft. */
    public Map<String, Object> decide(Long id, Map<String, Object> body) {
        InboundEmail e = inbox.findById(id).orElseThrow(() -> new IllegalArgumentException("That email does not exist."));
        if (!CLAIMED.equals(e.getAutoReplyStatus())) return Map.of("status", String.valueOf(e.getAutoReplyStatus()), "changed", false);

        String action = text(body.get("action")).toLowerCase(Locale.ROOT);
        String intent = text(body.get("intent")).toUpperCase(Locale.ROOT);
        String reply = clean(text(body.get("reply")));
        double confidence = number(body.get("confidence"));
        String given = clip(clean(text(body.get("reason"))), 150);

        String refusal;
        if ("reply".equals(action)) {
            Facts f = tx.execute(s -> facts(e));
            refusal = refusal(e, intent, confidence, reply, f);
        } else {
            refusal = given.isBlank() ? "Postmaster asked for a person to answer this." : "Postmaster: " + given;
        }
        if (refusal != null) {
            finish(id, SupportReplyService.NEEDS_PERSON, refusal, draftFrom(reply, e));
            return Map.of("status", SupportReplyService.NEEDS_PERSON, "changed", true);
        }

        String out = SupportReplyService.wrap(reply);
        String reason = "Postmaster answered: " + intent;
        if (!live) {
            finish(id, SupportReplyService.PREVIEW, reason, out);
            return Map.of("status", SupportReplyService.PREVIEW, "changed", true);
        }
        Integer moved = tx.execute(s -> inbox.moveAutoReply(id, CLAIMED, SENDING));
        if (moved == null || moved != 1) return Map.of("status", "ALREADY_HANDLED", "changed", false);
        if (email.sendSupportReply(e.getFromAddress(), SupportReplyService.replySubject(e.getSubject()), out)) {
            finish(id, SupportReplyService.SENT, reason, out);
            return Map.of("status", SupportReplyService.SENT, "changed", true);
        }
        finish(id, SupportReplyService.NEEDS_PERSON, "The reply could not be sent.", out);
        return Map.of("status", SupportReplyService.NEEDS_PERSON, "changed", true);
    }

    /** Null means safe to send. */
    String refusal(InboundEmail e, String intent, double confidence, String reply, Facts f) {
        if (!SupportReplyService.senderVerified(e.getAuthSummary())) return "The sender's address could not be verified.";
        if (!SIMPLE_INTENTS.contains(intent)) return "It is not a simple question.";
        if (confidence < MIN_CONFIDENCE) return "Postmaster was not sure enough.";
        if (HIGH_RISK.matcher(customerText(e)).find()) return "It may involve a refund, complaint, payment or similar.";
        if (reply.length() < 20 || reply.length() > MAX_REPLY_CHARS) return "The suggested reply was empty or too long.";
        if (MASK_TOKEN.matcher(reply).find()) return "The suggested reply contains hidden personal details.";
        if (PROMISE.matcher(reply).find()) return "The suggested reply makes a promise only a person can make.";
        Matcher mail = EMAIL.matcher(reply);
        while (mail.find()) {
            if (!"support@cauverystore.in".equalsIgnoreCase(mail.group())) return "The suggested reply contains an outside email address.";
        }
        String withoutMail = EMAIL.matcher(reply).replaceAll(" ");
        Matcher web = WEB.matcher(withoutMail);
        while (web.find()) {
            if (!STORE_HOST.matcher(web.group()).matches()) return "The suggested reply contains an outside link.";
        }
        Matcher n = NUMBER.matcher(reply);
        while (n.find()) {
            if (!f.numbers().contains(normal(n.group()))) return "The suggested reply contains a number the store could not confirm.";
        }
        LocalDateTime dayAgo = now().minusHours(24);
        if (inbox.countByFromAddressIgnoreCaseAndAutoReplyStatusAndAutoRepliedAtAfter(e.getFromAddress(), SupportReplyService.SENT, dayAgo)
                >= MAX_PER_SENDER_PER_DAY) {
            return "This sender already had automatic replies today.";
        }
        return null;
    }

    /** You approve a draft in Admin -> Inbox. It can go out only once. */
    public Map<String, Object> adminSend(Long id, String rawText, String who) {
        InboundEmail e = inbox.findById(id).orElseThrow(() -> new IllegalArgumentException("That email does not exist."));
        String prior = e.getAutoReplyStatus();
        if (!SupportReplyService.NEEDS_PERSON.equals(prior) && !SupportReplyService.PREVIEW.equals(prior)) {
            throw new IllegalStateException("This email has no draft waiting to be sent.");
        }
        String text = clean(rawText == null ? "" : rawText);
        if (text.length() < 10 || text.length() > MAX_ADMIN_CHARS) throw new IllegalArgumentException("Write a reply before sending.");
        if (MASK_TOKEN.matcher(text).find()) throw new IllegalArgumentException("Replace the hidden details in square brackets first.");
        Integer moved = tx.execute(s -> inbox.moveAutoReply(id, prior, SENDING));
        if (moved == null || moved != 1) throw new IllegalStateException("This email was already answered.");
        if (!email.sendSupportReply(e.getFromAddress(), SupportReplyService.replySubject(e.getSubject()), text)) {
            tx.execute(s -> inbox.moveAutoReply(id, SENDING, prior));
            throw new IllegalStateException("The reply could not be sent. Please try again.");
        }
        tx.executeWithoutResult(s -> inbox.recordAutoReply(id, SupportReplyService.SENT, "Approved and sent by an admin.", text, now()));
        audit.log(null, who, "POSTMASTER_ADMIN_SENT", "InboundEmail", id, "Draft approved and sent", null);
        return Map.of("status", SupportReplyService.SENT);
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled);
        m.put("live", live);
        m.put("waitingForYou", inbox.countByAutoReplyStatus(SupportReplyService.NEEDS_PERSON));
        m.put("previews", inbox.countByAutoReplyStatus(SupportReplyService.PREVIEW));
        m.put("sentToday", inbox.countByAutoReplyStatusAndAutoRepliedAtAfter(SupportReplyService.SENT, now().toLocalDate().atStartOfDay()));
        return m;
    }

    /** If the agent stops halfway, its mail comes to you rather than being lost. */
    private void recoverStale() {
        LocalDateTime before = now().minusMinutes(STALE_MINUTES);
        for (InboundEmail e : inbox.findByAutoReplyStatusAndAutoRepliedAtBefore(CLAIMED, before)) {
            finish(e.getId(), SupportReplyService.NEEDS_PERSON, "Postmaster did not finish this email.", SupportReplyService.draft(customerText(e)));
        }
        for (InboundEmail e : inbox.findByAutoReplyStatusAndAutoRepliedAtBefore(SENDING, before)) {
            finish(e.getId(), SupportReplyService.NEEDS_PERSON, "Sending stopped midway. Check Gmail before replying, it may have gone out.",
                    SupportReplyService.draft(customerText(e)));
        }
    }

    private void finish(Long id, String status, String reason, String text) {
        String shortReason = clip(reason, 200);
        tx.executeWithoutResult(s -> inbox.recordAutoReply(id, status, shortReason, text, now()));
        audit.log(null, "postmaster", "POSTMASTER_" + status, "InboundEmail", id, shortReason, null);
    }

    private static String draftFrom(String reply, InboundEmail e) {
        if (reply == null || reply.isBlank()) return SupportReplyService.draft(customerText(e));
        return "Hello,\n\n" + clip(reply, 3000) + "\n\nCauvery Store";
    }

    /** Hides personal details before mail leaves the store. Best effort: unusual wording can slip through. */
    static String mask(String s) {
        if (s == null) return "";
        String t = EMAIL.matcher(s).replaceAll("[email]");
        t = t.replaceAll("(?i)\\b(my name is|this is)\\s+[A-Z][\\p{L}.]*(\\s+[A-Z][\\p{L}.]*){0,2}", "$1 [name]");
        t = t.replaceAll("(?im)\\b(address)\\s*[:\\-].*$", "$1: [address]");
        t = t.replaceAll("(?im)^((?:best\\s+)?regards|thanks(?: and regards)?|thank you|sincerely)\\s*,?\\s*\\R\\s*\\S.*$", "$1,\n[name]");
        t = t.replaceAll("(?<!\\d)(?:\\+?91[\\s-]?)?[6-9]\\d{4}[\\s-]?\\d{5}(?!\\d)", "[phone]");
        t = t.replaceAll("(?<!\\d)\\d(?:[ -]?\\d){11,18}(?!\\d)", "[number]");
        t = t.replaceAll("(?i)(?<!order\\s)(?<!order)(?<!#)(?<!\\d)[1-9]\\d{5}(?!\\d)", "[pincode]");
        return t;
    }

    private static List<String> searchWords(String all) {
        Set<String> words = new LinkedHashSet<>();
        for (String w : all.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            if (w.length() >= 4 && !STOP_WORDS.contains(w)) words.add(w);
        }
        return words.stream().sorted(Comparator.comparingInt(String::length).reversed()).limit(3).toList();
    }

    private static String customerText(InboundEmail e) {
        return (e.getSubject() == null ? "" : e.getSubject()) + "\n" + SupportReplyService.latestText(e.getTextBody());
    }

    private static void remember(Set<String> numbers, Object... values) {
        for (Object v : values) {
            if (v == null) continue;
            Matcher m = NUMBER.matcher(String.valueOf(v));
            while (m.find()) numbers.add(normal(m.group()));
        }
    }

    static String normal(String n) {
        return n.replace(",", "").replaceAll("\\.0+$", "");
    }

    private static String clean(String s) {
        return s.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
    }

    private static String text(Object o) {
        return o == null ? "" : o.toString();
    }

    private static double number(Object o) {
        try {
            return o == null ? 0 : Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(IST);
    }
}
