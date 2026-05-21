package com.minduc.happabi.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpsertKnowledgeChunkRequest {

    @NotBlank(message = "Title is required.")
    @Size(max = 240, message = "Title must be at most 240 characters.")
    private String title;

    @NotBlank(message = "Content is required.")
    @Size(max = 12000, message = "Content must be at most 12000 characters.")
    private String content;

    @Size(max = 120, message = "Source type must be at most 120 characters.")
    private String sourceType;

    @Size(max = 160, message = "Source id must be at most 160 characters.")
    private String sourceId;

    @Size(max = 80, message = "Language must be at most 80 characters.")
    private String language;

    private Boolean verified;
}
