package com.minduc.happabi.service.ai;

import java.util.List;

public interface IRagRetrievalService {

    List<RagDocument> retrieve(String query, ChatIntent intent, int topK);
}
