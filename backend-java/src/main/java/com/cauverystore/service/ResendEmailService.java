package com.cauverystore.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Service
public class ResendEmailService {

    @Value("${resend.api.key}")
    private String apiKey;

    @Value("${resend.from.email}")
    private String fromEmail;

    public void sendHtml(String to, String subject, String html) {
        if (apiKey == null || apiKey.equals("re_placeholder")) {
            System.out.println("[Email] To: " + to + " | Subject: " + subject);
            return;
        }

        try {
            String payload = String.format(
                    "{\"from\":\"%s\",\"to\":\"%s\",\"subject\":\"%s\",\"html\":\"%s\"}",
                    escapeJson(fromEmail), escapeJson(to), escapeJson(subject), escapeJson(html));

            URL url = new URL("https://api.resend.com/emails");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception e) {
            System.err.println("[Email] Failed to send: " + e.getMessage());
        }
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
