package com.cauverystore.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a CBIC "Central Tax (Rate)" amending notification into the rate changes it makes.
 *
 * <h2>Why rules, and why that is enough</h2>
 *
 * An amending notification is not free prose. It is drafted from a handful of fixed legal
 * formulas - "in Schedule I - 2.5%", "against S. No. 150, for the entry in column (2), the entry
 * ... shall be substituted", "after S. No. 4 ... the following ... shall be inserted", "shall be
 * omitted", "shall come into force on" - and the same formulas have been used since 2017. Those
 * can be matched exactly. Nothing here guesses at meaning: a clause either fits a formula or is
 * handed back as unread.
 *
 * <h2>What it will not do</h2>
 *
 * It does not decide a rate. Everything it produces is a proposal carrying the clause it came
 * from, and a person accepts or rejects each one against that clause. It reads only what the
 * text states: when an entry's codes are substituted, the codes dropped from that entry are not
 * in the notification at all, so they cannot be reported from it - the proposal says so rather
 * than implying the list is complete.
 *
 * <h2>Why so much is marked for attention</h2>
 *
 * A PDF table cell that wraps loses its shape when read as text: "2403 19 29" has come out as
 * "2403 19" with the "29" a line lower, which reads as a perfectly valid six-digit heading and
 * would tax the whole of 2403 19 at the wrong rate. The reader cannot repair that, but it can
 * notice the signs - a code shorter than its neighbours in the same cell, loose digits further
 * along the row, an "other than" carve-out - and refuse to call the row clean.
 */
@Component
public class CbicNotificationReader {

    public enum Operation {
        /** New entry added to a schedule: its codes take that schedule's rate. */
        INSERT,
        /** An existing entry's code list replaced: the listed codes take the schedule's rate. */
        SUBSTITUTE_CODES,
        /** Entries or a whole schedule removed. Which codes that covers is not in the text. */
        OMIT,
        /** A clause no formula matched. */
        UNREAD
    }

    /** One thing the notification does. Codes are empty for OMIT and UNREAD. */
    public record Change(Operation operation, String schedule, Double totalRate, String serialNo,
                         List<String> codes, String description, String clause,
                         boolean needsAttention, String attentionReason) {}

    /** Everything read from one notification. */
    public record Reading(String notificationNumber, String taxKind, LocalDate effectiveFrom,
                          List<Change> changes, List<String> warnings) {

        public boolean readAnything() {
            return changes.stream().anyMatch(c -> c.operation() != Operation.UNREAD);
        }
    }

    private static final Pattern NOTIFICATION_NO = Pattern.compile(
            "Notification\\s+No\\.?\\s*(\\d{1,3})\\s*/\\s*(\\d{4})\\s*-?\\s*"
                    + "(Central|Integrated|Union Territory)\\s+Tax\\s*\\(\\s*Rate\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IN_FORCE = Pattern.compile(
            "come\\s+into\\s+force\\s+(?:on|from|with\\s+effect\\s+from)\\s+(?:the\\s+)?"
                    + "(\\d{1,2})\\s*(?:st|nd|rd|th)?\\s*(?:day\\s+of\\s+)?([A-Za-z]+)\\s*,?\\s*(\\d{4})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FORCE_PARAGRAPH = Pattern.compile(
            "\\b\\d\\.\\s*This\\s+notification\\s+shall", Pattern.CASE_INSENSITIVE);

    /** "in Schedule I - 2.5%," and "the Schedule VII - 14%,". The dash varies and is often mangled. */
    private static final Pattern SCHEDULE = Pattern.compile(
            "(?:\\bin\\s+)?(?:the\\s+)?Schedule\\s+(VII|VI|IV|V|III|II|I)\\b\\s*[^0-9A-Za-z]{0,4}\\s*"
                    + "(\\d{1,2}(?:\\.\\d{1,3})?)\\s*%",
            Pattern.CASE_INSENSITIVE);

    private static final String QUOTE_OPEN = "[\"“]";
    private static final String QUOTE_CLOSE = "[\"”]";

    private static final Pattern SUBSTITUTE = Pattern.compile(
            "against\\s+S\\.?\\s*No\\.?\\s*(\\d+[A-Z]{0,2})\\s*,?\\s*"
                    + "for\\s+the\\s+entry\\s+in\\s+column\\s*\\((\\d)\\)\\s*,?\\s*the\\s+entry\\s*"
                    + QUOTE_OPEN + "([^\"“”]*)" + QUOTE_CLOSE + "\\s*shall\\s+be\\s+substituted",
            Pattern.CASE_INSENSITIVE);

