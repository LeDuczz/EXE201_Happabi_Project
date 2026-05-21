package com.minduc.happabi.controller.ai;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.ai.UpsertKnowledgeChunkRequest;
import com.minduc.happabi.dto.response.ai.KnowledgeChunkResponse;
import com.minduc.happabi.service.ai.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-chat/knowledge-chunks")
@RequiredArgsConstructor
@Tag(name = "AI Knowledge", description = "Verified knowledge chunks for AI chatbot RAG")
@SecurityRequirement(name = "bearerAuth")
public class AiKnowledgeController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<KnowledgeChunkResponse>> upsertKnowledgeChunk(
            @Valid @RequestBody UpsertKnowledgeChunkRequest request) {
        KnowledgeChunkResponse response = knowledgeBaseService.upsertKnowledgeChunk(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.created(response));
    }
}
