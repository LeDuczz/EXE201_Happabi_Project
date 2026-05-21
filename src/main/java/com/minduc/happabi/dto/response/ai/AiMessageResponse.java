package com.minduc.happabi.dto.response.ai;

import com.minduc.happabi.enums.ChatRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMessageResponse {

    private UUID id;

    private ChatRole role;

    private String content;

    private String modelUsed;

    private Integer inputTokens;

    private Integer outputTokens;

    private OffsetDateTime createdAt;
}