    /** The sentence that says rows are being added, without the rows themselves. */
    private static final Pattern INSERT_CLAUSE = Pattern.compile(
            "after\\s+S\\.?\\s*No\\.?\\s*(\\d+)([A-Z]{0,2})\\b[^\"“”]{0,200}?shall\\s+be\\s+inserted\\s*,?\\s*"
                    + "namely\\s*:?\\s*[-–—]?",
            Pattern.CASE_INSENSITIVE);

    /** A quoted table of new rows: it opens on a serial number, which a quoted code list never does. */
    private static final Pattern INSERTED_TABLE = Pattern.compile(
            QUOTE_OPEN + "\\s*((\\d{1,3})([A-Z]{0,2})\\.\\s+\\d.*?)" + QUOTE_CLOSE + "\\s*;",
            Pattern.DOTALL);

    private static final Pattern OMIT_ENTRIES = Pattern.compile(
            "S\\.?\\s*Nos?\\.?\\s*((?:\\d+[A-Z]{0,2}\\s*(?:,|and|to)?\\s*)+)\\s*and\\s+the\\s+entr(?:y|ies)\\s+"
                    + "relating\\s+thereto\\s*,?\\s*shall\\s+be\\s+omitted",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern OMIT_SCHEDULE = Pattern.compile(
            "^\\s*,?\\s*and\\s+the\\s+entries\\s+relating\\s+thereto\\s*,?\\s*shall\\s+be\\s+omitted",
            Pattern.CASE_INSENSITIVE);

    /** A row of an inserted table: "4A. 2403 19 21, 2403 19 29 Biris". */
    private static final Pattern ROW_SERIAL = Pattern.compile("(?<![\\d.])(\\d{1,3}[A-Z]{0,2})\\.\\s+(?=\\d)");

    private static final Pattern LEADING_CODES = Pattern.compile("^[\\d\\s,]*\\d");

    /** Two or four digits standing alone: the tail of a code that wrapped out of its cell. */
    private static final Pattern LOOSE_DIGITS = Pattern.compile("(?<![\\w.])\\d{2}(?:\\s+\\d{2})?(?![\\w.%])");

    /** Extracts a PDF's text and reads it. */
    public Reading readPdf(byte[] pdf) {
        StringBuilder text = new StringBuilder();
        PdfReader reader = null;
        try {
            reader = new PdfReader(pdf);
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
        } catch (Exception e) {
            throw new IllegalStateException("The notification PDF could not be read as text: "
                    + e.getMessage(), e);
        } finally {
            if (reader != null) reader.close();
        }
        return read(text.toString());
    }

    /** Reads a notification's text. */
    public Reading read(String rawText) {
        List<Change> changes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        // Legal drafting does not depend on line breaks, and a PDF's are arbitrary anyway.
        String text = rawText == null ? "" : rawText.replace(' ', ' ').replaceAll("\\s+", " ").trim();

        String number = null;
        String taxKind = null;
        Matcher no = NOTIFICATION_NO.matcher(text);
        if (no.find()) {
            taxKind = capitalise(no.group(3)) + " Tax (Rate)";
            number = String.format("%02d/%s-%s", Integer.parseInt(no.group(1)), no.group(2), taxKind);
        } else {
            warnings.add("No \"Notification No. NN/YYYY-... Tax (Rate)\" heading was found, so this "
                    + "may not be a rate notification at all.");
        }

        LocalDate effective = null;
        int forceAt = -1;
        Matcher force = IN_FORCE.matcher(text);
        if (force.find()) {
            effective = date(force.group(1), force.group(2), force.group(3));
            forceAt = force.start();
        }
        if (effective == null) {
            warnings.add("The date it comes into force could not be read. Nothing can be applied "
                    + "without one - take it from the notification's last paragraph.");
        }

        // Integrated Tax schedules state the whole rate; Central and Union Territory Tax state
        // half of it, the other half being the State's.
        double multiplier = taxKind != null && taxKind.startsWith("Integrated") ? 1.0 : 2.0;

        // Only the operative part: after "In the said notification" and before the
        // coming-into-force paragraph. The footnote names older notifications and schedules and
        // must not be read as amendments.
        int start = text.toLowerCase(Locale.ROOT).indexOf("in the said notification");
        int end = forceAt < 0 ? text.length() : paragraphStart(text, forceAt);
        if (start < 0 || start >= end) {
            warnings.add("The usual opening \"In the said notification\" was not found. This does "
                    + "not look like an amending notification - a new principal notification or "
                    + "a different kind of document has to be read by a person.");
            return new Reading(number, taxKind, effective, changes, warnings);
        }
        String body = text.substring(start, end);

        List<int[]> marks = new ArrayList<>();
        List<String[]> heads = new ArrayList<>();
        Matcher sched = SCHEDULE.matcher(body);
        while (sched.find()) {
            marks.add(new int[] {sched.start(), sched.end()});
            heads.add(new String[] {sched.group(1).toUpperCase(Locale.ROOT), sched.group(2)});
        }
        if (marks.isEmpty()) {
            changes.add(unread(null, null, body, "No schedule was named, so no rate could be tied "
                    + "to anything in it."));
            return new Reading(number, taxKind, effective, changes, warnings);
        }

        StringBuilder leftover = new StringBuilder(body);
        List<List<Change>> perSection = new ArrayList<>();
        for (int i = 0; i < marks.size(); i++) perSection.add(new ArrayList<>());
        readInsertions(body, leftover, marks, heads, multiplier, perSection);

        for (int i = 0; i < marks.size(); i++) {
            int from = marks.get(i)[1];
            int to = i + 1 < marks.size() ? marks.get(i + 1)[0] : body.length();
            double total = Double.parseDouble(heads.get(i)[1]) * multiplier;
            readSection(heads.get(i)[0], total, body, leftover, from, to, perSection.get(i));
            changes.addAll(perSection.get(i));
        }
        return new Reading(number, taxKind, effective, changes, warnings);
    }

    /** An "inserted, namely" sentence: which schedule it sits under and the serial it follows. */
    private record Clause(int section, int after, String afterSuffix, String text) {}

    /**
     * Pairs each "shall be inserted, namely" sentence with the table of rows it introduces.
     *
     * They cannot simply be read in sequence. A PDF's text comes out in drawing order, not
     * reading order, and a table is often drawn after the paragraphs around it: in 19/2025 the
     * biris row for Schedule II (18%) is extracted below the heading of Schedule III (40%).
     * Taking the nearest schedule would have taxed biris at 40%.
     *
     * The drafting gives a way to tell which table belongs where. Rows added "after S. No. 4"
     * are numbered 4A or 5; rows added "after S. No. 13" start at 14. A table is paired with
     * the one sentence its first serial number can follow. When that does not single one out,
     * order is used instead and every row is marked for a person to confirm.
     */
    private void readInsertions(String body, StringBuilder leftover, List<int[]> marks,
                                List<String[]> heads, double multiplier, List<List<Change>> perSection) {
        List<Clause> clauses = new ArrayList<>();
        Matcher c = INSERT_CLAUSE.matcher(body);
        while (c.find()) {
            int section = sectionOf(marks, c.start());
            if (section < 0) continue;
            blank(leftover, c.start(), c.end());
            clauses.add(new Clause(section, Integer.parseInt(c.group(1)), c.group(2), c.group().trim()));
        }

        boolean[] used = new boolean[clauses.size()];
        Matcher t = INSERTED_TABLE.matcher(body);
        while (t.find()) {
            blank(leftover, t.start(), t.end());
            String block = t.group(1);
            int first = Integer.parseInt(t.group(2));
            boolean lettered = !t.group(3).isEmpty();

            List<Integer> fits = new ArrayList<>();
            for (int i = 0; i < clauses.size(); i++) {
                if (used[i]) continue;
                int after = clauses.get(i).after();
                if ((lettered && first == after) || (!lettered && first == after + 1)) fits.add(i);
            }
            int chosen = -1;
            String pairingDoubt = null;
            if (fits.size() == 1) {
                chosen = fits.get(0);
            } else {
                for (int i = 0; i < clauses.size() && chosen < 0; i++) if (!used[i]) chosen = i;
                pairingDoubt = "This table could not be tied to its schedule by its serial numbers, "
                        + "so the schedule - and therefore the rate - was taken from the order of "
                        + "the text. Confirm the schedule against the notification.";
            }
            if (chosen < 0) {
                int section = Math.max(sectionOf(marks, t.start()), 0);
                perSection.get(section).add(unread(heads.get(section)[0], null, block,
                        "A table of new entries was found with no sentence saying which schedule "
                                + "it is inserted into."));
                continue;
            }
            used[chosen] = true;
            Clause clause = clauses.get(chosen);
            String schedule = heads.get(clause.section())[0];
            double total = Double.parseDouble(heads.get(clause.section())[1]) * multiplier;
            String prefix = "Schedule " + schedule + ", after S. No. " + clause.after()
                    + clause.afterSuffix() + ": ";

            List<int[]> rows = new ArrayList<>();
            List<String> serials = new ArrayList<>();
            Matcher row = ROW_SERIAL.matcher(block);
            while (row.find()) {
                rows.add(new int[] {row.start(), row.end()});
                serials.add(row.group(1));
            }
            for (int r = 0; r < rows.size(); r++) {
                int rowEnd = r + 1 < rows.size() ? rows.get(r + 1)[0] : block.length();
                String rowText = block.substring(rows.get(r)[1], rowEnd).trim();
                CodeCell cell = leadingCodes(rowText);
                String description = rowText.substring(cell.consumed()).replaceAll("[;.\\s]+$", "").trim();
                String problem = cell.problem() == null ? pairingDoubt
                        : pairingDoubt == null ? cell.problem() : cell.problem() + " " + pairingDoubt;
                perSection.get(clause.section()).add(new Change(Operation.INSERT, schedule, total,
                        serials.get(r), cell.codes(), description, prefix + serials.get(r) + ". " + rowText,
                        problem != null, problem));
            }
        }

        for (int i = 0; i < clauses.size(); i++) {
            if (used[i]) continue;
            Clause clause = clauses.get(i);
            perSection.get(clause.section()).add(unread(heads.get(clause.section())[0], null, clause.text(),
                    "Entries are inserted here, but the table of new entries was not found in the text."));
        }
    }

    /** Which schedule heading a position falls under, or -1 when it is before the first. */
    private static int sectionOf(List<int[]> marks, int position) {
        int section = -1;
        for (int i = 0; i < marks.size(); i++) if (marks.get(i)[0] <= position) section = i;
        return section;
    }

    private void readSection(String schedule, double totalRate, String body, StringBuilder leftover,
                             int from, int to, List<Change> out) {
        String label = "Schedule " + schedule;
        String section = body.substring(from, to);

        if (OMIT_SCHEDULE.matcher(section).find()) {
            out.add(new Change(Operation.OMIT, schedule, totalRate, null, List.of(), null,
                    (label + section).trim(), true,
                    "The whole of " + label + " (" + trim(totalRate) + "%) is omitted. The notification "
                            + "does not list what was in it, so every heading currently at that "
                            + "rate has to be found and moved by hand."));
            return;
        }

        Matcher sub = SUBSTITUTE.matcher(section);
        while (sub.find()) {
            blank(leftover, from + sub.start(), from + sub.end());
            String serial = sub.group(1);
            String column = sub.group(2);
            String entry = sub.group(3).trim();
            String clause = label + ", " + sub.group().trim();
            if (!"2".equals(column)) {
                out.add(new Change(Operation.UNREAD, schedule, totalRate, serial, List.of(), entry, clause,
                        true, "This rewords column (" + column + ") of S. No. " + serial + ", not its "
                        + "tariff codes. It may narrow or widen what the entry covers without "
                        + "changing a code, which only a person can judge."));
                continue;
            }
            CodeCell cell = codes(entry);
            String note = "The codes previously listed against S. No. " + serial + " are not in the "
                    + "notification. Any that are no longer listed have left this rate and need "
                    + "checking against the principal notification.";
            out.add(new Change(Operation.SUBSTITUTE_CODES, schedule, totalRate, serial, cell.codes(), null,
                    clause, true, cell.problem() == null ? note : cell.problem() + " " + note));
        }

        Matcher omit = OMIT_ENTRIES.matcher(section);
        while (omit.find()) {
            blank(leftover, from + omit.start(), from + omit.end());
            out.add(new Change(Operation.OMIT, schedule, totalRate, omit.group(1).trim(), List.of(), null,
                    label + ", " + omit.group().trim(), true,
                    "S. No. " + omit.group(1).trim() + " is omitted from " + label + ". The codes it "
                            + "held are not in the notification - look them up in the principal "
                            + "notification and decide where they now fall."));
        }

        // Whatever no formula claimed. Clause numbering and connectives are expected to remain;
        // real words are not, and those are a clause this reader did not understand.
        String remaining = leftover.substring(from, to);
        String rest = remaining
                .replaceAll("\\(\\s*[a-z]{1,4}\\s*\\)", " ")
                .replaceAll("(?i)\\b(in|and|the|namely|said|notification)\\b", " ")
                .replaceAll("[^A-Za-z]+", " ").trim();
        if (rest.length() > 20) {
            out.add(unread(schedule, totalRate, label + ": " + remaining.replaceAll("\\s+", " ").trim(),
                    "Part of this schedule's amendment did not match any known wording."));
        }
    }

    private Change unread(String schedule, Double totalRate, String clause, String why) {
        return new Change(Operation.UNREAD, schedule, totalRate, null, List.of(), null,
                clause.length() > 1500 ? clause.substring(0, 1500) + " ..." : clause, true,
                why + " Read it in the notification itself.");
    }

    /** Codes found in a cell, with the reason they should not be trusted if there is one. */
    private record CodeCell(List<String> codes, String problem, int consumed) {}

    /** A cell that holds only codes: "2202 99 21, 2202 99 29". */
    private CodeCell codes(String cell) {
        Set<String> codes = new LinkedHashSet<>();
        List<String> problems = new ArrayList<>();
        if (cell.toLowerCase(Locale.ROOT).contains("other than")) {
            problems.add("The entry carves something out (\"other than ...\"), so the codes listed "
                    + "do not all take this rate.");
        }
        for (String part : cell.split("[,;]|\\band\\b")) {
            String digits = part.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) continue;
            if (digits.length() == 2 || digits.length() == 4 || digits.length() == 6 || digits.length() == 8) {
                codes.add(digits);
            } else {
                problems.add("\"" + part.trim() + "\" is not a 2, 4, 6 or 8 digit tariff code.");
            }
        }
        if (codes.isEmpty()) problems.add("No tariff code could be read from the entry.");
        // A wrapped cell splits one code across two lines, and the first half reads as a valid
        // shorter heading. Codes of different lengths in one cell are the sign of that.
        if (codes.stream().map(String::length).distinct().count() > 1) {
            problems.add("The codes in this entry are of different lengths, which usually means "
                    + "one was split across two lines of the PDF and is incomplete here.");
        }
        return new CodeCell(List.copyOf(codes), problems.isEmpty() ? null : String.join(" ", problems), 0);
    }

