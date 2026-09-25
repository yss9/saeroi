package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.agent.project.AgentProject;
import com.ssafy.upcycle.agent.service.step.BrandStep;
import com.ssafy.upcycle.agent.service.step.BrandStepResolver;
import com.ssafy.upcycle.agent.service.step.ProductionChainProgress;
import com.ssafy.upcycle.blockchain.service.ChainOrderStatusReader;
import com.ssafy.upcycle.blockchain.service.ChainProcessingStatusReader;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.processing.service.ProcessingJobWorkflowService;
import com.ssafy.upcycle.product.dto.ProductModelResponse;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.service.ProductModelQueryService;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import com.ssafy.upcycle.trade.service.TradeQueryService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 생산을 확정한 뒤의 순서 — <b>블록체인 기록 네 번, 그다음 제품 정보, 그다음 판매.</b>
 *
 * <p>둘을 한꺼번에 담당자 차례로 적었더니 모델이 제품 등록을 먼저 골랐고, 판매가를 묻지 않아 0원으로
 * 등록했다. 곧이어 판매 시작 카드가 떠 0원에 판매가 시작됐다.
 */
class BrandStepProductOrderTests {

    private static final long BATCH = 10L;
    private static final long CHAIN_BATCH = 7L;
    private static final long PRODUCT = 8L;

    private ProductionBatchService batches;
    private ProductModelQueryService products;
    private ProductionChainProgress chainRecord;
    private BrandStepResolver resolver;

    @BeforeEach
    void setUp() {
        batches = mock(ProductionBatchService.class);
        products = mock(ProductModelQueryService.class);
        chainRecord = mock(ProductionChainProgress.class);
        MaterialRepository materials = mock(MaterialRepository.class);
        when(materials.findById(anyLong())).thenReturn(Optional.empty());
        resolver = new BrandStepResolver(
                mock(TradeQueryService.class),
                mock(ProcessingJobWorkflowService.class),
                batches,
                products,
                mock(ChainOrderStatusReader.class),
                mock(ChainProcessingStatusReader.class),
                materials,
                chainRecord);
    }

    @Test
    void 기록이_남은_동안은_기록_하나만_적는다() {
        givenConfirmedBatch(null);
        when(chainRecord.isPending(CHAIN_BATCH)).thenReturn(true);

        List<BrandStep> steps = resolver.resolve(2L, batchProject());

        assertThat(steps).hasSize(1);
        assertThat(steps.getFirst().toolName()).isEqualTo("record_production_chain");
        assertThat(steps.getFirst().instruction()).contains("제품 등록은 네 번을 다 마친 뒤에");
    }

    @Test
    void 기록이_끝나면_판매가를_묻고_제품을_등록할_차례다() {
        givenConfirmedBatch(null);
        when(chainRecord.isPending(CHAIN_BATCH)).thenReturn(false);

        List<BrandStep> steps = resolver.resolve(2L, batchProject());

        assertThat(steps).hasSize(1);
        BrandStep step = steps.getFirst();
        assertThat(step.label()).isEqualTo("판매할 제품 정보를 정할 차례입니다");
        assertThat(step.toolName()).isEqualTo("register_product_model");
        assertThat(step.instruction()).contains("판매가를 묻고").contains("판매가를 듣기 전에는 등록하지 않는다");
    }

    /** 0원 제품은 판매 시작이 아니라 판매가를 고칠 차례다 — 판매 중이어도 알린다. */
    @Test
    void 판매가가_0원이면_판매_시작_대신_판매가를_고칠_차례다() {
        givenProduct(ProductModelStatus.DRAFT, 0L);

        BrandStep step = onlyProductStep();

        assertThat(step.label()).isEqualTo("판매가를 정할 차례입니다");
        assertThat(step.toolName()).isEqualTo("update_product_model");
    }

    @Test
    void 판매_중인데_0원이면_알린다() {
        givenProduct(ProductModelStatus.ON_SALE, 0L);

        BrandStep step = onlyProductStep();

        assertThat(step.label()).isEqualTo("판매가가 0원인 채로 판매 중입니다");
        assertThat(step.toolName()).isEqualTo("update_product_model");
    }

    @Test
    void 판매가가_있으면_판매_시작_차례다() {
        givenProduct(ProductModelStatus.DRAFT, 150_000L);

        assertThat(onlyProductStep().toolName()).isEqualTo("set_product_on_sale");
    }

    private BrandStep onlyProductStep() {
        List<BrandStep> steps = resolver.resolve(2L, new AgentProject.Builder().productModel(PRODUCT).build());
        assertThat(steps).hasSize(1);
        return steps.getFirst();
    }

    private AgentProject batchProject() {
        return new AgentProject.Builder().batch(BATCH).build();
    }

    private void givenConfirmedBatch(Long productModelId) {
        ProductionBatchResponse batch = mock(ProductionBatchResponse.class);
        when(batch.productionBatchId()).thenReturn(BATCH);
        when(batch.chainBatchId()).thenReturn(CHAIN_BATCH);
        when(batch.batchName()).thenReturn("폐데님 바지");
        when(batch.productModelId()).thenReturn(productModelId);
        when(batch.producedQuantity()).thenReturn(10);
        when(batch.status()).thenReturn(ProductionBatchStatus.CONFIRMED);
        when(batches.findMyBatches(any())).thenReturn(List.of(batch));
    }

    private void givenProduct(ProductModelStatus status, long price) {
        ProductModelResponse model = mock(ProductModelResponse.class);
        when(model.productModelId()).thenReturn(PRODUCT);
        when(model.name()).thenReturn("폐데님 업사이클링 바지");
        when(model.status()).thenReturn(status);
        when(model.price()).thenReturn(price);
        when(model.stockQuantity()).thenReturn(10L);
        when(products.findMyProductModels(any())).thenReturn(List.of(model));
    }
}
