package com.cauverystore.controller;

import com.cauverystore.dto.ChatResponse;
import com.cauverystore.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest http) {
        String message = body != null && body.get("message") != null
                ? String.valueOf(body.get("message"))
                : "";
        return ResponseEntity.ok(chatService.respond(authHeader, message, history(body), http.getRemoteAddr(), shownProducts(body)));
    }

    /** Products in the chat's last list, in order: {"context": {"productIds": [12, 7, ...]}}. */
    private static List<Long> shownProducts(Map<String, Object> body) {
        List<Long> ids = new ArrayList<>();
        if (body == null || !(body.get("context") instanceof Map<?, ?> ctx) || !(ctx.get("productIds") instanceof List<?> raw)) {
            return ids;
        }
        for (Object o : raw) {
            if (ids.size() >= 10) break;
            try {
                ids.add(Long.valueOf(String.valueOf(o)));
            } catch (NumberFormatException ignored) {
                // not an id
            }
        }
        return ids;
    }

    /** Earlier turns sent by the chat window: [{"role": "user"|"assistant", "text": "..."}]. */
    private static List<Map<String, String>> history(Map<String, Object> body) {
        List<Map<String, String>> turns = new ArrayList<>();
        if (body == null || !(body.get("history") instanceof List<?> raw)) return turns;
        for (Object o : raw) {
            if (o instanceof Map<?, ?> m && m.get("text") != null) {
                turns.add(Map.of("role", String.valueOf(m.get("role")), "text", String.valueOf(m.get("text"))));
            }
        }
        return turns;
    }

    @PostMapping("/action")
    public ResponseEntity<ChatResponse> action(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Object action = body != null ? body.get("action") : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = action instanceof Map ? (Map<String, Object>) action : body;
        return ResponseEntity.ok(chatService.performAction(authHeader, payload));
    }
}
