package com.minduc.happabi.service.review.impl;

import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.NurseReviewRepository;
import com.minduc.happabi.service.review.INurseRatingAggregationService;
import com.minduc.happabi.service.user.UserCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NurseRatingAggregationServiceImpl implements INurseRatingAggregationService {

    private final NurseProfileRepository nurseProfileRepository;
    private final NurseReviewRepository nurseReviewRepository;
    private final UserCacheService userCacheService;

    @Override
    @Transactional
    @LogExecution
    @TimedAction("RECALCULATE_NURSE_RATING")
    public void recalculate(UUID nurseProfileId) {
        NurseProfile nurseProfile = nurseProfileRepository.findById(nurseProfileId)
                .orElseThrow(() -> new AppException(UserErrorCode.NURSE_PROFILE_NOT_FOUND));

        NurseReviewRepository.NurseRatingAggregate aggregate =
                nurseReviewRepository.calculateAggregate(nurseProfileId);

        int totalReviews = aggregate == null || aggregate.getTotalReviews() == null
                ? 0
                : Math.toIntExact(aggregate.getTotalReviews());
        BigDecimal ratingAvg = aggregate == null || aggregate.getAverageRating() == null
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(aggregate.getAverageRating()).setScale(2, RoundingMode.HALF_UP);

        nurseProfile.setRatingAvg(ratingAvg);
        nurseProfile.setTotalReviews(totalReviews);
        nurseProfileRepository.save(nurseProfile);

        if (nurseProfile.getUser() != null && nurseProfile.getUser().getCognitoSub() != null) {
            userCacheService.evictProfiles(nurseProfile.getUser().getCognitoSub());
        }
        log.info("[NurseReview] Recalculated rating: nurseProfileId={} ratingAvg={} totalReviews={}",
                nurseProfileId, ratingAvg, totalReviews);
    }
}
