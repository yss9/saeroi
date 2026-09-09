package com.ssafy.upcycle.production.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialGenerationType;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockPurpose;
import com.ssafy.upcycle.material.entity.MaterialLockReferenceType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialStatus;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.production.ProductionErrorCode;
import com.ssafy.upcycle.production.dto.ProductionBatchOpenRequest;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.dto.ProductionInputRequest;
import com.ssafy.upcycle.production.dto.ProductionInputResponse;
import com.ssafy.upcycle.production.entity.ProductionBatch;
import com.ssafy.upcycle.production.entity.ProductionInput;
import com.ssafy.upcycle.production.entity.ProductionOutput;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
import com.ssafy.upcycle.production.repository.ProductionInputRepository;
import com.ssafy.upcycle.production.repository.ProductionOutputRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 생산 배치를 열고 소재를 연결한다. <b>확정은 여기 없다</b> — 별도 티켓이다.
 *
 * <h2>소재를 연결한다는 것은 잠그는 것이다</h2>
 *
 * <p>{@code production_input.material_lock_id} 가 {@code NOT NULL} 인 것이 이 서비스의
 * 뼈대다. 투입 행을 만들 때 반드시 {@code PRODUCTION} 목적의 잠금을 함께 건다. 잠그지
 * 않으면 같은 소재를 두 배치가 동시에 쓰고 <b>잔량이 음수</b>가 된다.
 *
 * <p>판매 등록({@code MARKETPLACE})·가공 요청({@code PROCESSING}) 이 같은 구조를 쓴다.
 * {@code PRODUCTION} 만 지금까지 아무도 쓰지 않았다.
 *
 * <h2>가용량은 보유량이 아니다</h2>
 *
 * <pre>
 *   available = balance.quantity - (만료되지 않은 ACTIVE 잠금 합계)
 * </pre>
 *
 * <p>보유량만 보고 허용하면 이미 판매 글에 걸어 둔 수량을 생산에도 넣을 수 있다. 판정은
 * <b>잠금 행을 함께 읽으면서</b> 한다 — {@code MaterialAvailability} 를 쓰지 않은 이유는
 * 그쪽이 잠금 없는 읽기라, 두 요청이 같은 스냅샷을 보고 둘 다 통과할 수 있기 때문이다.
 * 가공 요청이 같은 이유로 같은 방식을 쓴다 ({@code ProcessingRequestService}).
 */
@Service
@RequiredArgsConstructor
public class ProductionBatchService {

    private final ProductionBatchRepository batchRepository;
    private final ProductionInputRepository inputRepository;
    private final ProductionOutputRepository outputRepository;
    private final ProductionBatchCodeGenerator codeGenerator;
    private final ProductModelRepository productModelRepository;
    private final MaterialRepository materialRepository;
    private final MaterialBalanceRepository balanceRepository;
    private final MaterialLockRepository lockRepository;
    private final UserRepository userRepository;
    private final UserWalletRepository walletRepository;
    private final Clock clock;

    /**
     * 배치를 연다. <b>{@code DRAFT} 로 시작하고 투입은 아직 없다.</b>
     *
     * <p>서명 지갑을 지금 고정한다. 확정 때 다시 읽으면 그 사이 지갑을 바꾼 경우 온체인
     * 기록과 DB 가 어긋나고, 그 어긋남은 검증할 때만 드러난다.
     *
     * <p>코드는 저장 후 {@code id} 에서 파생한다 — 번호를 세면 두 요청이 같은 값을 읽는
     * 순간 UNIQUE 위반으로 터진다.
     */
    @Transactional
    public ProductionBatchResponse open(Long producerId, ProductionBatchOpenRequest request) {
        requireActiveBrand(producerId);
        // 행위자 자격을 먼저 다 본 뒤에 대상을 본다. 지갑이 없는 계정에게 "남의 제품이다"
        // 라고 답하면, 지갑을 연결해도 여전히 막힌다는 사실을 알 수 없다.
        String producerWallet = activeWalletAddress(producerId);
        requireOwnProductModel(request.productModelId(), producerId);

        ProductionBatch batch = batchRepository.save(ProductionBatch.open(
                codeGenerator.placeholder(),
                request.batchName(),
                request.productModelId(),
                request.producedQuantity(),
                producerId,
                producerWallet));
        // 임시 코드가 밖으로 새면 유일하지만 의미 없는 코드가 남는다.
        // 같은 트랜잭션 안에서 교체하는 것이 계약이다.
        batch.assignCode(codeGenerator.of(batch.getId()));

        return ProductionBatchResponse.of(batch, List.of());
    }

