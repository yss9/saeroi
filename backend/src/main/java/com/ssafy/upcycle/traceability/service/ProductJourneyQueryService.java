package com.ssafy.upcycle.traceability.service;

import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransaction;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransactionStatus;
import com.ssafy.upcycle.blockchain.repository.BlockchainTransactionRepository;
import com.ssafy.upcycle.evidence.entity.EvidenceFile;
import com.ssafy.upcycle.evidence.entity.EvidenceReferenceType;
import com.ssafy.upcycle.evidence.entity.EvidenceType;
import com.ssafy.upcycle.evidence.repository.EvidenceFileRepository;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialComposition;
import com.ssafy.upcycle.material.entity.MaterialLineage;
import com.ssafy.upcycle.material.entity.MaterialLineageRelationType;
import com.ssafy.upcycle.material.repository.MaterialCompositionRepository;
import com.ssafy.upcycle.material.repository.MaterialLineageRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.processing.entity.ProcessingJob;
import com.ssafy.upcycle.processing.entity.ProcessingJobStatus;
import com.ssafy.upcycle.processing.repository.ProcessingJobRepository;
import com.ssafy.upcycle.processing.repository.ProcessingRequestTypeRepository;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductOrder;
import com.ssafy.upcycle.product.entity.ProductOrderStatus;
import com.ssafy.upcycle.product.entity.ProductShipping;
import com.ssafy.upcycle.product.repository.ProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderRepository;
import com.ssafy.upcycle.product.repository.ProductShippingRepository;
import com.ssafy.upcycle.production.entity.ProductionBatch;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import com.ssafy.upcycle.production.entity.ProductionInput;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
import com.ssafy.upcycle.production.repository.ProductionInputRepository;
import com.ssafy.upcycle.traceability.dto.JourneyStageStatus;
import com.ssafy.upcycle.traceability.dto.JourneyStageType;
import com.ssafy.upcycle.traceability.dto.ProductJourneyResponse;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 제품 개체에서 생산 투입과 소재 혈통을 거슬러 공개 Journey를 조립한다. */
@Service
@RequiredArgsConstructor
public class ProductJourneyQueryService {

    static final int MAX_DEPTH = 5;
    static final int MAX_MATERIALS = 200;
    static final String TRUNCATED_MESSAGE = "이전 이력은 일부 생략되었습니다";
    private static final String VERIFY_SOURCE = "verifySource";
    private static final String CONFIRM_PRODUCTION = "confirmProduction";

    private final ProductionBatchRepository productionBatchRepository;
    private final ProductionInputRepository productionInputRepository;
    private final MaterialRepository materialRepository;
    private final MaterialLineageRepository materialLineageRepository;
    private final MaterialCompositionRepository materialCompositionRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final ProcessingRequestTypeRepository processingRequestTypeRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final UserRepository userRepository;
    private final MaterialTradeRepository materialTradeRepository;
    private final ProductOrderItemRepository productOrderItemRepository;
    private final ProductOrderRepository productOrderRepository;
    private final ProductShippingRepository productShippingRepository;
    private final BlockchainTransactionRepository blockchainTransactionRepository;
    private final EvidenceFileRepository evidenceFileRepository;

    /**
     * 정상 생산 경로로 생성된 개체만 Journey를 갖는다.
     *
     * <p>기존 테스트 픽스처나 마이그레이션 이전의 불완전 행 때문에 Passport 제품 정보까지
     * 500이 되지 않도록, 생산 배치·투입 연결이 없으면 빈 값을 돌려준다.
     */
    @Transactional(readOnly = true)
    public Optional<ProductJourneyResponse> findJourney(ProductItem item) {
        Optional<ProductionBatch> foundBatch = productionBatchRepository.findById(
                item.getProductionBatchId());
        if (foundBatch.isEmpty() || foundBatch.get().getStatus() != ProductionBatchStatus.CONFIRMED) {
            return Optional.empty();
        }

        ProductionBatch finalBatch = foundBatch.get();
        List<ProductionInput> finalInputs = productionInputRepository
                .findAllByProductionBatchId(finalBatch.getId())
                .stream()
                .filter(input -> input.getProductUsedQuantity() > 0)
                .sorted(Comparator.comparing(ProductionInput::getId))
                .toList();
        if (finalInputs.isEmpty()) {
            return Optional.empty();
        }

        LineageGraph graph = traceAncestors(finalInputs.stream()
                .map(ProductionInput::getMaterialId)
                .toList());
        if (!graph.materials().keySet().containsAll(
                finalInputs.stream().map(ProductionInput::getMaterialId).toList())) {
            return Optional.empty();
        }
        if (graph.edges().stream().anyMatch(edge ->
                !graph.materials().containsKey(edge.getChildMaterialId())
                        || !graph.materials().containsKey(edge.getParentMaterialId()))) {
            return Optional.empty();
        }

        Map<Long, ProcessingJob> processingJobs = loadProcessingJobs(graph.edges());
        Map<Long, ProductionBatch> historicalBatches = loadHistoricalBatches(graph.edges());
        if (!hasEveryOperation(graph.edges(), processingJobs, historicalBatches)) {
            return Optional.empty();
        }
        Map<TradeKey, MaterialTrade> completedTrades = completedTrades(
                graph, processingJobs, historicalBatches, finalInputs, finalBatch);

        Set<Long> companyIds = new LinkedHashSet<>();
        rootMaterials(graph).stream()
                .map(Material::getRegistrantId)
                .forEach(companyIds::add);
        processingJobs.values().stream().map(ProcessingJob::getProcessorId).forEach(companyIds::add);
        historicalBatches.values().stream().map(ProductionBatch::getProducerId).forEach(companyIds::add);
        companyIds.add(finalBatch.getProducerId());
        completedTrades.values().stream().map(MaterialTrade::getBuyerId).forEach(companyIds::add);
        Map<Long, UserRole> roles = userRepository.findAllById(companyIds).stream()
                .collect(Collectors.toMap(User::getId, User::getRole));
        Map<Long, CompanyView> companies = companyProfileRepository.findAllById(companyIds).stream()
                .collect(Collectors.toMap(
                        CompanyProfile::getUserId,
                        profile -> new CompanyView(profile, roles.get(profile.getUserId()))));

        return Optional.of(assemble(
                item,
                finalBatch,
                finalInputs,
                graph,
                processingJobs,
                historicalBatches,
                completedTrades,
                companies));
    }

