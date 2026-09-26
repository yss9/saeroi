package com.ssafy.upcycle.traceability.dto;

import com.ssafy.upcycle.blockchain.entity.BlockchainTransactionStatus;
import com.ssafy.upcycle.evidence.entity.EvidenceType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.user.entity.UserRole;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * QR Passport 가 공개하는 제품 Journey.
 *
 * <p>DB 순번과 거래 정보는 담지 않는다. 단가·거래 조건·보유자는 필드 자체가 없고,
 * 노드와 간선 식별자는 응답 안에서만 쓰는 {@code source-1}, {@code processing-1} 형태라
 * 내부 PK 를 역추측할 수 없다. 소재 수량은 거래량이 아니라 해당 소재 배치에 등록된 양만
 * 제공한다.
 */
public record ProductJourneyResponse(
        List<Stage> stages,
        List<Node> nodes,
        List<Edge> edges,
        List<Composition> materialComposition,
        BigDecimal upcycleRatio,
        boolean blockchainVerified,
        boolean truncated,
        String message) {

    public record Stage(
            JourneyStageType stage,
            int sequence,
            JourneyStageStatus status,
            List<String> nodeIds,
            List<String> edgeIds) {}

    public record Node(
            String nodeId,
            JourneyStageType stage,
            String companyName,
            UserRole companyRole,
            String locationName,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            String status,
            String description,
            List<ProcessType> processTypes,
            List<Material> materials,
            List<BlockchainProof> blockchainProofs,
            List<EvidenceCount> evidenceSummary) {}

    /**
     * 노드 사이의 이동. 지도의 이동선이 이 값 하나로 그려진다 — 출발·도착 노드가 좌표를
     * 들고 있고, 여기에 발송일·수령일·이동 완료 상태가 붙는다.
     */
    public record Edge(
            String edgeId,
            String fromNodeId,
            String toNodeId,
            JourneyStageType stage,
            String materialName,
            MaterialType materialType,
            LocalDateTime shippedAt,
            LocalDateTime receivedAt,
            JourneyStageStatus status) {}

    public record Material(
            String name,
            MaterialType materialType,
            MaterialRootOrigin rootOrigin,
            long batchQuantityGrams) {}

    public record Composition(String fiberType, BigDecimal percentage) {}

    public record BlockchainProof(
            String referenceType,
            Long chainId,
            String contractAddress,
            String functionName,
            String txHash,
            Long blockNumber,
            BlockchainTransactionStatus status,
            LocalDateTime confirmedAt) {}

    public record EvidenceCount(EvidenceType evidenceType, long count) {}
}
