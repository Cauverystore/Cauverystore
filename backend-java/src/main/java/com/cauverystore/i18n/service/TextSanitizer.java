package com.cauverystore.i18n.service;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * Every translated text passes through here before it is saved (typed by an admin, imported from CSV,
 * or - in later phases - produced by a machine). Names and meta fields: all HTML removed.
 * Descriptions: only a few harmless formatting tags are kept.
 */
@Component
public class TextSanitizer {

    private static final PolicyFactory PLAIN = new HtmlPolicyBuilder().toFactory();
    private static final PolicyFactory RICH = new HtmlPolicyBuilder()
            .allowElements("p", "br", "b", "strong", "i", "em", "ul", "ol", "li")
            .toFactory();

    public String plain(String input) {
        if (input == null) return null;
        String s = PLAIN.sanitize(input)
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&#34;", "\"").replace("&#39;", "'").replace("&#64;", "@");
        s = s.strip();
        return s.isEmpty() ? null : s;
    }

    public String rich(String input) {
        if (input == null) return null;
        String s = RICH.sanitize(input).strip();
        return s.isEmpty() ? null : s;
    }
}
