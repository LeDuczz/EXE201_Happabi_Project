package com.minduc.happabi.service.ai;

import java.util.List;

public interface RagRetrievalService {

    List<RagDocument> retrieve(String query, ChatIntent intent, int topK);
}
