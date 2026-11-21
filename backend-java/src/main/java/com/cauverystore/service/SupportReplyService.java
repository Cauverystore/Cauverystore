package com.cauverystore.service;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Answers simple support mail from real store data, using fixed rules rather than generated text.
 * Only order status and close FAQ matches are answered. Everything else goes to a person with a
 * draft. Nothing is sent unless SUPPORT_AUTOREPLY_LIVE=true.
 */
@Service
public class SupportReplyService {

    public static final String WORKING = "WORKING";
    public static final String SENT = "SENT";
    public static final String PREVIEW = "PREVIEW";
    public static final String NEEDS_PERSON = "NEEDS_PERSON";
    public static final String SKIPPED = "SKIPPED";

    private static final Logger log = LoggerFactory.getLogger(SupportReplyService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int MAX_WORDS = 60;
    private static final int MAX_PER_SENDER_PER_DAY = 2;
    private static final int BATCH = 25;

    private static final Pattern NEEDS_A_PERSON = Pattern.compile(
            "\\b(refunds?|refunded|returns?|returned|replace|replacement|exchange|cancel|cancell?ed|cancellation|damaged?|broken"
                    + "|defect|defective|wrong|missing|complain|complaint|complaints|fraud|scam|cheat|cheated|legal|court|consumer"
                    + "|police|lawyer|advocate|notice|charged|deducted|debited|money|payment|paid|invoice|gst|late|delay|delayed"
                    + "|not received|never received|did ?n.?t receive|not delivered|worst|angry|privacy|delete my|change my"
                    + "|change address|wholesale|bulk|supplier|partnership)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER_STATUS = Pattern.compile(
            "where('?s| is) my (order|parcel|package|item)|\\btrack(ing)?\\b|\\border status\\b|status of my order|\\bdelivery status\\b"
                    + "|when will (my|the) (order|parcel|package|item)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER_NUMBER = Pattern.compile(
            "\\border\\s*(?:no\\.?|number|id)?\\s*[:#-]?\\s*(\\d{1,12})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTOMATED_SENDER = Pattern.compile(
            "^(no-?reply|do-?not-?reply|mailer-daemon|postmaster|bounces?|notifications?|alerts?)[@+.-].*", Pattern.CASE_INSENSITIVE);
    private static final Set<String> STOP_WORDS = Set.of("a", "an", "and", "are", "can", "do", "does", "for", "from", "have", "how",
            "i", "in", "is", "it", "me", "my", "of", "on", "or", "please", "the", "there", "to", "we", "what", "when", "where",
            "which", "will", "with", "you", "your", "hello", "hi", "thanks", "thank");
    private static final Map<String, String> STATUS_TEXT = Map.of(
            "PLACED", "We have received it and are preparing it.",
            "COURIER_ASSIGNED", "A courier has been assigned and it will be picked up soon.",
            "SHIPPED", "It has been shipped and is on its way.");

    /** What to do with one email. */
    record Plan(String status, String reason, String text) {}

    private final InboundEmailRepository inbox;
    private final UserRepository users;
    private final OrderRepository orders;
    private final FaqRepository faqs;
    private final EmailService email;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${SUPPORT_AUTOREPLY_LIVE:false}")
    boolean live = false;

    /** When Postmaster is on it handles all new mail, so an email can never get two replies. */
    @Value("${postmaster.enabled:false}")
    boolean postmasterEnabled = false;

    public SupportReplyService(InboundEmailRepository inbox, UserRepository users, OrderRepository orders, FaqRepository faqs,
                               EmailService email, AuditService audit, PlatformTransactionManager transactions) {
        this.inbox = inbox;
        this.users = users;
        this.orders = orders;
        this.faqs = faqs;
        this.email = email;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactions);
    }

    @Scheduled(initialDelayString = "${support.autoreply.first-check-ms:90000}", fixedDelayString = "${support.autoreply.check-ms:120000}")
    public void scheduled() {
        try {
            sweep();
        } catch (Exception e) {
            log.warn("Support reply check failed: {}", e.getMessage());
        }
    }

    /** Handles new mail once each. The claim stops a second server or run from replying again. */
    public int sweep() {
        if (postmasterEnabled) return 0;
        if (!running.compareAndSet(false, true)) return 0;
        int done = 0;
        try {
            LocalDateTime since = LocalDateTime.now(IST).minusHours(24);
            for (InboundEmail e : inbox.findByAutoReplyStatusIsNullAndReceivedAtAfterOrderByIdAsc(since, PageRequest.of(0, BATCH))) {
                Integer claimed = tx.execute(s -> inbox.claimForAutoReply(e.getId(), WORKING));
                if (claimed == null || claimed != 1) continue;
                handle(e);
                done++;
            }
            return done;
        } finally {
            running.set(false);
        }
    }

