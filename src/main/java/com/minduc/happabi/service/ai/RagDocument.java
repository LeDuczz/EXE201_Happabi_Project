package com.minduc.happabi.service.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagDocument {

    private String title;

    private String content;

    private String source;

    private Double score;
}
