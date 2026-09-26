package com.ssafy.upcycle.prediction.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.prediction.PredictionErrorCode;
import com.ssafy.upcycle.prediction.dto.BrandPredictionBucketInsightResponse;
import com.ssafy.upcycle.prediction.dto.BrandPredictionCommentResponse;
import com.ssafy.upcycle.prediction.dto.BrandPredictionInsightResponse;
import com.ssafy.upcycle.prediction.dto.BrandPredictionReasonInsightResponse;
import com.ssafy.upcycle.prediction.dto.PredictionBucketResponse;
import com.ssafy.upcycle.prediction.dto.PredictionMarketResponse;
import com.ssafy.upcycle.prediction.entity.PredictionBucket;
import com.ssafy.upcycle.prediction.entity.PredictionMarket;
import com.ssafy.upcycle.prediction.entity.PredictionReasonCode;
import com.ssafy.upcycle.prediction.entity.PredictionResponse;
import com.ssafy.upcycle.prediction.repository.PredictionBucketRepository;
import com.ssafy.upcycle.prediction.repository.PredictionMarketRepository;
import com.ssafy.upcycle.prediction.repository.PredictionResponseRepository;
import com.ssafy.upcycle.user.entity.CompanyRoleApprovalStatus;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.math.BigInteger;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BrandPredictionInsightService {
    private final PredictionMarketRepository markets;
    private final PredictionBucketRepository buckets;
    private final PredictionResponseRepository responses;
    private final UserRepository users;
    private final CompanyProfileRepository companyProfiles;

    /**
     * 로그인한 브랜드가 소유한 시장만 집계한다.
     *
     * <p>역할 클레임만 믿지 않고 현재 계정·승인 상태를 다시 읽는다. 토큰 발급 뒤 계정이
     * 정지되거나 승인이 취소되어도 소비자 의견을 계속 읽을 수 있어서는 안 된다.
     */
    @Transactional(readOnly = true)
    public List<BrandPredictionInsightResponse> findMine(Long brandId) {
        requireApprovedBrand(brandId);
        return markets.findAllByBrandIdOrderByCreatedAtDesc(brandId).stream()
                .map(this::aggregate)
                .toList();
    }

    private BrandPredictionInsightResponse aggregate(PredictionMarket market) {
        List<PredictionBucket> marketBuckets =
                buckets.findAllByMarketIdOrderByBucketIndex(market.getId());
        List<PredictionResponse> marketResponses =
                responses.findAllByMarketIdOrderByCreatedAtAsc(market.getId());

        Map<Integer, BucketTotal> bucketTotals = new java.util.HashMap<>();
        Map<PredictionReasonCode, Long> reasonTotals = new EnumMap<>(PredictionReasonCode.class);
        BigInteger totalStake = BigInteger.ZERO;

        for (PredictionResponse response : marketResponses) {
            BigInteger stake = new BigInteger(response.getStakeAmount());
            bucketTotals.computeIfAbsent(response.getBucketIndex(), ignored -> new BucketTotal())
                    .add(stake);
            reasonTotals.merge(response.getReasonCode(), 1L, Long::sum);
            totalStake = totalStake.add(stake);
        }

        List<BrandPredictionBucketInsightResponse> bucketInsights = marketBuckets.stream()
                .map(bucket -> {
                    BucketTotal total = bucketTotals.getOrDefault(bucket.getBucketIndex(), new BucketTotal());
                    return new BrandPredictionBucketInsightResponse(
                            bucket.getBucketIndex(),
                            bucket.getLowerBound(),
                            bucket.getUpperBound(),
                            total.responseCount,
                            total.stakeAmount.toString());
                })
                .toList();

        List<BrandPredictionReasonInsightResponse> reasonInsights =
                java.util.Arrays.stream(PredictionReasonCode.values())
                        .map(reason -> new BrandPredictionReasonInsightResponse(
                                reason.name(), reasonTotals.getOrDefault(reason, 0L)))
                        .toList();

        List<BrandPredictionCommentResponse> comments = marketResponses.stream()
                .filter(response -> response.getReasonText() != null)
                .map(response -> new BrandPredictionCommentResponse(
                        response.getReasonCode().name(),
                        response.getReasonText(),
                        response.getCreatedAt()))
                .toList();

        List<PredictionBucketResponse> publicBuckets = marketBuckets.stream()
                .map(bucket -> new PredictionBucketResponse(
                        bucket.getBucketIndex(), bucket.getLowerBound(), bucket.getUpperBound()))
                .toList();

        return new BrandPredictionInsightResponse(
                PredictionMarketResponse.from(market, publicBuckets),
                marketResponses.size(),
                totalStake.toString(),
                bucketInsights,
                reasonInsights,
                comments);
    }

    private void requireApprovedBrand(Long brandId) {
        boolean activeBrand = users.findById(brandId)
                .filter(user -> user.isActive() && user.getRole() == UserRole.BRAND)
                .isPresent();
        boolean approved = companyProfiles.findById(brandId)
                .filter(profile -> profile.getRoleApprovalStatus() == CompanyRoleApprovalStatus.APPROVED)
                .isPresent();
        if (!activeBrand || !approved) {
            throw new BusinessException(PredictionErrorCode.BRAND_INSIGHT_FORBIDDEN);
        }
    }

    private static final class BucketTotal {
        private long responseCount;
        private BigInteger stakeAmount = BigInteger.ZERO;

        private void add(BigInteger stake) {
            responseCount++;
            stakeAmount = stakeAmount.add(stake);
        }
    }
}
