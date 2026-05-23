package com.minduc.happabi.service.ai;

import com.minduc.happabi.dto.document.RagDocument;

import java.util.List;

public interface RagRetrievalService {

    List<RagDocument> retrieve(String query, ChatIntent intent, int topK);
}
