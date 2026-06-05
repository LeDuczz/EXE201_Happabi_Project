package com.minduc.happabi.controller.mother;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.response.nurse.NursePublicProfileResponse;
import com.minduc.happabi.enums.NurseSpecialty;
import com.minduc.happabi.service.mother.IMotherNurseProfileService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mothers/nurses")
@RequiredArgsConstructor
@Tag(name = "Mother Nurses", description = "Public nurse profiles for mother booking flow")
@SecurityRequirement(name = "bearerAuth")
public class MotherNurseController {

    private final IMotherNurseProfileService motherNurseProfileService;

    @GetMapping
    @PreAuthorize("hasRole('MOTHER')")
    public ResponseEntity<BaseResponse<Page<NursePublicProfileResponse>>> searchActiveNurses(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) NurseSpecialty specialty,
            @RequestParam(defaultValue = "false") Boolean availableOnly,
            @PageableDefault(size = 12, sort = "ratingAvg", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<NursePublicProfileResponse> response = motherNurseProfileService.searchActiveNurses(
                keyword, city, specialty, availableOnly, pageable);
        return ResponseEntity.ok(BaseResponse.ok(response));
    }

    @GetMapping("/{profileId}")
    @PreAuthorize("hasRole('MOTHER')")
    public ResponseEntity<BaseResponse<NursePublicProfileResponse>> getActiveNurse(
            @PathVariable UUID profileId) {
        return ResponseEntity.ok(BaseResponse.ok(motherNurseProfileService.getActiveNurse(profileId)));
    }
}