    private void handle(InboundEmail e) {
        Plan plan;
        try {
            plan = plan(e);
        } catch (Exception ex) {
            log.warn("Could not prepare a reply for inbound email {}: {}", e.getId(), ex.getMessage());
            plan = new Plan(NEEDS_PERSON, "The store could not prepare a reply.", draft(""));
        }
        String status = plan.status();
        String reason = plan.reason();
        if (SENT.equals(status)) {
            if (!live) {
                status = PREVIEW;
            } else if (!email.sendSupportReply(e.getFromAddress(), replySubject(e.getSubject()), plan.text())) {
                status = NEEDS_PERSON;
                reason = "The automatic reply could not be sent.";
            }
        }
        // Only the reply fields: the webhook or an admin may be changing this email at the same moment.
        String finalStatus = status, finalReason = clip(reason, 200), text = plan.text();
        tx.executeWithoutResult(s -> inbox.recordAutoReply(e.getId(), finalStatus, finalReason, text, LocalDateTime.now(IST)));
        audit.log(null, "system", "SUPPORT_AUTO_REPLY_" + finalStatus, "InboundEmail", e.getId(), finalReason, null);
    }

    /** Decides the outcome. Sends nothing. A SENT plan means "safe to send". */
    Plan plan(InboundEmail e) {
        String from = e.getFromAddress() == null ? "" : e.getFromAddress().trim();
        String skip = automatedReason(e, from);
        if (skip != null) return new Plan(SKIPPED, skip, null);

        String subject = e.getSubject() == null ? "" : e.getSubject().trim();
        String body = latestText(e.getTextBody());
        String all = subject + "\n" + body;

        if (!mentions(e.getToAddresses(), "support@cauverystore.in")) return person("It was not sent to the support address.", all);
        if (subject.toLowerCase(Locale.ROOT).matches("^(re|fw|fwd)\\s*:.*")) return person("It is a reply or forward, so a person should read it.", all);
        if (e.getAttachmentNames() != null && !e.getAttachmentNames().isBlank()) return person("It has an attachment.", all);
        if (body.isBlank()) return person("It has no plain text to read.", all);
        if (words(all) > MAX_WORDS) return person("It is too long for an automatic reply.", all);
        if (NEEDS_A_PERSON.matcher(all).find()) return person("It may involve a refund, complaint, payment or similar.", all);
        if (!senderVerified(e.getAuthSummary())) return person("The sender's address could not be verified.", all);

        LocalDateTime dayAgo = LocalDateTime.now(IST).minusHours(24);
        if (inbox.countByFromAddressIgnoreCaseAndAutoReplyStatusAndAutoRepliedAtAfter(from, SENT, dayAgo) >= MAX_PER_SENDER_PER_DAY) {
            return person("This sender already had automatic replies today.", all);
        }
        if (ORDER_STATUS.matcher(all).find()) return orderPlan(from, all);

        Faq faq = bestFaq(all, faqs.findByActiveTrueOrderBySortOrder());
        if (faq == null) return person("The store has no clear answer to this.", all);
        return new Plan(SENT, "Answered from the FAQ: " + clip(faq.getQuestion(), 120), wrap(faq.getAnswer().trim()));
    }

    private Plan orderPlan(String from, String all) {
        User user = users.findByEmail(from);
        if (user == null) user = users.findByEmail(from.toLowerCase(Locale.ROOT));
        if (user == null) return person("No customer account matches this sender.", all);
        List<Order> mine = orders.findByUserOrderByCreatedAtDesc(user);

        Matcher m = ORDER_NUMBER.matcher(all);
        List<Order> answer;
        if (m.find()) {
            long wanted;
            try {
                wanted = Long.parseLong(m.group(1));
            } catch (NumberFormatException ex) {
                return person("The order number could not be read.", all);
            }
            Optional<Order> match = mine.stream().filter(o -> Objects.equals(o.getId(), wanted)).findFirst();
            if (match.isEmpty()) return person("That order number does not belong to this sender.", all);
            if (!STATUS_TEXT.containsKey(status(match.get()))) return person("That order is closed or needs checking.", all);
            answer = List.of(match.get());
        } else {
            answer = mine.stream().filter(o -> STATUS_TEXT.containsKey(status(o))).limit(3).toList();
            if (answer.isEmpty()) return person("This customer has no open order.", all);
        }

        StringBuilder sb = new StringBuilder();
        for (Order o : answer) {
            sb.append("Order #").append(o.getId()).append(": ").append(STATUS_TEXT.get(status(o))).append('\n');
            if (o.getCourier() != null && !o.getCourier().isBlank()) sb.append("Courier: ").append(o.getCourier().trim()).append('\n');
            if (o.getTrackingNumber() != null && !o.getTrackingNumber().isBlank()) sb.append("Tracking number: ").append(o.getTrackingNumber().trim()).append('\n');
            sb.append('\n');
        }
        return new Plan(SENT, "Answered with the status of the sender's own order.", wrap(sb.toString().trim()));
    }

