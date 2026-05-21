package com.minduc.happabi.service.ai;

import java.util.List;

public interface EmbeddingClient {

    List<Double> embed(String text);
}