    private ProductJourneyResponse assemble(
            ProductItem item,
            ProductionBatch finalBatch,
            List<ProductionInput> finalInputs,
            LineageGraph graph,
            Map<Long, ProcessingJob> processingJobs,
            Map<Long, ProductionBatch> historicalBatches,
            Map<TradeKey, MaterialTrade> completedTrades,
            Map<Long, CompanyView> companies) {
        List<ProductJourneyResponse.Node> nodes = new ArrayList<>();
        Map<Long, String> materialProducerNode = new HashMap<>();
        Map<Long, String> processingNodeByJob = new HashMap<>();
        Map<Long, String> brandNodeByBatch = new HashMap<>();
        Map<TradeKey, String> collectionNodeByTrade = new HashMap<>();

        Set<String> sourceNodeIds = new LinkedHashSet<>();
        List<Material> sources = rootMaterials(graph);
        int sourceSequence = 1;
        for (Material source : sources) {
            String nodeId = "source-" + sourceSequence++;
            sourceNodeIds.add(nodeId);
            materialProducerNode.put(source.getId(), nodeId);
            nodes.add(sourceNode(nodeId, source, companies.get(source.getRegistrantId())));
        }

        int collectionSequence = 1;
        for (Map.Entry<TradeKey, MaterialTrade> entry : completedTrades.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparing(
                                MaterialTrade::getReceivedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(MaterialTrade::getId)))
                .toList()) {
            String nodeId = "collection-brand-" + collectionSequence++;
            collectionNodeByTrade.put(entry.getKey(), nodeId);
            Material material = graph.materials().get(entry.getKey().materialId());
            nodes.add(collectionNode(nodeId, entry.getValue(), material,
                    companies.get(entry.getValue().getBuyerId())));
        }

        List<ProcessingJob> orderedJobs = processingJobs.values().stream()
                .sorted(Comparator.comparing(
                                ProcessingJob::getCompletedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProcessingJob::getId))
                .toList();
        int processingSequence = 1;
        for (ProcessingJob job : orderedJobs) {
            String nodeId = "processing-" + processingSequence++;
            processingNodeByJob.put(job.getId(), nodeId);
            List<Material> outputs = outputMaterials(graph, job.getId(), MaterialLineageRelationType.PROCESSING);
            outputs.forEach(output -> materialProducerNode.put(output.getId(), nodeId));
            nodes.add(processingNode(nodeId, job, outputs, companies.get(job.getProcessorId())));
        }

        List<ProductionBatch> orderedHistoricalBatches = historicalBatches.values().stream()
                .sorted(Comparator.comparing(
                                ProductionBatch::getConfirmedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProductionBatch::getId))
                .toList();
        int historicalBrandSequence = 1;
        for (ProductionBatch batch : orderedHistoricalBatches) {
            String nodeId = "brand-history-" + historicalBrandSequence++;
            brandNodeByBatch.put(batch.getId(), nodeId);
            List<Material> outputs = outputMaterials(
                    graph, batch.getId(), MaterialLineageRelationType.PRODUCTION_SCRAP);
            outputs.forEach(output -> materialProducerNode.put(output.getId(), nodeId));
            nodes.add(brandNode(
                    nodeId, batch, toJourneyMaterials(outputs), companies.get(batch.getProducerId())));
        }

        String finalBrandNodeId = "brand-final";
        brandNodeByBatch.put(finalBatch.getId(), finalBrandNodeId);
        nodes.add(brandNode(
                finalBrandNodeId,
                finalBatch,
                finalInputs.stream()
                        .map(input -> toJourneyMaterial(graph.materials().get(input.getMaterialId())))
                        .toList(),
                companies.get(finalBatch.getProducerId())));

