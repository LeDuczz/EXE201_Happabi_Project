package com.minduc.happabi.controller.ai;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.ai.CreateConversationRequest;
import com.minduc.happabi.dto.request.ai.SendAiMessageRequest;
import com.minduc.happabi.dto.response.ai.AiChatResponse;
import com.minduc.happabi.dto.response.ai.AiMessageResponse;
import com.minduc.happabi.dto.response.ai.ConversationResponse;
import com.minduc.happabi.service.ai.AiChatService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai-chat")
@RequiredArgsConstructor
@Tag(name = "AI Chat", description = "AI chatbot conversations and messages")
@SecurityRequirement(name = "bearerAuth")
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<ConversationResponse>> createConversation(
            @Valid @RequestBody(required = false) CreateConversationRequest request) {
        ConversationResponse response = aiChatService.createConversation(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.created(response));
    }

    @GetMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<List<ConversationResponse>>> getConversations() {
        return ResponseEntity.ok(BaseResponse.ok(aiChatService.getCurrentUserConversations()));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<List<AiMessageResponse>>> getMessages(
            @PathVariable UUID conversationId) {
        return ResponseEntity.ok(BaseResponse.ok(aiChatService.getMessages(conversationId)));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<AiChatResponse>> sendMessage(
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendAiMessageRequest request) {
        return ResponseEntity.ok(BaseResponse.ok(aiChatService.sendMessage(conversationId, request)));
    }
}
