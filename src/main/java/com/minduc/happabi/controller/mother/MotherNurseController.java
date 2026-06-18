package com.minduc.happabi.controller.mother;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.mother.NurseAiComparisonRequest;
import com.minduc.happabi.dto.response.mother.NurseAiComparisonResponse;
import com.minduc.happabi.dto.response.nurse.NursePublicProfileResponse;
import com.minduc.happabi.enums.NurseSpecialty;
import com.minduc.happabi.service.mother.IMotherNurseComparisonService;
import com.minduc.happabi.service.mother.IMotherNurseProfileService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mothers/nurses")
@RequiredArgsConstructor
@Tag(name = "Mother Nurses", description = "Public nurse profiles for mother booking flow")
@SecurityRequirement(name = "bearerAuth")
public class MotherNurseController {

    private final IMotherNurseProfileService motherNurseProfileService;
    private final IMotherNurseComparisonService motherNurseComparisonService;

    @GetMapping
    @PreAuthorize("hasRole('MOTHER')")
    public ResponseEntity<BaseResponse<Page<NursePublicProfileResponse>>> searchActiveNurses(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) NurseSpecialty specialty,
            @RequestParam(required = false) LocalDate availableDate,
            @RequestParam(defaultValue = "false") Boolean availableOnly,
            @PageableDefault(size = 12, sort = "ratingAvg", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<NursePublicProfileResponse> response = motherNurseProfileService.searchActiveNurses(
                keyword, city, specialty, availableDate, availableOnly, pageable);
        return ResponseEntity.ok(BaseResponse.ok(response));
    }

    @GetMapping("/{profileId}")
    @PreAuthorize("hasRole('MOTHER')")
    public ResponseEntity<BaseResponse<NursePublicProfileResponse>> getActiveNurse(
            @PathVariable UUID profileId) {
        return ResponseEntity.ok(BaseResponse.ok(motherNurseProfileService.getActiveNurse(profileId)));
    }

    @PostMapping("/ai-comparison")
    @PreAuthorize("hasRole('MOTHER')")
    public ResponseEntity<BaseResponse<NurseAiComparisonResponse>> compareNursesWithAi(
            @Valid @RequestBody NurseAiComparisonRequest request) {
        return ResponseEntity.ok(BaseResponse.ok(motherNurseComparisonService.compareNurses(request)));
    }
}