        List<ProductJourneyResponse.Edge> edges = new ArrayList<>();
        for (MaterialLineage lineage : sortedLineage(graph.edges())) {
            String from = materialProducerNode.get(lineage.getParentMaterialId());
            String to = lineage.getRelationType() == MaterialLineageRelationType.PROCESSING
                    ? processingNodeByJob.get(lineage.getReferenceId())
                    : brandNodeByBatch.get(lineage.getReferenceId());
            if (from == null || to == null || from.equals(to)) {
                continue;
            }
            boolean processing = lineage.getRelationType() == MaterialLineageRelationType.PROCESSING;
            Material material = graph.materials().get(lineage.getParentMaterialId());
            Long receivingBrandId = processing
                    ? processingJobs.get(lineage.getReferenceId()).getBrandId()
                    : historicalBatches.get(lineage.getReferenceId()).getProducerId();
            MaterialTrade trade = completedTrades.get(new TradeKey(material.getId(), receivingBrandId));
            String collectionNodeId = collectionNodeByTrade.get(new TradeKey(material.getId(), receivingBrandId));
            if (collectionNodeId != null && !from.equals(collectionNodeId)) {
                edges.add(edge(from, collectionNodeId, JourneyStageType.COLLECTION, material,
                        movement(trade.getShippedAt(), trade.getReceivedAt())));
                from = collectionNodeId;
            }
            Movement movement = processing
                    ? movement(processingJobs.get(lineage.getReferenceId()).getShippedAt(),
                            processingJobs.get(lineage.getReferenceId()).getReceivedAt())
                    : movement(null, historicalBatches.get(lineage.getReferenceId()).getConfirmedAt());
            edges.add(edge(
                    from,
                    to,
                    collectionNodeId == null ? movementStage(
                            from, sourceNodeIds,
                            processing ? JourneyStageType.PROCESSING : JourneyStageType.BRAND)
                            : processing ? JourneyStageType.PROCESSING : JourneyStageType.BRAND,
                    material,
                    movement));
        }

        for (ProductionInput input : finalInputs) {
            String from = materialProducerNode.get(input.getMaterialId());
            if (from == null || from.equals(finalBrandNodeId)) {
                continue;
            }
            Material material = graph.materials().get(input.getMaterialId());
            TradeKey tradeKey = new TradeKey(material.getId(), finalBatch.getProducerId());
            MaterialTrade trade = completedTrades.get(tradeKey);
            String collectionNodeId = collectionNodeByTrade.get(tradeKey);
            if (collectionNodeId != null && !from.equals(collectionNodeId)) {
                edges.add(edge(from, collectionNodeId, JourneyStageType.COLLECTION, material,
                        movement(trade.getShippedAt(), trade.getReceivedAt())));
                from = collectionNodeId;
            }
            edges.add(edge(
                    from,
                    finalBrandNodeId,
                    collectionNodeId == null
                            ? movementStage(from, sourceNodeIds, JourneyStageType.BRAND)
                            : JourneyStageType.BRAND,
                    material,
                    movement(null, finalBatch.getConfirmedAt())));
        }

        Delivery delivery = deliveryFor(item, finalBrandNodeId, edges.size() + 1);
        delivery.node().ifPresent(nodes::add);
        delivery.edge().ifPresent(edges::add);

        List<ProductJourneyResponse.Edge> numberedEdges = numberEdges(edges);
        List<ProductJourneyResponse.Stage> stages = stages(
                nodes, numberedEdges, delivery.status());

