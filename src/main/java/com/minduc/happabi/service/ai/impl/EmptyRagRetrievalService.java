package com.minduc.happabi.service.ai.impl;

import com.minduc.happabi.service.ai.ChatIntent;
import com.minduc.happabi.service.ai.RagDocument;
import com.minduc.happabi.service.ai.RagRetrievalService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmptyRagRetrievalService implements RagRetrievalService {

    @Override
    public List<RagDocument> retrieve(String query, ChatIntent intent, int topK) {
        return List.of();
    }
}
