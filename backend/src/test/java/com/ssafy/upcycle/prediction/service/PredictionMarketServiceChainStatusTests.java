package com.ssafy.upcycle.prediction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.blockchain.service.ChainProductionStatusReader;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.prediction.PredictionErrorCode;
import com.ssafy.upcycle.prediction.dto.PredictionMarketCreateRequest;
import com.ssafy.upcycle.prediction.dto.PredictionMarketEligibleBatchResponse;
import com.ssafy.upcycle.prediction.repository.PredictionMarketRepository;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.production.entity.ProductionBatch;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.CompanyRoleApprovalStatus;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 예측시장은 생산이 <b>체인에서도</b> 확정된 배치에만 열 수 있다.
 *
 * <p>생산 기록은 서버 확정 뒤 서명 네 번으로 이어진다. 중간에 멈추면 DB 는 CONFIRMED 인데
 * 체인은 DRAFT 로 남는다 — 그 배치를 목록에 올리면 관리자가 골라도 컨트랙트가
 * {@code batch is not confirmed} 로 되돌린다(운영 생산 #20 / 체인 배치 #35).
 */
@ExtendWith(MockitoExtension.class)
class PredictionMarketServiceChainStatusTests {
    private static final long PRODUCER_ID = 7L;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock PredictionMarketRepository markets;
    @Mock ProductionBatchRepository productionBatches;
    @Mock ProductModelRepository productModels;
    @Mock CompanyProfileRepository companyProfiles;
    @Mock PredictionChainGateway chain;
    @Mock ChainProductionStatusReader chainBatches;
    @Spy Clock clock = Clock.fixed(Instant.parse("2026-09-28T00:00:00Z"), SEOUL);
    @InjectMocks PredictionMarketService service;

    @BeforeEach
    void approvedProducer() {
        CompanyProfile profile = mock(CompanyProfile.class);
        lenient().when(profile.getRoleApprovalStatus()).thenReturn(CompanyRoleApprovalStatus.APPROVED);
        lenient().when(profile.getCompanyName()).thenReturn("Recode");
        lenient().when(companyProfiles.findById(PRODUCER_ID)).thenReturn(Optional.of(profile));
        ProductModel model = mock(ProductModel.class);
        lenient().when(model.getId()).thenReturn(31L);
        lenient().when(model.getName()).thenReturn("Wallet");
        lenient().when(model.getPrice()).thenReturn(30_000L);
        lenient().when(productModels.findById(31L)).thenReturn(Optional.of(model));
    }

    @Test
    void eligibleBatchesHideBatchesThatAreNotConfirmedOnChain() {
        ProductionBatch stalled = batch(20L, 35L);
        ProductionBatch confirmed = batch(22L, 37L);
        ProductionBatch unreadable = batch(23L, 38L);
        when(productionBatches.findAllByStatusOrderByConfirmedAtDesc(ProductionBatchStatus.CONFIRMED))
                .thenReturn(List.of(stalled, confirmed, unreadable));
        when(chainBatches.isConfirmedOnChain(35L)).thenReturn(Optional.of(false));
        when(chainBatches.isConfirmedOnChain(37L)).thenReturn(Optional.of(true));
        // 체인을 읽지 못하면 고를 수 있다고 말하지 않는다 — 골라도 열릴지 알 수 없다.
        when(chainBatches.isConfirmedOnChain(38L)).thenReturn(Optional.empty());

        List<PredictionMarketEligibleBatchResponse> eligible = service.eligibleBatches();

        assertThat(eligible).extracting(PredictionMarketEligibleBatchResponse::chainBatchId)
                .containsExactly(37L);
    }

    @Test
    void createRejectsABatchThatIsNotConfirmedOnChainBeforeSendingATransaction() {
        ProductionBatch stalled = batch(20L, 35L);
        when(productionBatches.findById(20L)).thenReturn(Optional.of(stalled));
        when(chainBatches.isConfirmedOnChain(35L)).thenReturn(Optional.of(false));

        assertThatThrownBy(() -> service.create(request(20L)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(PredictionErrorCode.BATCH_NOT_CONFIRMED_ON_CHAIN);
        verify(chain, never()).createMarket(anyLong(), anyLong(), anyLong(), any(), any(), any(), any());
    }

    private static ProductionBatch batch(long id, long chainBatchId) {
        ProductionBatch batch = mock(ProductionBatch.class);
        lenient().when(batch.getId()).thenReturn(id);
        lenient().when(batch.getChainBatchId()).thenReturn(chainBatchId);
        lenient().when(batch.getProductModelId()).thenReturn(31L);
        lenient().when(batch.getProducerId()).thenReturn(PRODUCER_ID);
        lenient().when(batch.getProducedQuantity()).thenReturn(10);
        lenient().when(batch.isConfirmed()).thenReturn(true);
        return batch;
    }

    private static PredictionMarketCreateRequest request(long productionBatchId) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 28, 9, 0);
        return new PredictionMarketCreateRequest(productionBatchId, now.plusDays(1), now.plusDays(1),
                now.plusDays(8), List.of(3, 6, 10));
    }
}
