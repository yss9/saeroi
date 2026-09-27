package com.ssafy.upcycle.traceability.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransaction;
import com.ssafy.upcycle.blockchain.repository.BlockchainTransactionRepository;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialLineage;
import com.ssafy.upcycle.material.entity.MaterialLineageRelationType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialLineageRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.processing.entity.ProcessingJob;
import com.ssafy.upcycle.processing.repository.ProcessingJobRepository;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.production.entity.ProductionBatch;
import com.ssafy.upcycle.production.entity.ProductionInput;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
import com.ssafy.upcycle.production.repository.ProductionInputRepository;
import com.ssafy.upcycle.traceability.dto.JourneyStageStatus;
import com.ssafy.upcycle.traceability.dto.JourneyStageType;
import com.ssafy.upcycle.traceability.dto.ProductJourneyResponse;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.geolocation.GeoCoordinates;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 Journey 조립 규칙 중 HTTP 픽스처로 만들기 번거로운 것들을 고정한다 — 확정되지 않은
 * 기록의 배제, 여러 공급업체의 병합, 계보 깊이 제한, 블록체인 검증 판정.
 *
 * <p>응답에 무엇이 나가고 무엇이 빠지는지(공개 범위)는 {@code PassportControllerTests} 가
 * HTTP 표면에서 본다. 여기서 다시 확인하지 않는다.
 */
@SpringBootTest
@Transactional
class ProductJourneyQueryServiceTests {

