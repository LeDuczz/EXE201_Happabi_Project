package com.minduc.happabi.service.ai;

import com.minduc.happabi.dto.request.ai.UpsertKnowledgeChunkRequest;
import com.minduc.happabi.dto.response.ai.KnowledgeChunkResponse;

public interface KnowledgeBaseService {

    KnowledgeChunkResponse upsertKnowledgeChunk(UpsertKnowledgeChunkRequest request);
}