        return new ProductJourneyResponse(
                stages,
                List.copyOf(nodes),
                numberedEdges,
                composition(finalInputs),
                finalBatch.getUpcycleRatio(),
                nodes.stream()
                        .filter(node -> node.stage() != JourneyStageType.DELIVERY)
                        .allMatch(this::hasRequiredBlockchainVerification),
                graph.truncated(),
                graph.truncated() ? TRUNCATED_MESSAGE : null);
    }

    private ProductJourneyResponse.Node sourceNode(
            String nodeId, Material source, CompanyView company) {
        return new ProductJourneyResponse.Node(
                nodeId,
                JourneyStageType.SOURCE,
                companyName(company),
                companyRole(company),
                location(company),
                latitude(company),
                longitude(company),
                source.getCreatedAt(),
                source.getCreatedAt(),
                source.getStatus().name(),
                source.getDescription(),
                List.of(),
                List.of(toJourneyMaterial(source)),
                proofs(List.of(new ChainReference(
                        BlockchainReferenceType.MATERIAL, source.getId()))),
                evidence(List.of(new EvidenceReference(
                        EvidenceReferenceType.MATERIAL, source.getId()))));
    }

    private ProductJourneyResponse.Node processingNode(
            String nodeId,
            ProcessingJob job,
            List<Material> outputs,
            CompanyView company) {
        List<ChainReference> chainReferences = new ArrayList<>();
        chainReferences.add(new ChainReference(BlockchainReferenceType.PROCESSING, job.getId()));
        outputs.forEach(material -> chainReferences.add(
                new ChainReference(BlockchainReferenceType.MATERIAL, material.getId())));

        List<EvidenceReference> evidenceReferences = new ArrayList<>();
        evidenceReferences.add(new EvidenceReference(
                EvidenceReferenceType.PROCESSING_JOB, job.getId()));
        outputs.forEach(material -> evidenceReferences.add(
                new EvidenceReference(EvidenceReferenceType.MATERIAL, material.getId())));

        List<ProcessType> processTypes = processingRequestTypeRepository
                .findAllByProcessingRequestIdOrderBySequenceNoAsc(job.getProcessingRequestId())
                .stream()
                .map(type -> type.getProcessType())
                .toList();
        return new ProductJourneyResponse.Node(
                nodeId,
                JourneyStageType.PROCESSING,
                companyName(company),
                companyRole(company),
                location(company),
                latitude(company),
                longitude(company),
                job.getReceivedAt() != null ? job.getReceivedAt() : job.getCreatedAt(),
                job.getCompletedAt(),
                job.getStatus().name(),
                null,
                processTypes,
                toJourneyMaterials(outputs),
                proofs(chainReferences),
                evidence(evidenceReferences));
    }

    /** 거래를 마친 브랜드의 수령·보유 지점. 가공 작업과는 별개의 실제 소유 구간이다. */
    private ProductJourneyResponse.Node collectionNode(
            String nodeId, MaterialTrade trade, Material material, CompanyView company) {
        return new ProductJourneyResponse.Node(
                nodeId,
                JourneyStageType.COLLECTION,
                companyName(company),
                companyRole(company),
                location(company),
                latitude(company),
                longitude(company),
                trade.getReceivedAt(),
                trade.getReceivedAt(),
                trade.getStatus().name(),
                "브랜드 소재 수령",
                List.of(),
                material == null ? List.of() : List.of(toJourneyMaterial(material)),
                proofs(List.of(new ChainReference(BlockchainReferenceType.TRADE, trade.getId()))),
                List.of());
    }

    private ProductJourneyResponse.Node brandNode(
            String nodeId,
            ProductionBatch batch,
            List<ProductJourneyResponse.Material> materials,
            CompanyView company) {
        List<EvidenceReference> evidenceReferences = new ArrayList<>();
        evidenceReferences.add(new EvidenceReference(
                EvidenceReferenceType.PRODUCTION_BATCH, batch.getId()));
        productionInputRepository.findAllByProductionBatchId(batch.getId()).forEach(input ->
                evidenceReferences.add(new EvidenceReference(
                        EvidenceReferenceType.PRODUCTION_INPUT, input.getId())));
        return new ProductJourneyResponse.Node(
                nodeId,
                JourneyStageType.BRAND,
                companyName(company),
                companyRole(company),
                location(company),
                latitude(company),
                longitude(company),
                batch.getCreatedAt(),
                batch.getConfirmedAt(),
                batch.getStatus().name(),
                batch.getBatchName(),
                List.of(),
                materials,
                proofs(List.of(new ChainReference(
                        BlockchainReferenceType.PRODUCTION, batch.getId()))),
                evidence(evidenceReferences));
    }

    private Delivery deliveryFor(ProductItem item, String brandNodeId, int nextEdgeSequence) {
        return productOrderItemRepository.findByProductItemId(item.getId())
                .flatMap(orderItem -> productOrderRepository.findById(orderItem.getProductOrderId()))
                .map(order -> deliveryFor(order, brandNodeId, nextEdgeSequence))
                .orElseGet(() -> new Delivery(Optional.empty(), Optional.empty(), JourneyStageStatus.PENDING));
    }

    private Delivery deliveryFor(ProductOrder order, String brandNodeId, int nextEdgeSequence) {
        Optional<ProductShipping> shipping = productShippingRepository.findByProductOrderId(order.getId());
        if (shipping.isEmpty()) {
            return new Delivery(Optional.empty(), Optional.empty(), deliveryStatus(order));
        }
        ProductShipping address = shipping.get();
        ProductJourneyResponse.Node node = new ProductJourneyResponse.Node(
                "delivery",
                JourneyStageType.DELIVERY,
                null,
                null,
                deliveryRegion(address.getAddress()),
                null,
                null,
                order.getShippedAt(),
                order.getDeliveredAt(),
                order.getStatus().name(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of());
        ProductJourneyResponse.Edge edge = new ProductJourneyResponse.Edge(
                "edge-" + nextEdgeSequence,
                brandNodeId,
                node.nodeId(),
                JourneyStageType.DELIVERY,
                null,
                null,
                order.getShippedAt(),
                order.getDeliveredAt(),
                deliveryStatus(order));
        return new Delivery(Optional.of(node), Optional.of(edge), deliveryStatus(order));
    }

    private JourneyStageStatus deliveryStatus(ProductOrder order) {
        return order.getStatus() == ProductOrderStatus.DELIVERED
                ? JourneyStageStatus.COMPLETED
                : JourneyStageStatus.CURRENT;
    }

    private List<ProductJourneyResponse.Stage> stages(
            List<ProductJourneyResponse.Node> nodes,
            List<ProductJourneyResponse.Edge> edges,
            JourneyStageStatus deliveryStatus) {
        List<String> sourceNodes = nodeIds(nodes, JourneyStageType.SOURCE);
        List<String> collectionNodes = nodeIds(nodes, JourneyStageType.COLLECTION);
        List<String> processingNodes = nodeIds(nodes, JourneyStageType.PROCESSING);
        List<String> brandNodes = nodeIds(nodes, JourneyStageType.BRAND);
        List<String> deliveryNodes = nodeIds(nodes, JourneyStageType.DELIVERY);
        List<String> collectionEdges = edgeIds(edges, JourneyStageType.COLLECTION);
        List<String> processingEdges = edgeIds(edges, JourneyStageType.PROCESSING);
        List<String> brandEdges = edgeIds(edges, JourneyStageType.BRAND);
        return List.of(
                new ProductJourneyResponse.Stage(
                        JourneyStageType.SOURCE,
                        1,
                        recorded(sourceNodes, List.of()),
                        sourceNodes,
                        List.of()),
                new ProductJourneyResponse.Stage(
                        JourneyStageType.COLLECTION,
                        2,
                        recorded(collectionNodes, collectionEdges),
                        collectionNodes,
                        collectionEdges),
                new ProductJourneyResponse.Stage(
                        JourneyStageType.PROCESSING,
                        3,
                        recorded(processingNodes, processingEdges),
                        processingNodes,
                        processingEdges),
                new ProductJourneyResponse.Stage(
                        JourneyStageType.BRAND,
                        4,
                        recorded(brandNodes, brandEdges),
                        brandNodes,
                        brandEdges),
                new ProductJourneyResponse.Stage(
                        JourneyStageType.DELIVERY,
                        5,
                        deliveryStatus,
                        deliveryNodes,
                        edgeIds(edges, JourneyStageType.DELIVERY)));
    }

    /**
     * 기록이 있는 단계만 "완료" 라고 말한다.
     *
     * <p>예전에는 앞의 네 단계를 <b>무조건</b> {@code COMPLETED} 로 채웠다. 가공을 거치지
     * 않고 소재에서 바로 만든 제품도 여권이 "가공: 완료" 라고 단언했고, 그 단계에는 지점도
     * 이동도 하나 없었다 (2026-09-21 확인 — 실제로 가공 없이 만든 제품의 PROCESSING 이
     * 노드 0개·엣지 0개인 채 COMPLETED 였다).
     *
     * <p>여권은 소비자가 출처를 믿으려고 보는 화면이라 없는 단계를 있었다고 하면 안 된다.
     * 기록이 없으면 {@code PENDING} 으로 두어 화면이 "대기" 로 그리게 한다.
     */
    private JourneyStageStatus recorded(List<String> nodeIds, List<String> edgeIds) {
        return nodeIds.isEmpty() && edgeIds.isEmpty()
                ? JourneyStageStatus.PENDING
                : JourneyStageStatus.COMPLETED;
    }

    private List<String> nodeIds(
            List<ProductJourneyResponse.Node> nodes, JourneyStageType stage) {
        return nodes.stream()
                .filter(node -> node.stage() == stage)
                .map(ProductJourneyResponse.Node::nodeId)
                .toList();
    }

    private List<String> edgeIds(
            List<ProductJourneyResponse.Edge> edges, JourneyStageType stage) {
        return edges.stream()
                .filter(edge -> edge.stage() == stage)
                .map(ProductJourneyResponse.Edge::edgeId)
                .toList();
    }

    private List<ProductJourneyResponse.Edge> numberEdges(List<ProductJourneyResponse.Edge> edges) {
        List<ProductJourneyResponse.Edge> numbered = new ArrayList<>(edges.size());
        int sequence = 1;
        for (ProductJourneyResponse.Edge edge : edges) {
            numbered.add(new ProductJourneyResponse.Edge(
                    "edge-" + sequence++,
                    edge.fromNodeId(),
                    edge.toNodeId(),
                    edge.stage(),
                    edge.materialName(),
                    edge.materialType(),
                    edge.shippedAt(),
                    edge.receivedAt(),
                    edge.status()));
        }
        return List.copyOf(numbered);
    }

    private ProductJourneyResponse.Edge edge(
            String from,
            String to,
            JourneyStageType stage,
            Material material,
            Movement movement) {
        return new ProductJourneyResponse.Edge(
                "",
                from,
                to,
                stage,
                material == null ? null : material.getName(),
                material == null ? null : material.getMaterialType(),
                movement.shippedAt(),
                movement.receivedAt(),
                movement.status());
    }

    /**
     * 거래 수령 이력이 없는 과거·자가 보유 소재는 별도의 브랜드 수령 지점을 만들지 않는다.
     * 이 경우에만 공급업체 출발 이동선을 기존 수거 단계로 표현한다.
     */
    private JourneyStageType movementStage(
            String fromNodeId, Set<String> sourceNodeIds, JourneyStageType targetStage) {
        return sourceNodeIds.contains(fromNodeId) ? JourneyStageType.COLLECTION : targetStage;
    }

    private Movement movement(LocalDateTime shippedAt, LocalDateTime receivedAt) {
        JourneyStageStatus status;
        if (receivedAt != null) {
            status = JourneyStageStatus.COMPLETED;
        } else if (shippedAt != null) {
            status = JourneyStageStatus.CURRENT;
        } else {
            status = JourneyStageStatus.PENDING;
        }
        return new Movement(shippedAt, receivedAt, status);
    }

    /**
     * 소재가 다음 공정으로 가기 전에 브랜드가 거래로 수령한 경우만 수령 지점을 만든다.
     * 거래 없는 자가 보유 소재에 브랜드를 지어내면 실제 이력보다 한 단계를 더 보여 주게 된다.
     */
    private Map<TradeKey, MaterialTrade> completedTrades(
            LineageGraph graph,
            Map<Long, ProcessingJob> processingJobs,
            Map<Long, ProductionBatch> historicalBatches,
            List<ProductionInput> finalInputs,
            ProductionBatch finalBatch) {
        Map<TradeKey, TradeExpectation> expectations = new LinkedHashMap<>();
        for (MaterialLineage lineage : graph.edges()) {
            Material material = graph.materials().get(lineage.getParentMaterialId());
            boolean processing = lineage.getRelationType() == MaterialLineageRelationType.PROCESSING;
            Long receivingBrandId = processing
                    ? processingJobs.get(lineage.getReferenceId()).getBrandId()
                    : historicalBatches.get(lineage.getReferenceId()).getProducerId();
            LocalDateTime downstreamAt = processing
                    ? processingJobs.get(lineage.getReferenceId()).getShippedAt()
                    : historicalBatches.get(lineage.getReferenceId()).getConfirmedAt();
            expectTrade(expectations, material, receivingBrandId, downstreamAt);
        }
        for (ProductionInput input : finalInputs) {
            Material material = graph.materials().get(input.getMaterialId());
            expectTrade(expectations, material, finalBatch.getProducerId(), finalBatch.getConfirmedAt());
        }
        if (expectations.isEmpty()) {
            return Map.of();
        }

        return materialTradeRepository.findAllByMaterialIdInAndStatus(
                        expectations.keySet().stream().map(TradeKey::materialId).collect(Collectors.toSet()),
                        TradeStatus.COMPLETED)
                .stream()
                .filter(trade -> isExpectedTrade(trade, expectations.get(
                        new TradeKey(trade.getMaterialId(), trade.getBuyerId()))))
                .collect(Collectors.toMap(
                        trade -> new TradeKey(trade.getMaterialId(), trade.getBuyerId()),
                        Function.identity(),
                        (left, right) -> left.getReceivedAt().isAfter(right.getReceivedAt()) ? left : right,
                        LinkedHashMap::new));
    }

    private void expectTrade(
            Map<TradeKey, TradeExpectation> expectations,
            Material material,
            Long buyerId,
            LocalDateTime downstreamAt) {
        if (material == null || buyerId == null) {
            return;
        }
        TradeKey key = new TradeKey(material.getId(), buyerId);
        expectations.merge(
                key,
                new TradeExpectation(material.getRegistrantId(), downstreamAt),
                (current, candidate) -> new TradeExpectation(
                        current.sellerId(), earliest(current.downstreamAt(), candidate.downstreamAt())));
    }

    private boolean isExpectedTrade(MaterialTrade trade, TradeExpectation expectation) {
        return expectation != null
                && trade.getSellerId().equals(expectation.sellerId())
                && (expectation.downstreamAt() == null
                        || !trade.getReceivedAt().isAfter(expectation.downstreamAt()));
    }

    private LocalDateTime earliest(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }

    private List<ProductJourneyResponse.Composition> composition(List<ProductionInput> inputs) {
        BigDecimal total = inputs.stream()
                .map(ProductionInput::getProductUsedQuantity)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) {
            return List.of();
        }

        Map<String, BigDecimal> percentages = new HashMap<>();
        for (ProductionInput input : inputs) {
            BigDecimal materialShare = BigDecimal.valueOf(input.getProductUsedQuantity())
                    .divide(total, 8, RoundingMode.HALF_UP);
            for (MaterialComposition component : materialCompositionRepository
                    .findByMaterialIdOrderByPercentageDescFiberTypeAsc(input.getMaterialId())) {
                percentages.merge(
                        component.getFiberType(),
                        component.getPercentage().multiply(materialShare),
                        BigDecimal::add);
            }
        }
        return percentages.entrySet().stream()
                .map(entry -> new ProductJourneyResponse.Composition(
                        entry.getKey(), entry.getValue().setScale(2, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparing(
                                ProductJourneyResponse.Composition::percentage,
                                Comparator.reverseOrder())
                        .thenComparing(ProductJourneyResponse.Composition::fiberType))
                .toList();
    }

    private List<ProductJourneyResponse.BlockchainProof> proofs(
            Collection<ChainReference> references) {
        Map<String, ProductJourneyResponse.BlockchainProof> proofs = new LinkedHashMap<>();
        for (ChainReference reference : references) {
            for (BlockchainTransaction transaction : blockchainTransactionRepository
                    .findByReferenceTypeAndReferenceIdOrderByConfirmedAtDesc(
                            reference.type(), reference.id())) {
                proofs.putIfAbsent(transaction.getTxHash(), new ProductJourneyResponse.BlockchainProof(
                        reference.type().name(),
                        transaction.getChainId(),
                        transaction.getContractAddress(),
                        transaction.getFunctionName(),
                        transaction.getTxHash(),
                        transaction.getBlockNumber(),
                        transaction.getStatus(),
                        transaction.getConfirmedAt()));
            }
        }
        return List.copyOf(proofs.values());
    }

    private List<ProductJourneyResponse.EvidenceCount> evidence(
            Collection<EvidenceReference> references) {
        Map<EvidenceType, Long> counts = new EnumMap<>(EvidenceType.class);
        for (EvidenceReference reference : references) {
            List<EvidenceFile> files = evidenceFileRepository
                    .findByReferenceTypeAndReferenceIdAndDeletedAtIsNullOrderByUploadedAtAscIdAsc(
                            reference.type(), reference.id());
            for (EvidenceFile file : files) {
                counts.merge(file.getEvidenceType(), 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> new ProductJourneyResponse.EvidenceCount(
                        entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<ProductJourneyResponse.Material> toJourneyMaterials(List<Material> materials) {
        return materials.stream()
                .sorted(materialComparator())
                .map(this::toJourneyMaterial)
                .toList();
    }

    private ProductJourneyResponse.Material toJourneyMaterial(Material material) {
        return new ProductJourneyResponse.Material(
                material.getName(),
                material.getMaterialType(),
                material.getRootOrigin(),
                material.getTotalQuantity());
    }

    /**
     * 일반적인 온체인 기록이 아니라 단계 확정을 증명하는 성공 트랜잭션만 검증으로 센다.
     * Processing은 아직 전용 확정 함수가 없으므로 PROCESSING 참조로 남은 성공 기록을
     * 요구한다. 전용 함수가 도입되면 이 조건도 함수명까지 좁혀야 한다.
     */
    private boolean hasRequiredBlockchainVerification(ProductJourneyResponse.Node node) {
        return switch (node.stage()) {
            case SOURCE -> hasSuccessfulProof(
                    node, BlockchainReferenceType.MATERIAL, VERIFY_SOURCE);
            case PROCESSING -> node.blockchainProofs().stream().anyMatch(proof ->
                    proof.status() == BlockchainTransactionStatus.SUCCESS
                            && proof.referenceType().equals(BlockchainReferenceType.PROCESSING.name()));
            case BRAND -> hasSuccessfulProof(
                    node, BlockchainReferenceType.PRODUCTION, CONFIRM_PRODUCTION);
            case COLLECTION, DELIVERY -> true;
        };
    }

    private boolean hasSuccessfulProof(
            ProductJourneyResponse.Node node,
            BlockchainReferenceType referenceType,
            String functionName) {
        return node.blockchainProofs().stream().anyMatch(proof ->
                proof.status() == BlockchainTransactionStatus.SUCCESS
                        && proof.referenceType().equals(referenceType.name())
                        && proof.functionName().equals(functionName));
    }

    private List<Material> outputMaterials(
            LineageGraph graph, Long referenceId, MaterialLineageRelationType type) {
        return graph.edges().stream()
                .filter(edge -> edge.getRelationType() == type)
                .filter(edge -> edge.getReferenceId().equals(referenceId))
                .map(MaterialLineage::getChildMaterialId)
                .distinct()
                .map(graph.materials()::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private Comparator<Material> materialComparator() {
        return Comparator.comparing(
                        Material::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Material::getId);
    }

    private List<Material> rootMaterials(LineageGraph graph) {
        Set<Long> derivedMaterialIds = graph.edges().stream()
                .map(MaterialLineage::getChildMaterialId)
                .collect(Collectors.toSet());
        return graph.materials().values().stream()
                .filter(material -> !derivedMaterialIds.contains(material.getId()))
                .sorted(materialComparator())
                .toList();
    }

    private List<MaterialLineage> sortedLineage(List<MaterialLineage> edges) {
        return edges.stream()
                .sorted(Comparator.comparing(MaterialLineage::getCreatedAt)
                        .thenComparing(MaterialLineage::getId))
                .toList();
    }

    private String companyName(CompanyView company) {
        return company == null ? null : company.profile().getCompanyName();
    }

    private UserRole companyRole(CompanyView company) {
        return company == null ? null : company.role();
    }

    private String location(CompanyView company) {
        if (company == null) {
            return null;
        }
        CompanyProfile profile = company.profile();
        return profile.getAddressDetail() == null
                ? profile.getAddress()
                : profile.getAddress() + " " + profile.getAddressDetail();
    }

    private BigDecimal latitude(CompanyView company) {
        return company == null ? null : company.profile().getLatitude();
    }

    private BigDecimal longitude(CompanyView company) {
        return company == null ? null : company.profile().getLongitude();
    }

    /** 소비자 주소는 시·도 수준 첫 토큰까지만 공개한다. */
    static String deliveryRegion(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        return address.trim().split("\\s+", 2)[0];
    }

    private Map<Long, ProcessingJob> loadProcessingJobs(List<MaterialLineage> edges) {
        Set<Long> ids = edges.stream()
                .filter(edge -> edge.getRelationType() == MaterialLineageRelationType.PROCESSING)
                .map(MaterialLineage::getReferenceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return processingJobRepository.findAllById(ids).stream()
                .filter(job -> job.getStatus() == ProcessingJobStatus.CONFIRMED)
                .collect(Collectors.toMap(ProcessingJob::getId, Function.identity()));
    }

    private Map<Long, ProductionBatch> loadHistoricalBatches(List<MaterialLineage> edges) {
        Set<Long> ids = edges.stream()
                .filter(edge -> edge.getRelationType() == MaterialLineageRelationType.PRODUCTION_SCRAP)
                .map(MaterialLineage::getReferenceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return productionBatchRepository.findAllById(ids).stream()
                .filter(batch -> batch.getStatus() == ProductionBatchStatus.CONFIRMED)
                .collect(Collectors.toMap(ProductionBatch::getId, Function.identity()));
    }

    private boolean hasEveryOperation(
            List<MaterialLineage> edges,
            Map<Long, ProcessingJob> processingJobs,
            Map<Long, ProductionBatch> historicalBatches) {
        return edges.stream().allMatch(edge ->
                edge.getRelationType() == MaterialLineageRelationType.PROCESSING
                        ? processingJobs.containsKey(edge.getReferenceId())
                        : historicalBatches.containsKey(edge.getReferenceId()));
    }

    private LineageGraph traceAncestors(List<Long> startMaterialIds) {
        Set<Long> visited = new LinkedHashSet<>(startMaterialIds);
        Map<LineageKey, MaterialLineage> edges = new LinkedHashMap<>();
        Deque<Long> frontier = new ArrayDeque<>(startMaterialIds);
        boolean truncated = false;

        for (int depth = 1; depth <= MAX_DEPTH && !frontier.isEmpty(); depth++) {
            Deque<Long> next = new ArrayDeque<>();
            while (!frontier.isEmpty()) {
                Long childId = frontier.poll();
                for (MaterialLineage edge : materialLineageRepository.findAllByChildMaterialId(childId)) {
                    Long parentId = edge.getParentMaterialId();
                    if (!visited.contains(parentId) && visited.size() >= MAX_MATERIALS) {
                        truncated = true;
                        continue;
                    }
                    edges.putIfAbsent(LineageKey.of(edge), edge);
                    if (visited.add(parentId)) {
                        next.add(parentId);
                    }
                }
            }
            frontier = next;
        }

        if (!frontier.isEmpty()) {
            for (Long materialId : frontier) {
                boolean hasMore = materialLineageRepository.findAllByChildMaterialId(materialId).stream()
                        .anyMatch(edge -> !visited.contains(edge.getParentMaterialId()));
                if (hasMore) {
                    truncated = true;
                    break;
                }
            }
        }

        Map<Long, Material> materials = materialRepository.findAllById(visited).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
        return new LineageGraph(materials, List.copyOf(edges.values()), truncated);
    }

    private record LineageGraph(
            Map<Long, Material> materials, List<MaterialLineage> edges, boolean truncated) {}

    private record LineageKey(
            Long childId,
            Long parentId,
            MaterialLineageRelationType type,
            Long referenceId) {
        static LineageKey of(MaterialLineage edge) {
            return new LineageKey(
                    edge.getChildMaterialId(),
                    edge.getParentMaterialId(),
                    edge.getRelationType(),
                    edge.getReferenceId());
        }
    }

    private record ChainReference(BlockchainReferenceType type, Long id) {}

    private record EvidenceReference(EvidenceReferenceType type, Long id) {}

    /** 노드에 붙는 업체 공개 정보 — 상호명·역할·주소·좌표를 한 묶음으로 넘긴다. */
    private record CompanyView(CompanyProfile profile, UserRole role) {}

    private record Movement(
            LocalDateTime shippedAt, LocalDateTime receivedAt, JourneyStageStatus status) {}

    private record Delivery(
            Optional<ProductJourneyResponse.Node> node,
            Optional<ProductJourneyResponse.Edge> edge,
            JourneyStageStatus status) {}

    private record TradeKey(Long materialId, Long buyerId) {}

    private record TradeExpectation(Long sellerId, LocalDateTime downstreamAt) {}

}