    private static String status(Order o) {
        return o.getStatus() == null ? "" : o.getStatus().toUpperCase(Locale.ROOT);
    }

    private static Plan person(String reason, String all) {
        return new Plan(NEEDS_PERSON, reason, draft(all));
    }

    static String wrap(String answer) {
        return "Hello,\n\nThank you for writing to Cauvery Store.\n\n" + answer
                + "\n\nThis is an automatic reply. If you need more help, reply to this email and a person from our team will read it.\n\nCauvery Store";
    }

    /** A starting point for the person who answers. It promises nothing. */
    static String draft(String all) {
        boolean problem = Pattern.compile("\\b(refund|return|replace|exchange|damaged?|broken|defective|wrong|missing)\\b",
                Pattern.CASE_INSENSITIVE).matcher(all).find();
        String middle = problem
                ? "We are sorry for the trouble. Please share your order number and, if the item is damaged or wrong, a photo of it so we can check this for you."
                : "We have read your message and are looking into it.";
        return "Hello,\n\nThank you for contacting Cauvery Store. " + middle + "\n\nCauvery Store";
    }

    /** Mail that must never get any reply, which is what stops mail loops. */
    static String automatedReason(InboundEmail e, String from) {
        if (Boolean.TRUE.equals(e.getAutomated())) return "Sent by an automatic system.";
        if (!from.contains("@")) return "It has no usable sender address.";
        String lower = from.toLowerCase(Locale.ROOT);
        if (lower.endsWith("@cauverystore.in")) return "Sent from the store's own address.";
        if (AUTOMATED_SENDER.matcher(lower).matches()) return "Sent by an automatic address.";
        String subject = e.getSubject() == null ? "" : e.getSubject().toLowerCase(Locale.ROOT);
        if (subject.startsWith("[cauvery inbox]") || subject.contains("out of office") || subject.contains("automatic reply")
                || subject.contains("delivery status notification") || subject.contains("undeliverable")) {
            return "It looks like an automatic message.";
        }
        return null;
    }

    /** DMARC must pass, otherwise the From address could be forged to read someone else's order. */
    static boolean senderVerified(String authSummary) {
        return authSummary != null && authSummary.toLowerCase(Locale.ROOT).matches(".*\\bdmarc pass\\b.*");
    }

    /** The customer's new words, without the quoted earlier message. */
    static String latestText(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (t.startsWith(">") || t.startsWith("-----Original") || t.matches("(?i)^on .+ wrote:$")) break;
            out.append(t).append('\n');
        }
        return out.toString().trim();
    }

    /** Only a close, clear match: most of the FAQ's question words appear, and no other FAQ ties. */
    static Faq bestFaq(String message, List<Faq> list) {
        Set<String> said = tokens(message);
        Faq best = null;
        double top = 0, second = 0;
        for (Faq f : list) {
            if (f.getQuestion() == null || f.getAnswer() == null || f.getAnswer().isBlank()) continue;
            Set<String> q = tokens(f.getQuestion());
            if (q.size() < 2) continue;
            long hit = q.stream().filter(said::contains).count();
            double score = (double) hit / q.size();
            if (score > top) {
                second = top;
                top = score;
                best = f;
            } else if (score > second) {
                second = score;
            }
        }
        return top >= 0.8 && top > second ? best : null;
    }

    private static Set<String> tokens(String s) {
        Set<String> out = new HashSet<>();
        for (String w : s.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.isEmpty() || STOP_WORDS.contains(w)) continue;
            out.add(w.length() > 3 && w.endsWith("s") ? w.substring(0, w.length() - 1) : w);
        }
        return out;
    }

    private static int words(String s) {
        String t = s.trim();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }

    private static boolean mentions(String addresses, String address) {
        return addresses != null && addresses.toLowerCase(Locale.ROOT).contains(address);
    }

    static String replySubject(String subject) {
        String s = subject == null ? "" : subject.replaceAll("[\\r\\n]+", " ").trim();
        return "Re: " + (s.isEmpty() ? "Your message to Cauvery Store" : clip(s, 150));
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