    private static final String WALLET = "0x" + "1".repeat(40);
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 9, 1, 9, 0);

    @Autowired
    private ProductJourneyQueryService productJourneyQueryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyProfileRepository companyProfileRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialLineageRepository materialLineageRepository;

    @Autowired
    private ProcessingJobRepository processingJobRepository;

    @Autowired
    private ProductionBatchRepository productionBatchRepository;

    @Autowired
    private ProductionInputRepository productionInputRepository;

    @Autowired
    private ProductItemRepository productItemRepository;

    @Autowired
    private BlockchainTransactionRepository blockchainTransactionRepository;

    @Autowired
    private MaterialTradeRepository materialTradeRepository;

    private long sequence = 0;

    @Test
    void omitsTheJourneyWhenTheProductionBatchIsNotConfirmed() {
        User brand = company("리위브", UserRole.BRAND);
        Material denim = sourceMaterial(company("한빛섬유", UserRole.SUPPLIER));

        ProductionBatch batch = openBatch(brand, denim);
        ProductItem item = item(batch);

        assertThat(productJourneyQueryService.findJourney(item)).isEmpty();
    }

    @Test
    void omitsTheJourneyWhenAProcessingJobInTheLineageIsNotConfirmed() {
        User brand = company("리위브", UserRole.BRAND);
        User processor = company("그린프로세싱", UserRole.PROCESSOR);
        Material denim = sourceMaterial(company("한빛섬유", UserRole.SUPPLIER));

        ProcessingJob job = processingJobRepository.save(newJob(brand, processor));
        Material processed = processedFrom(List.of(denim), job, processor);

        ProductItem item = item(confirmBatch(openBatch(brand, processed)));

        assertThat(productJourneyQueryService.findJourney(item)).isEmpty();
    }

    @Test
    void mergesSeveralSuppliersIntoOneProcessingNode() {
        User brand = company("리위브", UserRole.BRAND);
        User processor = company("그린프로세싱", UserRole.PROCESSOR);
        Material denim = sourceMaterial(company("한빛섬유", UserRole.SUPPLIER));
        Material lining = sourceMaterial(company("대성원단", UserRole.SUPPLIER));

        Material processed = processedFrom(List.of(denim, lining), confirmedJob(brand, processor), processor);
        ProductItem item = item(confirmBatch(openBatch(brand, processed)));

        ProductJourneyResponse journey = productJourneyQueryService.findJourney(item).orElseThrow();

        assertThat(journey.nodes())
                .extracting(ProductJourneyResponse.Node::nodeId, ProductJourneyResponse.Node::stage)
                .containsExactly(
                        tuple("source-1", JourneyStageType.SOURCE),
                        tuple("source-2", JourneyStageType.SOURCE),
                        tuple("processing-1", JourneyStageType.PROCESSING),
                        tuple("brand-final", JourneyStageType.BRAND));
        assertThat(journey.edges())
                .extracting(
                        ProductJourneyResponse.Edge::fromNodeId,
                        ProductJourneyResponse.Edge::toNodeId,
                        ProductJourneyResponse.Edge::stage)
                .containsExactly(
                        tuple("source-1", "processing-1", JourneyStageType.COLLECTION),
                        tuple("source-2", "processing-1", JourneyStageType.COLLECTION),
                        tuple("processing-1", "brand-final", JourneyStageType.BRAND));
        assertThat(stage(journey, JourneyStageType.COLLECTION).edgeIds())
                .containsExactly("edge-1", "edge-2");
        assertThat(stage(journey, JourneyStageType.COLLECTION).nodeIds()).isEmpty();
        assertThat(journey.edges())
                .extracting(ProductJourneyResponse.Edge::status)
                .containsOnly(JourneyStageStatus.COMPLETED);
        assertThat(journey.truncated()).isFalse();
        assertThat(journey.message()).isNull();
    }

    @Test
    void routesPurchasedMaterialThroughTheBrandBeforeProcessing() {
        User brand = company("리위브", UserRole.BRAND);
        User processor = company("그린프로세싱", UserRole.PROCESSOR);
        User supplier = company("한빛섬유", UserRole.SUPPLIER);
        Material denim = sourceMaterial(supplier);
        MaterialTrade purchase = completedTrade(denim, supplier, brand);
        ProcessingJob job = confirmedJob(brand, processor);
        // 가공 발송 뒤에 같은 소재로 생성된 거래는 이 제품의 가공 전 수령 이력이 아니다.
        completedTrade(denim, supplier, brand);
        Material processed = processedFrom(List.of(denim), job, processor);

        ProductJourneyResponse journey = productJourneyQueryService
                .findJourney(item(confirmBatch(openBatch(brand, processed))))
                .orElseThrow();

        assertThat(journey.nodes())
                .extracting(ProductJourneyResponse.Node::nodeId, ProductJourneyResponse.Node::stage,
                        ProductJourneyResponse.Node::companyName)
                .contains(
                        tuple("source-1", JourneyStageType.SOURCE, "한빛섬유"),
                        tuple("collection-brand-1", JourneyStageType.COLLECTION, "리위브"),
                        tuple("processing-1", JourneyStageType.PROCESSING, "그린프로세싱"));
        assertThat(journey.edges())
                .extracting(
                        ProductJourneyResponse.Edge::fromNodeId,
                        ProductJourneyResponse.Edge::toNodeId,
                        ProductJourneyResponse.Edge::stage)
                .contains(
                        tuple("source-1", "collection-brand-1", JourneyStageType.COLLECTION),
                        tuple("collection-brand-1", "processing-1", JourneyStageType.PROCESSING));
        assertThat(journey.edges()).filteredOn(edge -> edge.fromNodeId().equals("source-1"))
                .singleElement()
                .satisfies(edge -> {
                    assertThat(edge.shippedAt()).isEqualTo(purchase.getShippedAt());
                    assertThat(edge.receivedAt()).isEqualTo(purchase.getReceivedAt());
                });
        assertThat(journey.edges()).filteredOn(edge -> edge.fromNodeId().equals("collection-brand-1"))
                .singleElement()
                .satisfies(edge -> {
                    assertThat(edge.shippedAt()).isEqualTo(job.getShippedAt());
                    assertThat(edge.receivedAt()).isEqualTo(job.getReceivedAt());
                });
    }

    @Test
    void marksTheJourneyTruncatedBeyondTheLineageDepthLimit() {
        User brand = company("리위브", UserRole.BRAND);
        User processor = company("그린프로세싱", UserRole.PROCESSOR);
        Material current = sourceMaterial(company("한빛섬유", UserRole.SUPPLIER));
        for (int step = 0; step <= ProductJourneyQueryService.MAX_DEPTH; step++) {
            current = processedFrom(List.of(current), confirmedJob(brand, processor), processor);
        }

        ProductItem item = item(confirmBatch(openBatch(brand, current)));

        ProductJourneyResponse journey = productJourneyQueryService.findJourney(item).orElseThrow();

        assertThat(journey.truncated()).isTrue();
        assertThat(journey.message()).isEqualTo(ProductJourneyQueryService.TRUNCATED_MESSAGE);
        assertThat(journey.nodes())
                .extracting(ProductJourneyResponse.Node::companyName)
                .doesNotContain("한빛섬유");
    }

    @Test
    void reportsBlockchainVerifiedWhenEveryCompanyNodeHasAProof() {
        User brand = company("리위브", UserRole.BRAND);
        Material denim = sourceMaterial(company("한빛섬유", UserRole.SUPPLIER));
        ProductionBatch batch = confirmBatch(openBatch(brand, denim));
        anchor(BlockchainReferenceType.MATERIAL, denim.getId(), "verifySource");
        anchor(BlockchainReferenceType.PRODUCTION, batch.getId(), "confirmProduction");

        ProductJourneyResponse journey =
                productJourneyQueryService.findJourney(item(batch)).orElseThrow();

        assertThat(journey.blockchainVerified()).isTrue();
        assertThat(journey.edges())
                .extracting(
                        ProductJourneyResponse.Edge::fromNodeId,
                        ProductJourneyResponse.Edge::toNodeId,
                        ProductJourneyResponse.Edge::stage)
                .containsExactly(tuple("source-1", "brand-final", JourneyStageType.COLLECTION));
    }

    @Test
    void carriesTheAnchoredTransactionFieldsIntoTheNodesProof() {
        // S15P21E104-127: blockchainVerified 는 "증명이 있는가"만 보고, 그 증명의 각 필드가
        // 실제 blockchain_transaction 행과 한 글자도 다르지 않은지는 아무도 확인하지 않았다.
        // 화면은 이 값을 그대로 링크·해시 표시에 쓰므로 여기서 어긋나면 QR 스캔한 소비자가
        // 잘못된 트랜잭션으로 안내받는다.
        User brand = company("리위브", UserRole.BRAND);
        Material denim = sourceMaterial(company("한빛섬유", UserRole.SUPPLIER));
        ProductionBatch batch = confirmBatch(openBatch(brand, denim));
        BlockchainTransaction sourceProof =
                anchor(BlockchainReferenceType.MATERIAL, denim.getId(), "verifySource");
        anchor(BlockchainReferenceType.PRODUCTION, batch.getId(), "confirmProduction");

        ProductJourneyResponse journey =
                productJourneyQueryService.findJourney(item(batch)).orElseThrow();

        ProductJourneyResponse.Node sourceNode = journey.nodes().stream()
                .filter(candidate -> candidate.nodeId().equals("source-1"))
                .findFirst()
                .orElseThrow();
        assertThat(sourceNode.blockchainProofs())
                .extracting(
                        ProductJourneyResponse.BlockchainProof::referenceType,
                        ProductJourneyResponse.BlockchainProof::chainId,
                        ProductJourneyResponse.BlockchainProof::contractAddress,
                        ProductJourneyResponse.BlockchainProof::functionName,
                        ProductJourneyResponse.BlockchainProof::txHash,
                        ProductJourneyResponse.BlockchainProof::blockNumber,
                        ProductJourneyResponse.BlockchainProof::status,
                        ProductJourneyResponse.BlockchainProof::confirmedAt)
                .containsExactly(tuple(
                        "MATERIAL",
                        sourceProof.getChainId(),
                        sourceProof.getContractAddress(),
                        "verifySource",
                        sourceProof.getTxHash(),
                        sourceProof.getBlockNumber(),
                        sourceProof.getStatus(),
                        sourceProof.getConfirmedAt()));
    }

    @Test
    void reportsBlockchainUnverifiedWhenOneCompanyNodeHasNoProof() {
        User brand = company("리위브", UserRole.BRAND);
        Material denim = sourceMaterial(company("한빛섬유", UserRole.SUPPLIER));
        ProductionBatch batch = confirmBatch(openBatch(brand, denim));
        anchor(BlockchainReferenceType.PRODUCTION, batch.getId(), "confirmProduction");

        ProductJourneyResponse journey =
                productJourneyQueryService.findJourney(item(batch)).orElseThrow();

        assertThat(journey.blockchainVerified()).isFalse();
    }

    @Test
    void doesNotTreatUnrelatedBlockchainTransactionsAsVerification() {
        User brand = company("리위브", UserRole.BRAND);
        Material denim = sourceMaterial(company("한빛섬유", UserRole.SUPPLIER));
        ProductionBatch batch = confirmBatch(openBatch(brand, denim));
        anchor(BlockchainReferenceType.MATERIAL, denim.getId(), "mintMaterial");
        anchor(BlockchainReferenceType.PRODUCTION, batch.getId(), "createBatch");

        ProductJourneyResponse journey =
                productJourneyQueryService.findJourney(item(batch)).orElseThrow();

        assertThat(journey.blockchainVerified()).isFalse();
    }

    private ProductJourneyResponse.Stage stage(
            ProductJourneyResponse journey, JourneyStageType type) {
        return journey.stages().stream()
                .filter(stage -> stage.stage() == type)
                .findFirst()
                .orElseThrow();
    }

    private User company(String companyName, UserRole role) {
        long id = ++sequence;
        User user = userRepository.save(User.register(
                "journey-" + id + "@example.com",
                "{noop}x",
                companyName,
                "010-0000-0000",
                role));
        companyProfileRepository.save(CompanyProfile.register(
                user.getId(),
                companyName,
                String.format("%03d-45-6789%d", id, id % 10),
                "00000",
                "서울특별시 성동구 연무장길 " + id,
                id + "층",
                new GeoCoordinates(new BigDecimal("37.5446000"), new BigDecimal("127.0559000"))));
        return user;
    }

    private Material sourceMaterial(User supplier) {
        return materialRepository.save(Material.register(
                "MAT-JOURNEY-" + (++sequence),
                MaterialType.DENIM,
                MaterialRootOrigin.WASTE,
                "폐데님",
                null,
                "A",
                6_000,
                null,
                null,
                supplier.getId()));
    }

    private ProcessingJob newJob(User brand, User processor) {
        long id = ++sequence;
        return ProcessingJob.create(
                "JOB-JOURNEY-" + id,
                1_000L + id,
                brand.getId(),
                processor.getId(),
                WALLET,
                WALLET,
                0,
                PaymentMethod.MOCK,
                BASE.plusHours(id));
    }

    private ProcessingJob confirmedJob(User brand, User processor) {
        ProcessingJob job = processingJobRepository.save(newJob(brand, processor));
        LocalDateTime at = BASE.plusHours(sequence);
        job.markPaid("journey-payment-" + sequence, 0, at.plusMinutes(1));
        job.accept(at.plusMinutes(2));
        job.ship(at.plusMinutes(2));
        job.receive(at.plusMinutes(3));
        job.startProcessing();
        job.submitResult(
                5_000,
                5_000,
                "0x" + String.valueOf(sequence % 10).repeat(64),
                "https://example.com/manifest/" + sequence,
                at.plusMinutes(4));
        job.confirmResult(at.plusMinutes(5));
        return job;
    }

    private Material processedFrom(List<Material> parents, ProcessingJob job, User processor) {
        Material output = materialRepository.save(Material.process(
                "MAT-PROCESSED-" + (++sequence),
                parents.get(0),
                parents.get(0).getMaterialType(),
                "가공 소재 " + sequence,
                "A",
                5_000,
                processor.getId()));
        for (Material parent : parents) {
            materialLineageRepository.save(MaterialLineage.of(
                    output.getId(),
                    parent.getId(),
                    MaterialLineageRelationType.PROCESSING,
                    job.getId()));
        }
        return output;
    }

    private MaterialTrade completedTrade(Material material, User supplier, User brand) {
        MaterialTrade trade = materialTradeRepository.save(MaterialTrade.create(
                "TRADE-JOURNEY-" + (++sequence),
                7_000L + sequence,
                material.getId(),
                supplier.getId(),
                brand.getId(),
                WALLET,
                "0x" + "2".repeat(40),
                material.getTotalQuantity(),
                10_000L,
                PaymentMethod.MOCK,
                BASE.plusHours(sequence)));
        LocalDateTime receivedAt = BASE.plusHours(sequence).plusMinutes(3);
        trade.markPaid("journey-trade-payment-" + sequence, 10_000L,
                BASE.plusHours(sequence), PaymentMethod.MOCK);
        trade.ship(BASE.plusHours(sequence).plusMinutes(1));
        trade.confirmReceipt(receivedAt);
        trade.complete(receivedAt);
        return trade;
    }

    private ProductionBatch openBatch(User brand, Material input) {
        long id = ++sequence;
        ProductionBatch batch = productionBatchRepository.save(ProductionBatch.open(
                "BATCH-JOURNEY-" + id, "RE:BAG 생산", 1L, 1, brand.getId(), WALLET));
        productionInputRepository.save(ProductionInput.record(
                batch.getId(), input.getId(), 5_000, 5_000, 0, 0, null, 9_000L + id));
        return batch;
    }

    private ProductionBatch confirmBatch(ProductionBatch batch) {
        batch.startSigning();
        batch.confirm(0, 5_000, BASE.plusDays(1));
        return batch;
    }

    private ProductItem item(ProductionBatch batch) {
        long id = ++sequence;
        return productItemRepository.save(ProductItem.produce(
                batch.getId(),
                1L,
                (int) id,
                "ITEM-JOURNEY-" + id,
                UUID.randomUUID().toString().replace("-", "")));
    }

    private BlockchainTransaction anchor(
            BlockchainReferenceType referenceType, Long referenceId, String functionName) {
        long id = ++sequence;
        return blockchainTransactionRepository.save(BlockchainTransaction.confirmed(
                31_221L,
                "0x" + "a".repeat(40),
                "0x" + String.format("%064d", id),
                referenceType,
                referenceId,
                functionName,
                WALLET,
                100L + id,
                BASE.plusDays(2)));
    }
}
