package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.service.ConversationListings;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.processing.ApproveProcessingResultTool;
import com.ssafy.upcycle.agent.tool.processing.PayProcessingFeeTool;
import com.ssafy.upcycle.agent.tool.product.SetProductOnSaleTool;
import com.ssafy.upcycle.agent.tool.product.UpdateProductModelTool;
import com.ssafy.upcycle.product.dto.ProductModelUpdateRequest;
import com.ssafy.upcycle.agent.tool.production.ConfirmProductionTool;
import com.ssafy.upcycle.agent.tool.trade.PurchaseMaterialTool;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.service.MaterialQueryService;
import com.ssafy.upcycle.processing.dto.ProcessingJobResponse;
import com.ssafy.upcycle.processing.dto.ProcessingOutputResponse;
import com.ssafy.upcycle.processing.entity.ProcessingJob;
import com.ssafy.upcycle.processing.entity.ProcessingJobStatus;
import com.ssafy.upcycle.processing.entity.ProcessingInput;
import com.ssafy.upcycle.processing.repository.ProcessingInputRepository;
import com.ssafy.upcycle.processing.repository.ProcessingJobRepository;
import com.ssafy.upcycle.processing.service.ProcessingJobService;
import com.ssafy.upcycle.processing.service.ProcessingJobWorkflowService;
import com.ssafy.upcycle.processing.service.ProcessingPaymentService;
import com.ssafy.upcycle.product.dto.ProductModelResponse;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.service.ProductModelQueryService;
import com.ssafy.upcycle.product.service.ProductModelRegistrationService;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.dto.ProductionInputResponse;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import com.ssafy.upcycle.production.service.ProductionConfirmService;
import com.ssafy.upcycle.trade.dto.ListingResponse;
import com.ssafy.upcycle.trade.repository.ListingSearchQueryRepository;
import com.ssafy.upcycle.trade.service.PaymentService;
import com.ssafy.upcycle.trade.service.TradeCommandService;
import com.ssafy.upcycle.agent.tool.product.RegisterProductModelTool;
import com.ssafy.upcycle.agent.tool.production.CancelProductionBatchTool;
import com.ssafy.upcycle.agent.tool.production.OpenProductionBatchTool;
import com.ssafy.upcycle.trade.entity.ListingStatus;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 쓰기 도구가 <b>카드를 띄우기 전에</b> 스스로 보는 것들.
 *
 * <p>카드는 담당자가 동의하는 자리다. 이미 끝난 일이나 아직 이른 일에 카드가 뜨면 담당자는
 * 누른 뒤에야 실패를 본다. 그리고 카드 제목은 처리 뒤 대화에 남는 <b>동의 기록</b>이라,
 * 거기 들어가는 숫자는 모델이 아니라 서버가 써야 한다.
 */
class WriteToolGuardTests {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static AgentToolContext brand() {
        return new AgentToolContext(2L, mock(AgentConversation.class));
    }

