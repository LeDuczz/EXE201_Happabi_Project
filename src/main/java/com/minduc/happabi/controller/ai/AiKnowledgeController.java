package com.minduc.happabi.controller.ai;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.common.utils.AuthUtils;
import com.minduc.happabi.dto.request.ai.ReviewKnowledgeItemRequest;
import com.minduc.happabi.dto.request.ai.UpsertKnowledgeChunkRequest;
import com.minduc.happabi.dto.response.ai.KnowledgeChunkResponse;
import com.minduc.happabi.dto.response.ai.KnowledgeItemResponse;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.repository.UserIdentityProviderRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.ai.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai-chat/knowledge-chunks")
@RequiredArgsConstructor
@Tag(name = "AI Knowledge", description = "Verified knowledge chunks for AI chatbot RAG")
@SecurityRequirement(name = "bearerAuth")
public class AiKnowledgeController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final UserRepository userRepository;
    private final UserIdentityProviderRepository identityProviderRepository;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<KnowledgeChunkResponse>> upsertKnowledgeChunk(
            @Valid @RequestBody UpsertKnowledgeChunkRequest request) {
        KnowledgeChunkResponse response = knowledgeBaseService.upsertKnowledgeChunk(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.created(response));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<List<KnowledgeItemResponse>>> getPendingKnowledgeItems() {
        return ResponseEntity.ok(BaseResponse.ok(knowledgeBaseService.getPendingReviewItems()));
    }

    @PatchMapping("/{knowledgeItemId}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<KnowledgeItemResponse>> reviewKnowledgeItem(
            @PathVariable UUID knowledgeItemId,
            @Valid @RequestBody ReviewKnowledgeItemRequest request) {
        KnowledgeItemResponse response = knowledgeBaseService.reviewKnowledgeItem(
                knowledgeItemId,
                getCurrentUser().getId(),
                Boolean.TRUE.equals(request.getApproved())
        );
        return ResponseEntity.ok(BaseResponse.ok(response));
    }

    @PostMapping("/{knowledgeItemId}/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<KnowledgeItemResponse>> reindexKnowledgeItem(
            @PathVariable UUID knowledgeItemId) {
        return ResponseEntity.ok(BaseResponse.ok(knowledgeBaseService.reindexKnowledgeItem(knowledgeItemId)));
    }

    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<List<KnowledgeItemResponse>>> reindexVerifiedKnowledgeItems() {
        return ResponseEntity.ok(BaseResponse.ok(knowledgeBaseService.reindexVerifiedKnowledgeItems()));
    }

    private User getCurrentUser() {
        String cognitoSub = AuthUtils.getCurrentSub()
                .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_FAILED));

        return userRepository.findByCognitoSub(cognitoSub)
                .or(() -> identityProviderRepository.findUserByProviderUidWithRolesAndProviders(cognitoSub))
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));
    }
}