    /**
     * 소재를 배치에 연결한다. <b>이 티켓의 핵심이다.</b>
     *
     * <p>순서가 중요하다 — <b>검증을 모두 끝낸 뒤에 처음으로 무엇인가를 바꾼다.</b> 잠금을
     * 먼저 걸고 나중에 거절하면 풀어 줘야 하고, 그 풀기가 실패하면 아무도 쓰지 않는 잠금이
     * 남아 소재가 영영 묶인다.
     */
    @Transactional
    public ProductionBatchResponse addInput(
            Long producerId, Long batchId, ProductionInputRequest request) {
        requireActiveBrand(producerId);
        ProductionBatch batch = editableBatch(batchId, producerId);
        requireQuantitiesAddUp(request);
        Material material = activeMaterial(request.materialId());
        // 소유를 자격보다 먼저 본다. 가진 적도 없는 소재에게 "가공을 거쳐야 한다" 고
        // 답하면, 그 소재를 가공하면 쓸 수 있다는 뜻으로 읽힌다.
        requireOwned(material.getId(), producerId);
        requireEligible(material);
        requireNotAlreadyInput(batchId, material.getId());
        requireAvailable(material.getId(), producerId, request.inputQuantity());

        // 여기부터 변경. 잠금을 먼저 만들어 id 를 얻어야 투입 행이 그것을 가리킬 수 있다.
        MaterialLock lock = lockRepository.save(MaterialLock.create(
                material.getId(),
                producerId,
                request.inputQuantity(),
                MaterialLockPurpose.PRODUCTION,
                MaterialLockReferenceType.PRODUCTION_BATCH,
                batch.getId(),
                // 만료를 두지 않는다. 생산은 브랜드가 스스로 확정하거나 취소하는 절차이고,
                // 기한이 지나 잠금이 풀리면 확정 시점에 소재가 사라진 배치가 된다.
                null));

        ProductionInput input = inputRepository.save(ProductionInput.record(
                batch.getId(),
                material.getId(),
                request.inputQuantity(),
                request.productUsedQuantity(),
                request.reusableScrapQuantity(),
                request.wasteQuantity(),
                request.wasteReason(),
                lock.getId()));

        // 자투리가 남는다면 확정 때 새 소재가 될 몫을 지금 예정해 둔다.
        // 확정 로직은 이 행에 발행된 소재를 연결하기만 하면 된다.
        if (input.hasReusableScrap()) {
            outputRepository.save(
                    ProductionOutput.expect(input.getId(), input.getReusableScrapQuantity()));
        }

        return findDetail(producerId, batchId);
    }

    /**
     * 투입을 취소한다. <b>잠금을 함께 푼다.</b>
     *
     * <p>잠금을 풀지 않으면 소재가 어떤 배치에도 쓰이지 않으면서 영영 묶인다 — 사용자
     * 입장에서는 "분명 취소했는데 수량이 돌아오지 않는" 상태다.
     *
     * <p>예정해 둔 자투리 산출도 함께 지운다. 투입이 없어졌는데 그 자투리만 남으면 확정
     * 로직이 부모 없는 산출을 만나게 된다.
     */
    @Transactional
    public ProductionBatchResponse removeInput(Long producerId, Long batchId, Long inputId) {
        requireActiveBrand(producerId);
        editableBatch(batchId, producerId);

        ProductionInput input = inputRepository.findById(inputId)
                .filter(found -> found.getProductionBatchId().equals(batchId))
                .orElseThrow(
                        () -> new BusinessException(ProductionErrorCode.PRODUCTION_INPUT_NOT_FOUND));

        outputRepository.deleteAll(outputRepository.findAllByProductionInputId(input.getId()));
        inputRepository.delete(input);
        // 잠금이 이미 풀렸거나 소비된 경우까지 되돌리지는 않는다 — release 가 상태를 검사해
        // 거절하고, 그건 데이터가 어긋났다는 신호라 조용히 넘어가면 안 된다.
        lockRepository.findById(input.getMaterialLockId())
                .ifPresent(lock -> lock.release(LocalDateTime.now(clock)));

        return findDetail(producerId, batchId);
    }