    /** The codes at the start of a table row, and how much of the row they take up. */
    private CodeCell leadingCodes(String row) {
        Matcher m = LEADING_CODES.matcher(row);
        if (!m.find()) {
            return new CodeCell(List.of(), "No tariff code could be read at the start of this row.", 0);
        }
        int consumed = m.end();
        CodeCell cell = codes(m.group());
        String rest = row.substring(consumed).trim();
        String problem = cell.problem();
        if (rest.toLowerCase(Locale.ROOT).startsWith("(other than")) {
            problem = (problem == null ? "" : problem + " ")
                    + "The entry carves something out (\"other than ...\"), so not everything under "
                    + "these codes takes this rate.";
        } else if (LOOSE_DIGITS.matcher(rest).find()) {
            problem = (problem == null ? "" : problem + " ")
                    + "Loose digits appear later in this row, which usually means a code wrapped "
                    + "onto a second line and is incomplete as read.";
        }
        return new CodeCell(cell.codes(), problem, consumed);
    }

    private static void blank(StringBuilder sb, int from, int to) {
        for (int i = from; i < to; i++) sb.setCharAt(i, ' ');
    }

    /** Where the numbered paragraph holding the coming-into-force sentence begins. */
    private static int paragraphStart(String text, int forceAt) {
        Matcher m = FORCE_PARAGRAPH.matcher(text);
        int at = forceAt;
        while (m.find() && m.start() <= forceAt) at = m.start();
        return at;
    }

    private static final Map<String, Month> MONTHS = Map.ofEntries(
            Map.entry("january", Month.JANUARY), Map.entry("february", Month.FEBRUARY),
            Map.entry("march", Month.MARCH), Map.entry("april", Month.APRIL),
            Map.entry("may", Month.MAY), Map.entry("june", Month.JUNE),
            Map.entry("july", Month.JULY), Map.entry("august", Month.AUGUST),
            Map.entry("september", Month.SEPTEMBER), Map.entry("october", Month.OCTOBER),
            Map.entry("november", Month.NOVEMBER), Map.entry("december", Month.DECEMBER));

    private static LocalDate date(String day, String month, String year) {
        Month m = MONTHS.get(month.toLowerCase(Locale.ROOT));
        if (m == null) return null;
        try {
            return LocalDate.of(Integer.parseInt(year), m, Integer.parseInt(day));
        } catch (Exception e) {
            return null;
        }
    }

    private static String capitalise(String words) {
        StringBuilder out = new StringBuilder();
        for (String w : words.trim().split("\\s+")) {
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private static String trim(double rate) {
        return rate == Math.rint(rate) ? String.valueOf((long) rate) : String.valueOf(rate);
    }
}
