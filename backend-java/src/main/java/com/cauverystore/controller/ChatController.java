package com.cauverystore.controller;

import com.cauverystore.dto.ChatResponse;
import com.cauverystore.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String message = body != null && body.get("message") != null
                ? String.valueOf(body.get("message"))
                : "";
        return ResponseEntity.ok(chatService.respond(authHeader, message));
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
