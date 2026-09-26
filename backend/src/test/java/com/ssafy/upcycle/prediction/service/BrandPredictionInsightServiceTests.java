package com.ssafy.upcycle.prediction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.prediction.PredictionErrorCode;
import com.ssafy.upcycle.prediction.dto.BrandPredictionInsightResponse;
import com.ssafy.upcycle.prediction.entity.PredictionBucket;
import com.ssafy.upcycle.prediction.entity.PredictionMarket;
import com.ssafy.upcycle.prediction.entity.PredictionReasonCode;
import com.ssafy.upcycle.prediction.entity.PredictionResponse;
import com.ssafy.upcycle.prediction.repository.PredictionBucketRepository;
import com.ssafy.upcycle.prediction.repository.PredictionMarketRepository;
import com.ssafy.upcycle.prediction.repository.PredictionResponseRepository;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.geolocation.GeoCoordinates;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BrandPredictionInsightServiceTests {
    private static final Long BRAND_ID = 7L;
    private static final Long MARKET_ID = 31L;

    @Mock PredictionMarketRepository markets;
    @Mock PredictionBucketRepository buckets;
    @Mock PredictionResponseRepository responses;
    @Mock UserRepository users;
    @Mock CompanyProfileRepository companyProfiles;
    @InjectMocks BrandPredictionInsightService service;

    private void mockApprovedBrand() {
        User brand = User.register("brand@test.com", "encoded", "대표", "01012345678", UserRole.BRAND);
        CompanyProfile profile = CompanyProfile.register(
                BRAND_ID, "테스트 브랜드", "123-45-67890", "12345", "서울",
                null, new GeoCoordinates(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127)));
        profile.approveRole("0x1111111111111111111111111111111111111111", "[]", 1L, LocalDateTime.now());
        when(users.findById(BRAND_ID)).thenReturn(Optional.of(brand));
        when(companyProfiles.findById(BRAND_ID)).thenReturn(Optional.of(profile));
    }

    @Test
    void aggregatesBucketsReasonsAndAnonymousCommentsForOwnedMarkets() {
        mockApprovedBrand();
        PredictionMarket market = market();
        PredictionResponse first = response(0, "10", PredictionReasonCode.DESIGN, "색감이 좋아요");
        PredictionResponse second = response(1, "25", PredictionReasonCode.PRICE, null);
        ReflectionTestUtils.setField(first, "createdAt", LocalDateTime.of(2026, 9, 20, 10, 0));

        when(markets.findAllByBrandIdOrderByCreatedAtDesc(BRAND_ID)).thenReturn(List.of(market));
        when(buckets.findAllByMarketIdOrderByBucketIndex(MARKET_ID)).thenReturn(List.of(
                PredictionBucket.of(MARKET_ID, 0, 0, 49),
                PredictionBucket.of(MARKET_ID, 1, 50, 100)));
        when(responses.findAllByMarketIdOrderByCreatedAtAsc(MARKET_ID)).thenReturn(List.of(first, second));

        BrandPredictionInsightResponse result = service.findMine(BRAND_ID).getFirst();

        assertThat(result.participantCount()).isEqualTo(2);
        assertThat(result.totalStakeAmount()).isEqualTo("35");
        assertThat(result.buckets()).extracting(value -> value.responseCount()).containsExactly(1L, 1L);
        assertThat(result.buckets()).extracting(value -> value.stakeAmount()).containsExactly("10", "25");
        assertThat(result.reasons()).filteredOn(value -> value.responseCount() > 0)
                .extracting(value -> value.reasonCode()).containsExactly("DESIGN", "PRICE");
        assertThat(result.comments()).hasSize(1);
        assertThat(result.comments().getFirst().reasonText()).isEqualTo("색감이 좋아요");
    }

    @Test
    void rejectsNonBrandBeforeReadingMarketData() {
        User consumer = User.register("consumer@test.com", "encoded", "소비자", "01012345678", UserRole.CONSUMER);
        when(users.findById(BRAND_ID)).thenReturn(Optional.of(consumer));

        assertThatThrownBy(() -> service.findMine(BRAND_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).getErrorCode())
                .isEqualTo(PredictionErrorCode.BRAND_INSIGHT_FORBIDDEN);
    }

    private PredictionMarket market() {
        PredictionMarket market = PredictionMarket.create(
                9L, 11L, 12L, 13L, "업사이클 재킷", null, BRAND_ID,
                "0x1111111111111111111111111111111111111111", 120_000L, 100,
                LocalDateTime.of(2026, 9, 21, 0, 0),
                LocalDateTime.of(2026, 9, 22, 0, 0),
                LocalDateTime.of(2026, 9, 30, 0, 0), "0xcreate");
        ReflectionTestUtils.setField(market, "id", MARKET_ID);
        return market;
    }

    private PredictionResponse response(
            int bucket, String stake, PredictionReasonCode reason, String text) {
        return PredictionResponse.of(
                MARKET_ID, 100L + bucket,
                bucket == 0
                        ? "0x2222222222222222222222222222222222222222"
                        : "0x3333333333333333333333333333333333333333",
                bucket, stake, reason, text, bucket == 0 ? "0xbet1" : "0xbet2");
    }
}
