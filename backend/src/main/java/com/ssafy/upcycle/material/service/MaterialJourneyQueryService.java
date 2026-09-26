package com.ssafy.upcycle.material.service;

import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransaction;
import com.ssafy.upcycle.blockchain.repository.BlockchainTransactionRepository;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.MaterialErrorCode;
import com.ssafy.upcycle.material.dto.MaterialJourneyResponse;
import com.ssafy.upcycle.material.entity.MaterialEvent;
import com.ssafy.upcycle.material.entity.MaterialEventReferenceType;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.entity.MaterialJourneyStage;
import com.ssafy.upcycle.material.repository.MaterialEventRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소재 상세 화면의 Journey Timeline 조립 (S15P21E104-73).
 *
 * <p>사건 로그 자체는 {@link MaterialEventQueryService} 가 이미 갖고 있다. 이 서비스가
 * 더하는 것은 셋뿐이다 — <b>단계 분류</b>(40여 종의 이벤트를 4단계로 묶기), <b>행위자
 * 표시명·지갑</b>(actorId 를 사람이 읽는 값으로), <b>온체인 증빙 해시</b>(있으면). 그래프를
 * 만들지 않으므로 {@code ProductJourneyQueryService} 만큼 무겁지 않다.
 */
@Service
@RequiredArgsConstructor
public class MaterialJourneyQueryService {

    /**
     * {@link MaterialEventType} → {@link MaterialJourneyStage}.
     *
     * <p>{@code CHILD_MATERIAL_CREATED} 는 SOURCE 가 아니라 PROCESSING 이다 — 이 사건은
     * <b>부모</b> 소재에 남는 기록이라 "내가 원료가 되어 자식을 냈다"는 가공 결과이지,
     * 이 소재 자신의 출처가 아니다. 자식 쪽 출처 사건은 같은 시점에 {@code MATERIAL_DERIVED}
     * 로 자식 소재에 따로 남는다.
     */
    private static final Map<MaterialEventType, MaterialJourneyStage> STAGE_BY_EVENT_TYPE =
            new EnumMap<>(MaterialEventType.class);

    static {
        for (MaterialEventType type : List.of(
                MaterialEventType.MATERIAL_HISTORY_IMPORTED,
                MaterialEventType.MATERIAL_REGISTERED,
                MaterialEventType.MATERIAL_DERIVED,
                MaterialEventType.MATERIAL_MINTED,
                MaterialEventType.SOURCE_VERIFIED,
                MaterialEventType.EVIDENCE_ATTACHED,
                MaterialEventType.EVIDENCE_DELETED)) {
            STAGE_BY_EVENT_TYPE.put(type, MaterialJourneyStage.SOURCE);
        }
        for (MaterialEventType type : List.of(
                MaterialEventType.MARKETPLACE_LISTED,
                MaterialEventType.MARKETPLACE_LISTING_CANCELED,
                MaterialEventType.MARKETPLACE_LISTING_LINKED_ON_CHAIN,
                MaterialEventType.TRADE_CREATED,
                MaterialEventType.TRADE_PAID,
                MaterialEventType.TRADE_CANCELED,
                MaterialEventType.TRADE_LINKED_ON_CHAIN,
                MaterialEventType.TRADE_SHIPPED,
                MaterialEventType.TRADE_RECEIVED,
                MaterialEventType.OWNERSHIP_TRANSFERRED)) {
            STAGE_BY_EVENT_TYPE.put(type, MaterialJourneyStage.TRANSACTION);
        }
        for (MaterialEventType type : List.of(
                MaterialEventType.CHILD_MATERIAL_CREATED,
                MaterialEventType.PROCESSING_REQUESTED,
                MaterialEventType.PROCESSING_REQUEST_CANCELED,
                MaterialEventType.PROCESSING_REQUEST_REJECTED,
                MaterialEventType.PROCESSING_REQUEST_EXPIRED,
                MaterialEventType.PROCESSING_QUOTE_SUBMITTED,
                MaterialEventType.PROCESSING_QUOTE_SELECTED,
                MaterialEventType.PROCESSING_JOB_PAID,
                MaterialEventType.PROCESSING_JOB_EXPIRED,
                MaterialEventType.PROCESSING_MATERIAL_SHIPPED,
                MaterialEventType.PROCESSING_MATERIAL_RECEIVED,
                MaterialEventType.PROCESSING_STARTED,
                MaterialEventType.PROCESSING_RESULT_SUBMITTED,
                MaterialEventType.PROCESSING_RESULT_REJECTED,
                MaterialEventType.PROCESSING_RESULT_APPROVED,
                MaterialEventType.PROCESSING_RESULT_CONFIRMED)) {
            STAGE_BY_EVENT_TYPE.put(type, MaterialJourneyStage.PROCESSING);
        }
        for (MaterialEventType type : List.of(
                MaterialEventType.PRODUCTION_INPUT_RESERVED,
                MaterialEventType.PRODUCTION_INPUT_REMOVED,
                MaterialEventType.PRODUCTION_BATCH_CANCELED,
                MaterialEventType.MATERIAL_CONSUMED)) {
            STAGE_BY_EVENT_TYPE.put(type, MaterialJourneyStage.PRODUCT_LINK);
        }
    }