    private static JsonNode args(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** 되돌릴 수 없는 유일한 지점이라 확정할 수 없는 배치에는 카드를 띄우지 않는다. */
    @Nested
    @DisplayName("생산 확정")
    class ConfirmProduction {

        private final ProductionBatchService batches = mock(ProductionBatchService.class);
        private final ConfirmProductionTool tool = new ConfirmProductionTool(
                JSON, mock(ProductionConfirmService.class), batches);

        @Test
        void 투입이_없으면_카드_전에_막는다() {
            givenBatch(ProductionBatchStatus.DRAFT, List.of());

            assertThat(tool.refuseBeforeProposal(brand(), confirm()))
                    .hasValueSatisfying(refusal -> assertThat(refusal.payloadJson()).contains("NO_INPUT"));
        }

        @Test
        void 취소된_배치는_카드_전에_막는다() {
            givenBatch(ProductionBatchStatus.CANCELED, List.of(input(6_000)));

            assertThat(tool.refuseBeforeProposal(brand(), confirm()))
                    .hasValueSatisfying(refusal ->
                            assertThat(refusal.payloadJson()).contains("BATCH_NOT_CONFIRMABLE"));
        }

        @Test
        void 이미_확정된_배치는_지난_일로_닫는다() {
            givenBatch(ProductionBatchStatus.CONFIRMED, List.of(input(6_000)));

            assertThat(tool.refuseBeforeProposal(brand(), confirm())).isEmpty();
            assertThat(tool.stalenessReason(brand(), confirm())).isPresent();
        }

        @Test
        void 투입이_있는_초안은_통과하고_제목은_서버가_쓴다() {
            givenBatch(ProductionBatchStatus.DRAFT, List.of(input(6_000), input(2_000), input(1_000)));

            assertThat(tool.refuseBeforeProposal(brand(), confirm())).isEmpty();
            assertThat(tool.serverSummary(brand(), confirm()))
                    .hasValue("조합 가방 생산 확정 — 제품 10개 · 소재 9kg 소비 (되돌릴 수 없음)");
        }

        private JsonNode confirm() {
            return args("{\"batchId\":7,\"summary\":\"모델이 쓴 제목\"}");
        }

        private void givenBatch(ProductionBatchStatus status, List<ProductionInputResponse> inputs) {
            ProductionBatchResponse batch = mock(ProductionBatchResponse.class);
            when(batch.productionBatchId()).thenReturn(7L);
            when(batch.batchName()).thenReturn("조합 가방");
            when(batch.producedQuantity()).thenReturn(10);
            when(batch.status()).thenReturn(status);
            when(batch.inputs()).thenReturn(inputs);
            when(batches.findMyBatches(any())).thenReturn(List.of(batch));
        }

        private ProductionInputResponse input(long quantity) {
            ProductionInputResponse input = mock(ProductionInputResponse.class);
            when(input.inputQuantity()).thenReturn(quantity);
            return input;
        }
    }

    /**
     * 승인은 <b>업체가 결과물을 돌려보낸 뒤에만</b> 된다.
     *
     * <p>결과만 올라온 단계에서 승인 서명 카드가 자동으로 떴다. 승인은 반환 발송 뒤에만 받으므로
     * 그 카드는 절대 성공할 수 없었다.
     */
    @Nested
    @DisplayName("가공 결과 승인")
    class ApproveProcessingResult {

        private final ProcessingJobWorkflowService workflow = mock(ProcessingJobWorkflowService.class);
        private final ApproveProcessingResultTool tool = new ApproveProcessingResultTool(
                JSON, mock(ProcessingJobService.class), mock(MaterialQueryService.class), workflow);

        @Test
        void 결과만_올라온_단계에서는_카드를_띄우지_않는다() {
            givenJob(ProcessingJobStatus.RESULT_SUBMITTED);

            assertThat(tool.refuseBeforeProposal(brand(), approve()))
                    .hasValueSatisfying(refusal -> assertThat(refusal.payloadJson())
                            .contains("NOT_READY_FOR_APPROVAL")
                            .contains("반환 발송한 뒤"));
        }

        @Test
        void 반환_발송된_작업은_통과한다() {
            givenJob(ProcessingJobStatus.RETURN_SHIPPED);

            assertThat(tool.refuseBeforeProposal(brand(), approve())).isEmpty();
            assertThat(tool.stalenessReason(brand(), approve())).isEmpty();
        }

        @Test
        void 이미_승인된_작업은_지난_일로_닫는다() {
            givenJob(ProcessingJobStatus.CONFIRMED);

            assertThat(tool.stalenessReason(brand(), approve())).isPresent();
        }

        /** 방금 서명한 요청은 체인이 DB 를 앞서 있을 수 있다. 자기 서명을 지난 일로 읽지 않는다. */
        @Test
        void 방금_서명한_승인은_지난_일로_읽지_않는다() {
            givenJob(ProcessingJobStatus.CONFIRMED);

            assertThat(tool.stalenessReason(brand(), args(
                    "{\"jobId\":6,\"summary\":\"승인\",\"_userResult\":{\"txHash\":\"0xabc\"}}")))
                    .isEmpty();
        }

        /**
         * 승인 카드에는 무엇이 얼마나 돌아왔고 얼마가 줄었는지 적힌다. "결과 승인" 만 떠서 담당자는 10kg 을 보내 9.5kg 이
         * 돌아온 것을 모르고 승인했다.
         */
        @Test
        void 카드_제목에_결과물과_가공_중_손실이_보인다() {
            ProcessingOutputResponse output = new ProcessingOutputResponse(
                    1L, 45L, MaterialType.DENIM, "A", "양호", "폐데님 가공 소재", 9_500L);
            ProcessingJobResponse job = mock(ProcessingJobResponse.class);
            when(job.jobId()).thenReturn(6L);
            when(job.status()).thenReturn(ProcessingJobStatus.RETURN_SHIPPED);
            when(job.inputQuantity()).thenReturn(10_000L);
            when(job.lossQuantity()).thenReturn(500L);
            when(job.outputs()).thenReturn(List.of(output));
            List<ProcessingJobResponse> jobs = List.of(job);
            when(workflow.list(any())).thenReturn(jobs);

            assertThat(tool.serverSummary(brand(), approve())).hasValue(
                    "가공 작업 6 — 결과 승인 · 투입 10kg → 결과물 폐데님 가공 소재 9.5kg(A등급) · 가공 중 손실 0.5kg");
        }

        private JsonNode approve() {
            return args("{\"jobId\":6,\"summary\":\"승인\"}");
        }

        private void givenJob(ProcessingJobStatus status) {
            ProcessingJobResponse job = mock(ProcessingJobResponse.class);
            when(job.jobId()).thenReturn(6L);
            when(job.status()).thenReturn(status);
            when(workflow.list(any())).thenReturn(List.of(job));
        }
    }

    /** 서버가 스스로 띄우는 카드이기도 해서, 여기서 막지 않으면 부르지도 않은 카드가 실패한다. */
    @Nested
    @DisplayName("판매 시작")
    class SetProductOnSale {

        private final ProductModelQueryService products = mock(ProductModelQueryService.class);
        private final SetProductOnSaleTool tool = new SetProductOnSaleTool(
                JSON, mock(ProductModelRegistrationService.class), products);

        @Test
        void 재고가_없으면_카드_전에_막는다() {
            givenModel(ProductModelStatus.DRAFT, 0);

            assertThat(tool.refuseBeforeProposal(brand(), sale()))
                    .hasValueSatisfying(refusal -> assertThat(refusal.payloadJson()).contains("NO_STOCK"));
        }

        /** 0원 제품의 판매 시작 카드가 떠 결국 0원에 판매가 시작됐다. 재고가 있어도 막는다. */
        @Test
        void 판매가가_0원이면_재고가_있어도_막고_고치는_도구를_알려준다() {
            givenModel(ProductModelStatus.DRAFT, 10, 0L);

            assertThat(tool.refuseBeforeProposal(brand(), sale()))
                    .hasValueSatisfying(refusal -> assertThat(refusal.payloadJson())
                            .contains("PRICE_NOT_SET")
                            .contains("update_product_model"));
        }

        @Test
        void 이미_판매_중이면_지난_일로_닫는다() {
            givenModel(ProductModelStatus.ON_SALE, 10);

            assertThat(tool.stalenessReason(brand(), sale())).isPresent();
        }

        @Test
        void 재고가_있으면_통과하고_제목은_서버가_쓴다() {
            givenModel(ProductModelStatus.DRAFT, 10);

            assertThat(tool.refuseBeforeProposal(brand(), sale())).isEmpty();
            assertThat(tool.serverSummary(brand(), sale())).hasValue("조합 가방 판매 시작 — 재고 10개 · 39,000원");
        }

        private JsonNode sale() {
            return args("{\"productModelId\":5,\"summary\":\"모델이 쓴 제목\"}");
        }

        private void givenModel(ProductModelStatus status, long stock) {
            givenModel(status, stock, 39_000L);
        }

        private void givenModel(ProductModelStatus status, long stock, long price) {
            ProductModelResponse model = mock(ProductModelResponse.class);
            when(model.productModelId()).thenReturn(5L);
            when(model.name()).thenReturn("조합 가방");
            when(model.price()).thenReturn(price);
            when(model.status()).thenReturn(status);
            when(model.stockQuantity()).thenReturn(stock);
            when(products.findMyProductModels(any())).thenReturn(List.of(model));
        }
    }

    /**
     * 등록한 제품을 고친다. 이 도구가 없어 모델은 판매가를 "수정" 한다며 같은 제품을 하나 더 등록했다.
     */
    @Nested
    @DisplayName("제품 수정")
    class UpdateProductModel {

        private final ProductModelQueryService products = mock(ProductModelQueryService.class);
        private final ProductModelRegistrationService registration = mock(ProductModelRegistrationService.class);
        private final UpdateProductModelTool tool = new UpdateProductModelTool(JSON, registration, products);

        @Test
        void 제목에_무엇이_무엇으로_바뀌는지_서버가_쓴다() {
            givenModel(ProductModelStatus.ON_SALE, 0L);

            JsonNode update = args("{\"productModelId\":8,\"price\":150000,\"summary\":\"모델이 쓴 제목\"}");

            assertThat(tool.refuseBeforeProposal(brand(), update)).isEmpty();
            assertThat(tool.serverSummary(brand(), update))
                    .hasValue("폐데님 바지 수정 — 판매가 0원 → 150,000원");
        }

        @Test
        void 판매가를_0원으로_고치려_하면_막는다() {
            givenModel(ProductModelStatus.DRAFT, 150_000L);

            assertThat(tool.refuseBeforeProposal(brand(), args("{\"productModelId\":8,\"price\":0,\"summary\":\"s\"}")))
                    .hasValueSatisfying(refusal -> assertThat(refusal.payloadJson()).contains("PRICE_REQUIRED"));
        }

        @Test
        void 바뀌는_것이_없으면_막는다() {
            givenModel(ProductModelStatus.DRAFT, 150_000L);

            assertThat(tool.refuseBeforeProposal(brand(),
                            args("{\"productModelId\":8,\"price\":150000,\"name\":\"폐데님 바지\",\"summary\":\"s\"}")))
                    .hasValueSatisfying(refusal -> assertThat(refusal.payloadJson()).contains("NOTHING_TO_UPDATE"));
        }

        @Test
        void 남의_제품이나_없는_제품이면_막는다() {
            givenModel(ProductModelStatus.DRAFT, 150_000L);

            assertThat(tool.refuseBeforeProposal(brand(), args("{\"productModelId\":99,\"price\":1000,\"summary\":\"s\"}")))
                    .hasValueSatisfying(refusal -> assertThat(refusal.payloadJson()).contains("PRODUCT_NOT_FOUND"));
        }

        @Test
        void 판매를_끝낸_제품은_막는다() {
            givenModel(ProductModelStatus.DISCONTINUED, 150_000L);

            assertThat(tool.refuseBeforeProposal(brand(), args("{\"productModelId\":8,\"price\":1000,\"summary\":\"s\"}")))
                    .hasValueSatisfying(refusal -> assertThat(refusal.payloadJson()).contains("PRODUCT_DISCONTINUED"));
        }

        @Test
        void 보낸_값만_고친다() {
            ProductModelResponse updated = mock(ProductModelResponse.class);
            when(updated.name()).thenReturn("폐데님 바지");
            when(updated.price()).thenReturn(150_000L);
            when(registration.update(any(), any(), any())).thenReturn(updated);

            var result = tool.execute(brand(), args("{\"productModelId\":8,\"price\":150000,\"summary\":\"s\"}"));

            var request = org.mockito.ArgumentCaptor.forClass(ProductModelUpdateRequest.class);
            org.mockito.Mockito.verify(registration).update(org.mockito.ArgumentMatchers.eq(2L),
                    org.mockito.ArgumentMatchers.eq(8L), request.capture());
            assertThat(request.getValue().price()).isEqualTo(150_000L);
            assertThat(request.getValue().name()).isNull();
            assertThat(request.getValue().description()).isNull();
            assertThat(result.payloadJson()).contains("150,000원");
        }

        private void givenModel(ProductModelStatus status, long price) {
            ProductModelResponse model = mock(ProductModelResponse.class);
            when(model.productModelId()).thenReturn(8L);
            when(model.name()).thenReturn("폐데님 바지");
            when(model.price()).thenReturn(price);
            when(model.status()).thenReturn(status);
            List<ProductModelResponse> mine = List.of(model);
            when(products.findMyProductModels(any())).thenReturn(mine);
        }
    }

    /**
     * 금액이 들어가는 카드 제목은 <b>서버가 쓴다.</b>
     *
     * <p>모델이 12.5kg × 2,000원/kg 을 25,000,000원이라고 적은 적이 있다. 카드 금액 줄은 서버
     * 값이었지만 제목은 모델 문장이었고, 그 제목이 처리 뒤 "✓ 결제 … 완료" 기록으로 남는다.
     */
    @Nested
    @DisplayName("금액이 들어가는 카드 제목")
    class ServerSummary {

        @Test
        void 구매_카드_제목은_판매_글과_정본_계산으로_만든다() {
            ListingSearchQueryRepository listings = mock(ListingSearchQueryRepository.class);
            ListingResponse listing = mock(ListingResponse.class);
            when(listing.materialName()).thenReturn("폐데님 원단");
            when(listing.pricePerKg()).thenReturn(2_000L);
            when(listings.findDetail(anyLong())).thenReturn(listing);
            PurchaseMaterialTool tool = new PurchaseMaterialTool(
                    JSON, mock(TradeCommandService.class), mock(PaymentService.class), listings,
                    mock(ConversationListings.class), new com.ssafy.upcycle.agent.service.CompanyNames(org.mockito.Mockito.mock(com.ssafy.upcycle.user.repository.CompanyProfileRepository.class)));

            assertThat(tool.serverSummary(brand(), args(
                    "{\"listingId\":21,\"quantityGram\":12500,\"summary\":\"폐데님 12.5kg (25,000,000원)\"}")))
                    .hasValue("폐데님 원단 12.5kg 구매 · 25,000원");
        }

        @Test
        void 가공비_카드_제목은_작업에_정해진_가공비로_만든다() {
            ProcessingJobRepository jobs = mock(ProcessingJobRepository.class);
            ProcessingJob job = mock(ProcessingJob.class);
            when(job.getId()).thenReturn(6L);
            when(job.getBrandId()).thenReturn(2L);
            when(job.getFee()).thenReturn(30_000L);
            when(jobs.findById(6L)).thenReturn(Optional.of(job));
            PayProcessingFeeTool tool = new PayProcessingFeeTool(
                    JSON, mock(ProcessingPaymentService.class), jobs, mock(ProcessingInputRepository.class), mock(MaterialRepository.class));

            assertThat(tool.serverSummary(brand(), args("{\"jobId\":6,\"summary\":\"가공비 3만원\"}")))
                    .hasValue("가공 작업 6 — 가공비 · 30,000원");
        }

        /** 두 소재를 함께 맡기면 "가공 작업 8 가공비" 와 "가공 작업 9 가공비" 가 나란히 떴다. 어느 쪽이 울인지 적는다. */
        @Test
        void 무엇을_맡긴_가공인지_이름으로_적는다() {
            ProcessingJobRepository jobs = mock(ProcessingJobRepository.class);
            ProcessingJob job = mock(ProcessingJob.class);
            when(job.getId()).thenReturn(8L);
            when(job.getBrandId()).thenReturn(2L);
            when(job.getFee()).thenReturn(75_000L);
            when(jobs.findById(8L)).thenReturn(Optional.of(job));
            ProcessingInput input = mock(ProcessingInput.class);
            when(input.getMaterialId()).thenReturn(38L);
            when(input.getQuantity()).thenReturn(5_000L);
            ProcessingInputRepository inputs = mock(ProcessingInputRepository.class);
            when(inputs.findByProcessingJobId(8L)).thenReturn(Optional.of(input));
            Material wool = mock(Material.class);
            when(wool.getName()).thenReturn("울 원단");
            MaterialRepository materials = mock(MaterialRepository.class);
            when(materials.findById(38L)).thenReturn(Optional.of(wool));
            PayProcessingFeeTool tool = new PayProcessingFeeTool(
                    JSON, mock(ProcessingPaymentService.class), jobs, inputs, materials);

            assertThat(tool.serverSummary(brand(), args("{\"jobId\":8,\"summary\":\"가공비\"}")))
                    .hasValue("울 원단 5kg 가공 — 가공비 · 75,000원");
        }

        @Test
        void 남의_작업이면_제목을_만들지_않는다() {
            ProcessingJobRepository jobs = mock(ProcessingJobRepository.class);
            ProcessingJob job = mock(ProcessingJob.class);
            when(job.getBrandId()).thenReturn(99L);
            when(jobs.findById(6L)).thenReturn(Optional.of(job));
            PayProcessingFeeTool tool = new PayProcessingFeeTool(
                    JSON, mock(ProcessingPaymentService.class), jobs, mock(ProcessingInputRepository.class), mock(MaterialRepository.class));

            assertThat(tool.serverSummary(brand(), args("{\"jobId\":6,\"summary\":\"가공비\"}"))).isEmpty();
        }
    }

    /** 결제 카드라 누른 뒤에 실패하면 가장 아프다. 살 수 있을 때만 띄운다. */
    @Nested
    @DisplayName("소재 구매")
    class PurchaseMaterial {

        private final ListingSearchQueryRepository listings = mock(ListingSearchQueryRepository.class);
        private final ConversationListings shown = mock(ConversationListings.class);
        private final PurchaseMaterialTool tool = new PurchaseMaterialTool(
                JSON, mock(TradeCommandService.class), mock(PaymentService.class), listings, shown, new com.ssafy.upcycle.agent.service.CompanyNames(org.mockito.Mockito.mock(com.ssafy.upcycle.user.repository.CompanyProfileRepository.class)));

        /** 아래 경우들은 21번을 이 대화에서 찾아 보여 준 뒤다. */
        @org.junit.jupiter.api.BeforeEach
        void 판매_글_21번을_이_대화에서_찾아_보여_줬다() {
            List<Long> seen = List.of(21L);
            when(shown.seen(any())).thenReturn(seen);
        }

        /**
         * <b>이 대화에서 찾아 보여 준 판매 글만 산다.</b> 이력이 잘려 검색 결과가 사라지자, 모델이
         * 폐데님 원단(21번)으로 합의해 놓고 본 적 없는 19번(깨끗한 데님)으로 결제 카드를 띄웠다.
         * 19번은 실제로 있는 판매 글이라 "없는 판매 글" 확인에는 걸리지 않았다.
         */
        @Test
        void 보여_준_적_없는_판매_글이면_막고_보여_준_번호를_알려준다() {
            givenListing(ListingStatus.ON_SALE, 9L, 40_000);
            ListingResponse denim = mock(ListingResponse.class);
            when(denim.listingId()).thenReturn(21L);
            when(denim.materialName()).thenReturn("폐데님 원단");
            List<ListingResponse> current = List.of(denim);
            when(shown.current(any(), anyInt())).thenReturn(current);

            assertThat(tool.refuseBeforeProposal(brand(), args("{\"listingId\":19,\"quantityGram\":3000}")))
                    .hasValueSatisfying(result -> assertThat(result.payloadJson())
                            .contains("LISTING_NOT_SHOWN").contains("21번 폐데님 원단")
                            // 담당자가 번호를 직접 불러 준 경우의 길 — 견적으로 확인하면 "본 글" 이 된다.
                            .contains("quote_trade"));
        }

        @Test
        void 없는_판매_글이면_다시_찾으라고_한다() {
            assertThat(tool.refuseBeforeProposal(brand(), purchase(5_000)))
                    .hasValueSatisfying(result -> assertThat(result.payloadJson())
                            .contains("LISTING_NOT_FOUND").contains("search_listings"));
        }

        @Test
        void 판매가_끝난_글이면_막는다() {
            givenListing(ListingStatus.SOLD_OUT, 9L, 40_000);

            assertThat(tool.refuseBeforeProposal(brand(), purchase(5_000)))
                    .hasValueSatisfying(result -> assertThat(result.payloadJson()).contains("LISTING_NOT_ON_SALE"));
        }

        @Test
        void 자기_판매_글은_막는다() {
            givenListing(ListingStatus.ON_SALE, 2L, 40_000);

            assertThat(tool.refuseBeforeProposal(brand(), purchase(5_000)))
                    .hasValueSatisfying(result -> assertThat(result.payloadJson()).contains("SELF_PURCHASE"));
        }

        /** 남은 양을 문장으로 알려야 모델이 수량을 고쳐 다시 부른다. */
        @Test
        void 남은_양보다_많이_사면_남은_양을_알려준다() {
            givenListing(ListingStatus.ON_SALE, 9L, 4_000);

            assertThat(tool.refuseBeforeProposal(brand(), purchase(5_000)))
                    .hasValueSatisfying(result -> assertThat(result.payloadJson())
                            .contains("QUANTITY_EXCEEDS_AVAILABLE").contains("4kg"));
        }

        @Test
        void 살_수_있으면_카드를_띄운다() {
            givenListing(ListingStatus.ON_SALE, 9L, 40_000);

            assertThat(tool.refuseBeforeProposal(brand(), purchase(5_000))).isEmpty();
        }

        private void givenListing(ListingStatus status, Long sellerId, long available) {
            ListingResponse listing = mock(ListingResponse.class);
            when(listing.status()).thenReturn(status);
            when(listing.sellerId()).thenReturn(sellerId);
            when(listing.availableQuantity()).thenReturn(available);
            when(listings.findDetail(anyLong())).thenReturn(listing);
        }

        private JsonNode purchase(long quantityGram) {
            return args("{\"listingId\":21,\"quantityGram\":%d}".formatted(quantityGram));
        }
    }

    /** 배치에 확정 서명 지갑을 고정하므로, 지갑이 없으면 누른 뒤에 거절된다. */
    @Nested
    @DisplayName("생산 배치 열기")
    class OpenProductionBatch {

        private final UserWalletRepository wallets = mock(UserWalletRepository.class);
        private final OpenProductionBatchTool tool =
                new OpenProductionBatchTool(JSON, mock(ProductionBatchService.class), wallets);

        @Test
        void 지갑이_없으면_먼저_연결하라고_한다() {
            when(wallets.findByUserIdAndActiveTrue(any())).thenReturn(Optional.empty());

            assertThat(tool.refuseBeforeProposal(brand(), args("{}")))
                    .hasValueSatisfying(result -> assertThat(result.payloadJson()).contains("WALLET_NOT_LINKED"));
        }

        @Test
        void 지갑이_있으면_카드를_띄운다() {
            when(wallets.findByUserIdAndActiveTrue(any())).thenReturn(Optional.of(mock(UserWallet.class)));

            assertThat(tool.refuseBeforeProposal(brand(), args("{}"))).isEmpty();
        }
    }

    /** 서명을 시작했거나 확정된 배치는 되돌릴 수 없다. */
    @Nested
    @DisplayName("생산 배치 취소")
    class CancelProductionBatch {

        private final ProductionBatchService batches = mock(ProductionBatchService.class);
        private final CancelProductionBatchTool tool = new CancelProductionBatchTool(JSON, batches);

        @Test
        void 서명_중인_배치는_카드_전에_막는다() {
            givenBatch(batches, ProductionBatchStatus.SIGNING, null);

            assertThat(tool.refuseBeforeProposal(brand(), args("{\"batchId\":3}")))
                    .hasValueSatisfying(result -> assertThat(result.payloadJson())
                            .contains("BATCH_NOT_CANCELABLE").contains("SIGNING"));
        }

        @Test
        void 이미_취소된_배치는_이미_했다로_닫는다() {
            givenBatch(batches, ProductionBatchStatus.CANCELED, null);

            assertThat(tool.refuseBeforeProposal(brand(), args("{\"batchId\":3}"))).isEmpty();
            assertThat(tool.stalenessReason(brand(), args("{\"batchId\":3}")))
                    .hasValueSatisfying(reason -> assertThat(reason).contains("이미 취소"));
        }

        @Test
        void 초안이면_카드를_띄운다() {
            givenBatch(batches, ProductionBatchStatus.DRAFT, null);

            assertThat(tool.refuseBeforeProposal(brand(), args("{\"batchId\":3}"))).isEmpty();
            assertThat(tool.stalenessReason(brand(), args("{\"batchId\":3}"))).isEmpty();
        }
    }

    /** 사진까지 올린 뒤에 누르는 카드라, 누른 뒤에 거절되면 다시 해야 할 것이 많다. */
    @Nested
    @DisplayName("제품 등록")
    class RegisterProductModel {

        private final ProductionBatchService batches = mock(ProductionBatchService.class);
        private final com.ssafy.upcycle.agent.service.step.ProductionChainProgress chainRecord =
                mock(com.ssafy.upcycle.agent.service.step.ProductionChainProgress.class);
        private final ProductModelQueryService productQuery = mock(ProductModelQueryService.class);
        private final com.ssafy.upcycle.agent.project.AgentProjects projects =
                mock(com.ssafy.upcycle.agent.project.AgentProjects.class);
        private final RegisterProductModelTool tool = new RegisterProductModelTool(
                JSON, mock(ProductModelRegistrationService.class), batches, chainRecord, productQuery, provider(projects));

        /** 판매가를 묻지 않고 0원으로 등록했고, 그 제품이 0원에 판매까지 갔다. */
        @Test
        void 판매가가_없으면_카드_전에_막는다() {
            assertThat(tool.refuseBeforeProposal(brand(), args("{\"name\":\"폐데님 바지\",\"price\":0}")))
                    .hasValueSatisfying(result -> assertThat(result.payloadJson()).contains("PRICE_REQUIRED"));
            assertThat(tool.refuseBeforeProposal(brand(), args("{\"name\":\"폐데님 바지\"}")))
                    .hasValueSatisfying(result -> assertThat(result.payloadJson()).contains("PRICE_REQUIRED"));
        }

        /** 기록보다 제품 등록이 먼저 되자 판매 시작 카드가 기록보다 먼저 떴다. */
        @Test
        void 확정_배치의_블록체인_기록이_남았으면_기록부터_하게_한다() {
            givenBatch(batches, ProductionBatchStatus.CONFIRMED, null);
            when(chainRecord.isPending(any())).thenReturn(true);

            assertThat(tool.refuseBeforeProposal(brand(), args("{\"batchId\":3,\"price\":150000}")))
                    .hasValueSatisfying(result -> assertThat(result.payloadJson())
                            .contains("CHAIN_RECORD_FIRST").contains("record_production_chain"));
        }

        /** 판매가를 "수정" 한다며 같은 제품을 새로 등록해, 재고 없는 제품이 하나 더 생겼다. */
        @Test
        void 이_대화에서_이미_등록한_제품이면_고치는_도구를_쓰게_한다() {
            AgentConversation conversation = mock(AgentConversation.class);
            when(conversation.getId()).thenReturn(23L);
            when(projects.of(2L, 23L)).thenReturn(
                    new com.ssafy.upcycle.agent.project.AgentProject.Builder().productModel(8L).build());
            ProductModelResponse existing = mock(ProductModelResponse.class);
            when(existing.productModelId()).thenReturn(8L);
            when(existing.name()).thenReturn("폐데님 업사이클링 바지");
            when(existing.price()).thenReturn(0L);
            List<ProductModelResponse> mine = List.of(existing);
            when(productQuery.findMyProductModels(any())).thenReturn(mine);

            assertThat(tool.refuseBeforeProposal(new AgentToolContext(2L, conversation),
                            args("{\"name\":\"폐데님 업사이클링 바지\",\"price\":150000}")))
                    .hasValueSatisfying(result -> assertThat(result.payloadJson())
                            .contains("ALREADY_REGISTERED").contains("update_product_model"));
        }

        @Test
        void 이미_제품이_붙은_확정_배치면_이미_했다로_닫는다() {
            givenBatch(batches, ProductionBatchStatus.CONFIRMED, 70L);

            assertThat(tool.stalenessReason(brand(), args("{\"batchId\":3}")))
                    .hasValueSatisfying(reason -> assertThat(reason).contains("제품 70"));
        }

        @Test
        void 제품을_받을_수_없는_배치면_카드_전에_막는다() {
            givenBatch(batches, ProductionBatchStatus.CANCELED, null);

            assertThat(tool.refuseBeforeProposal(brand(), args("{\"batchId\":3,\"price\":30000}")))
                    .hasValueSatisfying(result -> assertThat(result.payloadJson()).contains("BATCH_NOT_ASSIGNABLE"));
        }

        @Test
        void 배치_없이_제품만_등록하면_볼_것이_없다() {
            assertThat(tool.refuseBeforeProposal(brand(), args("{\"price\":30000}"))).isEmpty();
            assertThat(tool.stalenessReason(brand(), args("{}"))).isEmpty();
        }
    }

    @SuppressWarnings("unchecked")
    private static org.springframework.beans.factory.ObjectProvider<com.ssafy.upcycle.agent.project.AgentProjects> provider(
            com.ssafy.upcycle.agent.project.AgentProjects projects) {
        org.springframework.beans.factory.ObjectProvider<com.ssafy.upcycle.agent.project.AgentProjects> provider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getObject()).thenReturn(projects);
        return provider;
    }

    /** 배치 3 하나를 가진 상태. 목을 다 만든 뒤에 스텁한다 — 스텁 안에서 목을 만들면 Mockito 가 거절한다. */
    private static void givenBatch(ProductionBatchService batches, ProductionBatchStatus status, Long productModelId) {
        ProductionBatchResponse batch = mock(ProductionBatchResponse.class);
        when(batch.productionBatchId()).thenReturn(3L);
        when(batch.status()).thenReturn(status);
        when(batch.productModelId()).thenReturn(productModelId);
        List<ProductionBatchResponse> mine = List.of(batch);
        when(batches.findMyBatches(any())).thenReturn(mine);
    }
}
