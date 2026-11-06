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
        return ResponseEntity.ok(chatService.respond(authHeader, message, history(body), http.getRemoteAddr()));
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