    /**
     * {@link MaterialEventReferenceType} → {@link BlockchainReferenceType}.
     *
     * <p>둘이 갈라져 있는 이유는 {@code material_event.reference_type} 이 업무 레코드
     * 종류(잠금·판매글·요청 등)까지 세분화하는 반면, 온체인 기록은 네 도메인
     * (MATERIAL/TRADE/PROCESSING/PRODUCTION) 단위로만 남기 때문이다. 매핑이 없는
     * 참조(예: MATERIAL_LOCK, EVIDENCE_FILE)는 애초에 온체인 서명 대상이 아니라
     * {@code txHash} 를 붙일 수 없다 — 그 사건은 그냥 {@code null} 로 둔다.
     */
    private static final Map<MaterialEventReferenceType, BlockchainReferenceType>
            BLOCKCHAIN_REFERENCE_TYPE_BY_EVENT_REFERENCE_TYPE = Map.of(
                    MaterialEventReferenceType.MATERIAL, BlockchainReferenceType.MATERIAL,
                    MaterialEventReferenceType.MATERIAL_TRADE, BlockchainReferenceType.TRADE,
                    MaterialEventReferenceType.PROCESSING_JOB, BlockchainReferenceType.PROCESSING,
                    MaterialEventReferenceType.PRODUCTION_BATCH, BlockchainReferenceType.PRODUCTION);

    private final MaterialRepository materialRepository;
    private final MaterialEventRepository eventRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final UserWalletRepository walletRepository;
    private final BlockchainTransactionRepository blockchainTransactionRepository;

    @Transactional(readOnly = true)
    public MaterialJourneyResponse findJourney(Long materialId) {
        if (!materialRepository.existsById(materialId)) {
            throw new BusinessException(MaterialErrorCode.MATERIAL_NOT_FOUND);
        }

        List<MaterialEvent> events = eventRepository
                .findAllByMaterialIdOrderByOccurredAtAscIdAsc(materialId, Pageable.unpaged())
                .getContent();

        // 행위자 표시명·지갑은 한 번에 모아 읽는다. 이벤트마다 조회하면 타임라인 하나에
        // 수십 번의 왕복이 생긴다 — 소재 하나가 등록부터 제품 사용까지 거치는 사건 수만큼.
        List<Long> actorIds = events.stream()
                .map(MaterialEvent::getActorId)
                .filter(actorId -> actorId != null)
                .distinct()
                .toList();
        Map<Long, String> companyNames = companyProfileRepository.findAllById(actorIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        CompanyProfile::getUserId, CompanyProfile::getCompanyName));
        Map<Long, String> activeWallets = new HashMap<>();
        for (Long actorId : actorIds) {
            walletRepository.findByUserIdAndActiveTrue(actorId)
                    .ifPresent(wallet -> activeWallets.put(actorId, wallet.getWalletAddress()));
        }

        List<MaterialJourneyResponse.Entry> entries = events.stream()
                .map(event -> toEntry(event, companyNames, activeWallets))
                .toList();
        return new MaterialJourneyResponse(materialId, entries);
    }

    private MaterialJourneyResponse.Entry toEntry(
            MaterialEvent event, Map<Long, String> companyNames, Map<Long, String> activeWallets) {
        Long actorId = event.getActorId();
        return new MaterialJourneyResponse.Entry(
                event.getEventType(),
                STAGE_BY_EVENT_TYPE.get(event.getEventType()),
                actorId == null ? null : activeWallets.get(actorId),
                actorId == null ? null : companyNames.get(actorId),
                event.getQuantity(),
                event.getOccurredAt(),
                findTxHash(event),
                event.getDetail());
    }

    /**
     * 이 사건에 대응하는 온체인 트랜잭션이 있으면 그 해시를. 함수 이름을 가리지 않는다 —
     * 같은 대상에 여러 함수가 걸릴 수 있어(mint 확정·검증 등), 가장 최근 확정 기록을
     * "이 사건의 증빙"으로 본다. {@code UNCONFIRMED} 상태를 걸러내지 않는 이유는
     * {@link BlockchainTransactionRepository#findByReferenceTypeAndReferenceIdOrderByConfirmedAtDesc}
     * 가 상태와 무관하게 대상 기준으로만 찾기 때문이다 — 실패·대기 중인 기록의 해시도
     * "서명은 갔다"는 사실이라 보여줄 가치가 있다.
     */
    private String findTxHash(MaterialEvent event) {
        BlockchainReferenceType referenceType = BLOCKCHAIN_REFERENCE_TYPE_BY_EVENT_REFERENCE_TYPE
                .get(event.getReferenceType());
        if (referenceType == null || event.getReferenceId() == null) return null;
        List<BlockchainTransaction> transactions = blockchainTransactionRepository
                .findByReferenceTypeAndReferenceIdOrderByConfirmedAtDesc(
                        referenceType, event.getReferenceId());
        return transactions.isEmpty() ? null : transactions.get(0).getTxHash();
    }
}
