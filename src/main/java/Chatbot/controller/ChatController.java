package Chatbot.controller;

import Chatbot.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/chat")
    public ResponseEntity<?> chat(@RequestParam String message) {
        String response = chatService.getChatResponse(message);
        return ResponseEntity.ok(Map.of("response", response));
    }
}