    /** 배치와 투입 목록. 화면이 둘을 같이 보여주므로 한 번에 준다. */
    @Transactional(readOnly = true)
    public ProductionBatchResponse findDetail(Long producerId, Long batchId) {
        ProductionBatch batch = batchRepository.findById(batchId)
                .orElseThrow(
                        () -> new BusinessException(ProductionErrorCode.PRODUCTION_BATCH_NOT_FOUND));
        requireOwner(batch, producerId);
        return ProductionBatchResponse.of(batch, inputsOf(batchId));
    }

    /** 내 배치 목록. */
    @Transactional(readOnly = true)
    public List<ProductionBatchResponse> findMyBatches(Long producerId) {
        return batchRepository.findAllByProducerId(producerId).stream()
                .map(batch -> ProductionBatchResponse.of(batch, inputsOf(batch.getId())))
                .toList();
    }

    private List<ProductionInputResponse> inputsOf(Long batchId) {
        return inputRepository.findAllByProductionBatchId(batchId).stream()
                .map(ProductionInputResponse::from)
                .toList();
    }

    /**
     * 투입을 바꿀 수 있는 배치인가.
     *
     * <p>{@code SIGNING} 이후로는 막는다 — 서명한 내용과 저장된 내용이 달라지면 온체인
     * 기록이 무엇을 증명하는지 알 수 없게 된다.
     */
    private ProductionBatch editableBatch(Long batchId, Long producerId) {
        ProductionBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(
                        () -> new BusinessException(ProductionErrorCode.PRODUCTION_BATCH_NOT_FOUND));
        requireOwner(batch, producerId);
        if (!batch.isEditable()) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_BATCH_NOT_EDITABLE);
        }
        return batch;
    }

    private void requireOwner(ProductionBatch batch, Long producerId) {
        if (!batch.getProducerId().equals(producerId)) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_FORBIDDEN);
        }
    }

    /**
     * 살아 있는 소재인가.
     *
     * <p><b>존재 여부를 감추지 않는다.</b> {@code GET /api/materials/{id}} 가 인증만 되면
     * 누구에게나 열려 있어 (소재 정보는 추적성이 목적이라 누가 보든 같은 값이다) 여기서
     * 감출 것이 없고, 감추면 오타를 낸 사용자의 안내만 나빠진다.
     */
    private Material activeMaterial(Long materialId) {
        return materialRepository.findById(materialId)
                .filter(found -> found.getStatus() == MaterialStatus.ACTIVE)
                .orElseThrow(
                        () -> new BusinessException(ProductionErrorCode.PRODUCTION_MATERIAL_NOT_FOUND));
    }

    /**
     * 투입할 수 있는 소재인가.
     *
     * <p>ERD 의 투입 자격 — {@code NOT (WASTE AND INITIAL)}. 수거한 그대로의 폐소재는 제품이
     * 될 수 없고 Processing 을 최소 1회 거쳐야 한다. 막지 않으면 <b>가공 이력이 없는 제품이
     * 업사이클로 팔린다.</b>
     */
    private void requireEligible(Material material) {
        if (material.getRootOrigin() == MaterialRootOrigin.WASTE
                && material.getGenerationType() == MaterialGenerationType.INITIAL) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_MATERIAL_NOT_ELIGIBLE);
        }
    }

    /**
     * 보유한 소재인가. <b>수량 부족과 구분한다.</b>
     *
     * <p>{@code material_balance} 행의 <b>존재</b>가 소유의 근거다. 수량이 0 이어도 행이 있으면
     * 보유한 것이고, 그때의 정직한 답은 "수량이 부족하다" 다 — 전량을 소비한 소재가 그렇다.
     *
     * <p>둘을 하나로 합치면 "수량이 부족하다" 는 안내를 받은 사용자가 수량을 줄여 가며 다시
     * 시도하는데, 가진 적이 없는 소재는 아무리 줄여도 통과하지 못한다. 판매 등록이 같은
     * 이유로 같은 구분을 한다 ({@code ListingCommandService#requireSellableQuantity}).
     *
     * <p><b>소재의 등록자가 아니라 보유자를 본다.</b> 소재는 거래로 주인이 바뀌므로
     * {@code material.registrant_id} 는 지금 누가 가졌는지 답하지 못한다.
     */
    private void requireOwned(Long materialId, Long ownerId) {
        if (balanceRepository.findByMaterialIdAndOwnerId(materialId, ownerId).isEmpty()) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_MATERIAL_NOT_OWNED);
        }
    }

    /**
     * 투입량 셋의 합이 총 투입량과 맞는가.
     *
     * <p>엔티티도 같은 검사를 하지만 그쪽은 {@code IllegalArgumentException} 이라 그대로 두면
     * 500 이 나간다. 사용자가 고칠 수 있는 입력 오류라 여기서 먼저 걸러 400 을 만들고,
     * 엔티티 검사는 최후 방어선으로 남긴다.
     */
    private void requireQuantitiesAddUp(ProductionInputRequest request) {
        long parts = request.productUsedQuantity()
                + request.reusableScrapQuantity()
                + request.wasteQuantity();
        if (request.inputQuantity() != parts) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_INPUT_QUANTITY_MISMATCH);
        }
    }

    private void requireNotAlreadyInput(Long batchId, Long materialId) {
        if (inputRepository.existsByProductionBatchIdAndMaterialId(batchId, materialId)) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_MATERIAL_ALREADY_INPUT);
        }
    }

    /**
     * 가용 수량이 충분한가.
     *
     * <p>보유 행과 잠금 행을 <b>둘 다 잠그고</b> 읽는다. 잠금 없이 읽으면 두 요청이 같은
     * 스냅샷을 보고 각자 통과해 잔량을 넘겨 잠글 수 있다 — MySQL 의 REPEATABLE READ 에서는
     * 특히 그렇다.
     *
     * <p>만료된 잠금은 빼지 않는다. 도메인 규칙이 <b>"전환 전이라도 available 계산에서
     * 제외"</b> 라고 못 박았고, 상태를 {@code EXPIRED} 로 바꾸는 배치가 아직 없어 기한이 지난
     * 잠금이 {@code ACTIVE} 로 남아 있다. 그걸 세면 이미 풀린 수량을 영영 쓰지 못한다.
     */
    private void requireAvailable(Long materialId, Long ownerId, long wanted) {
        // 여기 도달했다면 requireOwned 가 보유 행을 확인한 뒤다. 그래도 없으면 그 사이에
        // 사라졌다는 뜻이고, 그건 수량 문제가 아니라 소유 문제다.
        long available = balanceRepository.findForUpdate(materialId, ownerId)
                .orElseThrow(() ->
                        new BusinessException(ProductionErrorCode.PRODUCTION_MATERIAL_NOT_OWNED))
                .getQuantity();
        LocalDateTime now = LocalDateTime.now(clock);
        for (MaterialLock lock : lockRepository.findActiveForUpdate(materialId, ownerId)) {
            if (!lock.isExpiredAt(now)) {
                available -= lock.getAmount();
            }
        }
        if (wanted > available) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_INSUFFICIENT_BALANCE);
        }
    }

    /**
     * 남의 제품을 생산할 수 없다.
     *
     * <p>역할만 보면 브랜드 아무나 다른 브랜드의 제품 이름으로 배치를 열 수 있고, 그 배치가
     * 확정되면 남의 제품 재고가 늘어난다.
     */
    private void requireOwnProductModel(Long productModelId, Long producerId) {
        ProductModel model = productModelRepository.findById(productModelId)
                .orElseThrow(() -> new BusinessException(
                        ProductionErrorCode.PRODUCTION_PRODUCT_MODEL_NOT_FOUND));
        if (!model.getBrandId().equals(producerId)) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_PRODUCT_MODEL_FORBIDDEN);
        }
    }

    /**
     * 생산 자격을 확인한다.
     *
     * <p>JWT 의 {@code roles} 클레임을 믿지 않고 DB 를 다시 읽는다. 토큰은 발급 시점의
     * 스냅샷이라, 정지되거나 역할이 바뀐 계정이 만료 전까지 계속 생산할 수 있다. 생산 확정은
     * 소재를 소비하는 되돌릴 수 없는 행위다.
     */
    private void requireActiveBrand(Long producerId) {
        User user = userRepository.findById(producerId)
                .orElseThrow(() -> new BusinessException(
                        ProductionErrorCode.PRODUCTION_PRODUCER_NOT_FOUND));
        if (!user.isActive()) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_PRODUCER_SUSPENDED);
        }
        if (user.getRole() != UserRole.BRAND) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_ROLE_NOT_ALLOWED);
        }
    }

    /** 확정 시 서명할 주소. 배치를 여는 시점에 고정한다. */
    private String activeWalletAddress(Long producerId) {
        return walletRepository.findByUserIdAndActiveTrue(producerId)
                .map(wallet -> wallet.getWalletAddress())
                .orElseThrow(() -> new BusinessException(
                        ProductionErrorCode.PRODUCTION_WALLET_NOT_LINKED));
    }
}
