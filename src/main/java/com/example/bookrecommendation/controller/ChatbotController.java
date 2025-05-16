package com.example.bookrecommendation.controller;

import com.example.bookrecommendation.model.ChatMessage;
import com.example.bookrecommendation.service.ChatbotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private final ChatbotService chatbotService;

    @Autowired
    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping("/chat")
    public ResponseEntity<List<ChatMessage>> chat(
            @RequestParam(required = false) String sessionId,
            @RequestParam String message,
            @RequestParam(required = false) String context) {
        
        // Generate a session ID if not provided
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }
        
        List<ChatMessage> response = chatbotService.chat(sessionId, message, context);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversation-starters")
    public ResponseEntity<List<String>> getConversationStarters(
            @RequestParam(required = false) String context) {
        
        List<String> starters = chatbotService.getConversationStarters(context);
        return ResponseEntity.ok(starters);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchBooks(
            @RequestParam(required = false) String theme,
            @RequestParam(required = false) String author) {
        
        List<Map<String, Object>> results = chatbotService.searchBooksByThemeAndAuthor(theme, author);
        return ResponseEntity.ok(results);
    }
}