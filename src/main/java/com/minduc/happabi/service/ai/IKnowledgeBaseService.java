package com.minduc.happabi.service.ai;

import com.minduc.happabi.dto.request.ai.UpsertKnowledgeChunkRequest;
import com.minduc.happabi.dto.response.ai.KnowledgeChunkResponse;
import com.minduc.happabi.dto.response.ai.KnowledgeItemResponse;
import com.minduc.happabi.enums.KnowledgeStatus;

import java.util.List;
import java.util.UUID;

public interface IKnowledgeBaseService {

    KnowledgeChunkResponse upsertKnowledgeChunk(UpsertKnowledgeChunkRequest request);

    KnowledgeItemResponse createPendingReview(UUID userId,
                                              UUID conversationId,
                                              String question,
                                              String answer,
                                              String context);

    List<KnowledgeItemResponse> getPendingReviewItems();

    List<KnowledgeItemResponse> getKnowledgeItems(KnowledgeStatus status);

    KnowledgeItemResponse reviewKnowledgeItem(UUID knowledgeItemId, UUID reviewerId, boolean approved);

    KnowledgeItemResponse reindexKnowledgeItem(UUID knowledgeItemId);

    List<KnowledgeItemResponse> reindexVerifiedKnowledgeItems();

}
