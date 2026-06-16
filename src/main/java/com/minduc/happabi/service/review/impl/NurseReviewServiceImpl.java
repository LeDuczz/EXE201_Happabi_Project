package com.minduc.happabi.service.review.impl;

import com.minduc.happabi.dto.request.review.CreateNurseReviewRequest;
import com.minduc.happabi.dto.response.review.NurseReviewResponse;
import com.minduc.happabi.entity.NurseReview;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.entity.WorkSession;
import com.minduc.happabi.enums.NurseReviewTag;
import com.minduc.happabi.enums.WorkSessionStatus;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.NurseReviewErrorCode;
import com.minduc.happabi.exception.code.WorkSessionErrorCode;
import com.minduc.happabi.integration.sqs.INurseReviewAggregationPublisher;
import com.minduc.happabi.mapper.NurseReviewMapper;
import com.minduc.happabi.repository.NurseReviewRepository;
import com.minduc.happabi.repository.WorkSessionRepository;
import com.minduc.happabi.service.review.INurseReviewService;
import com.minduc.happabi.service.user.UserAccountLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NurseReviewServiceImpl implements INurseReviewService {

    private static final Set<WorkSessionStatus> REVIEWABLE_STATUSES = Set.of(
            WorkSessionStatus.COMPLETED,
            WorkSessionStatus.AUTO_CONFIRMED
    );

    private final WorkSessionRepository workSessionRepository;
    private final NurseReviewRepository nurseReviewRepository;
    private final UserAccountLookupService userAccountLookupService;
    private final NurseReviewMapper nurseReviewMapper;
    private final INurseReviewAggregationPublisher aggregationPublisher;

    @Override
    @Transactional
    @PreAuthorize("hasRole('MOTHER')")
    public NurseReviewResponse createMyReview(UUID workSessionId, CreateNurseReviewRequest request) {
        if (request == null || request.getRating() == null) {
            throw new AppException(NurseReviewErrorCode.NURSE_REVIEW_RATING_REQUIRED);
        }

        User mother = userAccountLookupService.getCurrentUser();
        WorkSession session = workSessionRepository.findByIdForUpdate(workSessionId)
                .orElseThrow(() -> new AppException(WorkSessionErrorCode.WORK_SESSION_NOT_FOUND));
        ensureMotherOwns(session, mother);
        ensureReviewable(session);

        if (nurseReviewRepository.existsByWorkSession_Id(session.getId())) {
            throw new AppException(NurseReviewErrorCode.NURSE_REVIEW_ALREADY_EXISTS);
        }

        NurseReview review = NurseReview.builder()
                .workSession(session)
                .nurseProfile(session.getNurseProfile())
                .mother(mother)
                .rating(request.getRating())
                .comment(normalize(request.getComment()))
                .tags(normalizeTags(request.getTags()))
                .build();

        try {
            NurseReview saved = nurseReviewRepository.saveAndFlush(review);
            publishAfterCommit(saved.getNurseProfile().getId(), saved.getId());
            return nurseReviewMapper.toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(NurseReviewErrorCode.NURSE_REVIEW_ALREADY_EXISTS, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MOTHER')")
    public NurseReviewResponse getMyReview(UUID workSessionId) {
        User mother = userAccountLookupService.getCurrentUser();
        return nurseReviewRepository.findByWorkSession_IdAndMother_Id(workSessionId, mother.getId())
                .map(nurseReviewMapper::toResponse)
                .orElseThrow(() -> new AppException(NurseReviewErrorCode.NURSE_REVIEW_NOT_FOUND));
    }

    private void ensureMotherOwns(WorkSession session, User mother) {
        if (!session.getMother().getId().equals(mother.getId())) {
            throw new AppException(WorkSessionErrorCode.WORK_SESSION_ACCESS_DENIED);
        }
    }

    private void ensureReviewable(WorkSession session) {
        if (!REVIEWABLE_STATUSES.contains(session.getStatus())) {
            throw new AppException(NurseReviewErrorCode.NURSE_REVIEW_INVALID_SESSION_STATE,
                    "Current status is " + session.getStatus());
        }
    }

    private List<NurseReviewTag> normalizeTags(List<NurseReviewTag> tags) {
        if (tags == null || tags.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(new LinkedHashSet<>(tags));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void publishAfterCommit(UUID nurseProfileId, UUID reviewId) {
        Runnable publish = () -> aggregationPublisher.publishRecalculate(
                nurseProfileId, reviewId, "MOTHER_REVIEW_CREATED");
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish.run();
            }
        });
    }
}
