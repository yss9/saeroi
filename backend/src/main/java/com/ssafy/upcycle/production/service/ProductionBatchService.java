package com.ssafy.upcycle.production.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.storage.FileReference;
import com.ssafy.upcycle.common.storage.FileStorageErrorCode;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.common.storage.StoredFile;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialGenerationType;
import com.ssafy.upcycle.material.entity.MaterialEventReferenceType;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockPurpose;
import com.ssafy.upcycle.material.entity.MaterialLockReferenceType;
import com.ssafy.upcycle.material.entity.MaterialLockStatus;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialStatus;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.material.service.MaterialAvailabilityPolicy;
import com.ssafy.upcycle.material.service.MaterialEventRecorder;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.service.ProductQrCodeGenerator;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.production.ProductionErrorCode;
import com.ssafy.upcycle.production.dto.ProductionBatchOpenRequest;
import com.ssafy.upcycle.production.dto.ProductionBatchProductModelRequest;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.dto.ProductionInputRequest;
import com.ssafy.upcycle.production.dto.ProductionInputsRequest;
import com.ssafy.upcycle.production.dto.ProductionItemResponse;
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final ProductItemRepository productItemRepository;
    private final ProductQrCodeGenerator qrCodeGenerator;
    private final FileStorageService fileStorageService;
    private final MaterialRepository materialRepository;
    private final MaterialBalanceRepository balanceRepository;
    private final MaterialLockRepository lockRepository;
    private final UserRepository userRepository;
    private final UserWalletRepository walletRepository;
    private final Clock clock;
    private final MaterialEventRecorder eventRecorder;

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
        // 제품은 선택이다. 보냈을 때만 소유를 본다 — 나중에 지정해도 같은 검사를 거친다.
        if (request.productModelId() != null) {
            requireOwnProductModel(request.productModelId(), producerId);
        }

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
     * 무엇을 만들지 나중에 정한다. <b>{@code DRAFT} 일 때만 된다.</b>
     *
     * <p>배치를 열 때 제품을 비워 둠 수 있게 하면서 함께 생긴 경로다. 설정해 둔 것을
     * 바꾸는 것도 같은 경로로 한다 — 잘못 고른 제품을 고치려고 배치를 다시 여는 것은
     * 이미 담아 둔 투입을 버리는 일이다.
     *
     * <p>{@link #editableBatch} 를 그대로 쓴다 — 소유자 확인과 "서명 전인가" 를 한 곳에서
     * 보기 때문이다. 서명이 시작된 뒤에 제품이 바뀜면 서명한 내용과 저장된 내용이 어긋난다.
     *
     * <p>자기 브랜드의 제품인지는 여는 시점과 똑같은 검사를 거친다 — 단계가 나뉘었다고
     * 느슬해지면 남의 제품을 내 배치에 붙일 수 있게 된다.
     */
    @Transactional
    public ProductionBatchResponse assignProductModel(
            Long producerId, Long batchId, ProductionBatchProductModelRequest request) {
        requireActiveBrand(producerId);
        // editableBatch 를 쓰지 않는다 — 그쪽은 SIGNING 이후를 전부 막는데,
        // 제품은 확정된 뒤에 붙이는 것이 정상 순서다. 상태별 허용 판정은 엔티티가 한다.
        ProductionBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(
                        () -> new BusinessException(ProductionErrorCode.PRODUCTION_BATCH_NOT_FOUND));
        requireOwner(batch, producerId);
        requireAssignableStatus(batch);
        // 확정된 배치의 제품은 한 번만 붙는다. 엔티티도 같은 것을 막지만 그쪽은
        // IllegalStateException 이라 그대로 두면 500 이 나간다 — 여기가 사용자에게 답하는 자리다.
        if (batch.isConfirmed() && batch.getProductModelId() != null) {
            throw new BusinessException(
                    ProductionErrorCode.PRODUCTION_PRODUCT_MODEL_ALREADY_SET);
        }
        requireOwnProductModel(request.productModelId(), producerId);

        batch.assignProductModel(request.productModelId());
        // 확정된 배치라면 개체가 이미 만들어져 있다. 같은 트랜잭션에서 함께 채워야
        // 배치는 제품을 가졌는데 그 산출물은 아닌 상태가 생기지 않는다.
        productItemRepository.findAllByProductionBatchId(batchId)
                .forEach(item -> item.attachProductModel(request.productModelId()));
        return ProductionBatchResponse.of(batch, inputsOf(batchId));
    }

    /**
     * 제품을 붙일 수 있는 상태인가.
     *
     * <p>{@code DRAFT} 는 언제든 된다 — 개체가 아직 없어 고쳐도 잉힐 것이 없다.
     * {@code CONFIRMED} 도 된다 — 그것이 바로 이번에 열어 준 경로다. 다만 한 번 붙인
     * 뒤에는 바꿀 수 없고, 그 판정은 {@code ProductionBatch} 와 {@code ProductItem} 이 한다.
     *
     * <p>{@code SIGNING} 을 막는 이유는 따로 있다 — 서명하는 중에 내용이 바뀌면 서명한
     * 것과 저장된 것이 어긋난다. {@code CANCELED} 는 더 만질 대상이 아니다.
     */
    private void requireAssignableStatus(ProductionBatch batch) {
        if (!batch.isEditable() && !batch.isConfirmed()) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_BATCH_NOT_EDITABLE);
        }
    }

    /**
     * 소재를 배치에 연결한다. <b>전부 성공하거나 전부 실패한다.</b>
     *
     * <p>순서가 중요하다 — <b>목록 전체를 검증한 뒤에 처음으로 무엇인가를 바꾼다.</b>
     * 한 건씩 처리하면 세 번째에서 거절될 때 앞의 둘이 이미 잠긴 채 남고, 사용자는 화면에서
     * 실패를 봤는데 소재는 묶여 있는 상태가 된다. 트랜잭션이 롤백해 주지만 그건 마지막
     * 방어선이고, 애초에 거절할 요청을 반쯤 처리하다 되돌리는 것과 시작 전에 거절하는 것은
     * 다르다 — 증빙 업로드가 같은 이유로 같은 순서를 쓴다.
     *
     * <p>가용량은 소재별로 본다. 같은 소재가 요청 안에 두 번 들어오는 것은
     * {@link #requireNoDuplicateMaterial} 이 먼저 막으므로, 한 소재의 가용량을 두 항목이
     * 나눠 쓰는 경우는 생기지 않는다.
     */
    @Transactional
    public ProductionBatchResponse addInputs(
            Long producerId, Long batchId, ProductionInputsRequest request) {
        requireActiveBrand(producerId);
        ProductionBatch batch = editableBatch(batchId, producerId);

        List<Material> materials = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (ProductionInputRequest each : request.inputs()) {
            requireQuantitiesAddUp(each);
            Material material = activeMaterial(each.materialId());
            requireNoDuplicateMaterial(seen, material.getId());
            // 소유를 자격보다 먼저 본다. 가진 적도 없는 소재에게 "가공을 거쳐야 한다" 고
            // 답하면, 그 소재를 가공하면 쓸 수 있다는 뜻으로 읽힌다.
            requireOwned(material.getId(), producerId);
            requireEligible(material);
            requireNotAlreadyInput(batchId, material.getId());
            requireAvailable(material.getId(), producerId, each.inputQuantity());
            materials.add(material);
        }

        // ── 여기까지 검증. 아래에서 처음으로 무엇인가를 바꾼다 ──

        for (int i = 0; i < request.inputs().size(); i++) {
            link(batch, materials.get(i), producerId, request.inputs().get(i));
        }
        return findDetail(producerId, batchId);
    }

    /**
     * 소재 한 건을 배치에 붙인다. 잠금 → 투입 → (자투리가 있으면) 산출 예정 순이다.
     *
     * <p>잠금을 먼저 만들어야 id 를 얻고, 투입 행이 그것을 가리킬 수 있다.
     */
    private void link(
            ProductionBatch batch,
            Material material,
            Long producerId,
            ProductionInputRequest request) {
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
        eventRecorder.record(
                material.getId(), MaterialEventType.PRODUCTION_INPUT_RESERVED, producerId,
                MaterialEventReferenceType.PRODUCTION_INPUT, input.getId(), null,
                input.getInputQuantity(), "생산 배치 투입 예약",
                MaterialEventRecorder.key(
                        MaterialEventType.PRODUCTION_INPUT_RESERVED,
                        material.getId(), input.getId()));
    }

    /**
     * 한 요청 안에서 같은 소재가 겹치지 않는가.
     *
     * <p>DB 의 UNIQUE 는 두 번째 INSERT 에서야 걸리는데, 그때는 첫 번째 잠금이 이미 만들어진
     * 뒤다. 쓰기 전에 걸러야 그 경로가 아예 없어진다.
     */
    private void requireNoDuplicateMaterial(Set<Long> seen, Long materialId) {
        if (!seen.add(materialId)) {
            throw new BusinessException(
                    ProductionErrorCode.PRODUCTION_DUPLICATE_MATERIAL_IN_REQUEST);
        }
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
        eventRecorder.record(
                input.getMaterialId(), MaterialEventType.PRODUCTION_INPUT_REMOVED, producerId,
                MaterialEventReferenceType.PRODUCTION_INPUT, input.getId(), null,
                input.getInputQuantity(), "생산 배치 투입 제거 및 수량 해제",
                MaterialEventRecorder.key(
                        MaterialEventType.PRODUCTION_INPUT_REMOVED,
                        input.getMaterialId(), input.getId()));

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

    /**
     * 투입 목록에 소재 이름을 붙인다.
     *
     * <p>투입마다 소재를 다시 읽으면 그게 N+1 이다. 한 번에 읽어 붙인다 — 배치 하나의
     * 투입은 많아야 스무 건이라 {@code findAllById} 한 번으로 끝난다.
     */
    private List<ProductionInputResponse> inputsOf(Long batchId) {
        List<ProductionInput> inputs = inputRepository.findAllByProductionBatchId(batchId);
        Map<Long, Material> materials = materialRepository
                .findAllById(inputs.stream().map(ProductionInput::getMaterialId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Material::getId, material -> material));
        // 잠금도 한 번에 읽는다. 투입마다 다시 읽으면 소재와 같은 이유로 N+1 이다.
        Map<Long, MaterialLock> locks = lockRepository
                .findAllById(inputs.stream().map(ProductionInput::getMaterialLockId).toList())
                .stream()
                .collect(Collectors.toMap(MaterialLock::getId, lock -> lock));
        return inputs.stream()
                .map(input -> ProductionInputResponse.of(
                        input,
                        materials.get(input.getMaterialId()),
                        locks.get(input.getMaterialLockId())))
                .toList();
    }

    /**
     * 확정으로 만들어진 개체 목록. <b>QR 라벨 인쇄가 쓴다.</b>
     *
     * <p>{@code qrToken} 이 실려 나가므로 <b>배치 소유자만</b> 볼 수 있다. 그 토큰은 인증
     * 없이 열리는 조회 경로의 열쇠라, 남에게 주면 남의 제품 이력을 열람할 수 있다.
     *
     * <p>확정 전 배치는 빈 목록이다 — 오류가 아니라 사실이다. 개체는 확정이 만든다.
     */
    @Transactional(readOnly = true)
    public List<ProductionItemResponse> findItems(Long producerId, Long batchId) {
        ProductionBatch batch = batchRepository.findById(batchId)
                .orElseThrow(
                        () -> new BusinessException(ProductionErrorCode.PRODUCTION_BATCH_NOT_FOUND));
        requireOwner(batch, producerId);
        return productItemRepository.findAllByProductionBatchId(batchId).stream()
                .sorted(Comparator.comparingInt(ProductItem::getSerialNumber))
                .map(item -> ProductionItemResponse.of(item, qrCodeGenerator.urlFor(item.getQrToken())))
                .toList();
    }

    /**
     * 개체의 QR 이미지. <b>라벨 인쇄가 쓴다.</b>
     *
     * <p>개체를 {@code qrToken} 이 아니라 id 로 받는다. 토큰으로 받으면 경로가 공개 열쇠를
     * 그대로 드러내고, <b>아무나 남의 QR 이미지를 만들어낼 수 있다.</b> 배치 소유자만 자기
     * 개체의 라벨을 뽑는다.
     *
     * <p>개체가 그 배치의 것인지 따로 본다. 소유자 검사를 통과했더라도 개체 번호만 믿으면
     * 남의 배치 개체 라벨을 받아 갈 수 있다.
     *
     * <p><b>기본 크기(size 생략)만 저장소에 캐시한다(S15P21E104-141).</b> 커스텀 크기는
     * 요청마다 새로 그린다 — 크기별로 캐시하면 키가 갈리고 무효화 로직이 따로 필요해지는데,
     * 라벨 인쇄가 실제로 쓰는 것은 기본 크기뿐이다. 쓰기가 섞여 들어오므로 더 이상
     * {@code readOnly} 트랜잭션이 아니다.
     */
    @Transactional
    public byte[] qrImage(Long producerId, Long batchId, Long productItemId, Integer size) {
        ProductionBatch batch = batchRepository.findById(batchId)
                .orElseThrow(
                        () -> new BusinessException(ProductionErrorCode.PRODUCTION_BATCH_NOT_FOUND));
        requireOwner(batch, producerId);

        ProductItem item = productItemRepository.findById(productItemId)
                .filter(found -> found.getProductionBatchId().equals(batchId))
                .orElseThrow(
                        () -> new BusinessException(ProductionErrorCode.PRODUCTION_ITEM_NOT_FOUND));
        try {
            return size == null ? cachedDefaultQrImage(item) : qrCodeGenerator.pngFor(item.getQrToken(), size);
        } catch (IllegalArgumentException invalidSize) {
            // 생성 모듈은 IllegalArgumentException 을 던진다. 그대로 두면 500 이 나가는데,
            // 크기는 사용자가 고칠 수 있는 입력이다.
            throw new BusinessException(ProductionErrorCode.PRODUCTION_QR_SIZE_INVALID);
        }
    }

    /**
     * 기본 크기 QR PNG 를 한 번만 그려 저장소에 남기고, 다음부터는 그 바이트를 그대로
     * 돌려준다.
     *
     * <p>같은 토큰으로 다시 그려도 PNG 바이트는 항상 같으므로(결정적 렌더링), 매 요청 렌더링은
     * 순수한 낭비다 — 배치 하나에 수십 개체, 라벨 인쇄는 여러 번 반복된다.
     *
     * <p>저장소에 키는 있는데 실제 객체가 지워진 경우(수동 정리 등) {@code FILE_NOT_FOUND} 를
     * 그대로 사용자에게 보이면 복구할 방법이 없어 보인다. 다시 그려 저장하는 편이 낫다.
     */
    private byte[] cachedDefaultQrImage(ProductItem item) {
        String existingKey = item.getQrImageKey();
        if (existingKey != null) {
            try (InputStream stream = fileStorageService.read(existingKey).stream()) {
                return stream.readAllBytes();
            } catch (BusinessException missing) {
                if (missing.getErrorCode() != FileStorageErrorCode.FILE_NOT_FOUND) {
                    throw missing;
                }
                // 행은 있는데 객체가 없다 — 아래에서 다시 그려 저장한다.
            } catch (IOException unreadable) {
                throw new BusinessException(FileStorageErrorCode.FILE_STORAGE_FAILED);
            }
        }

        byte[] png = qrCodeGenerator.pngFor(item.getQrToken());
        StoredFile stored = fileStorageService.store(
                new FileReference("PRODUCT_QR", item.getId()),
                item.getQrToken() + ".png",
                "image/png",
                new ByteArrayInputStream(png),
                png.length);
        item.attachQrImage(stored.storageKey());
        return png;
    }

    /**
     * 배치를 취소한다. <b>{@code DRAFT} 에서만, 남은 잠금을 함께 푼다.</b>
     *
     * <p>잘못 연 배치를 정리할 방법이 없으면 목록에 영원히 쌓이고, 그 배치가 잡고 있는
     * 소재도 계속 묶인다 — 투입을 한 건씩 빼면 되지만 그건 사용자가 할 일이 아니다.
     *
     * <p>투입 행은 지우지 않는다. "이런 소재를 넣으려다 취소했다" 는 것도 이력이고,
     * 취소된 배치는 되살아나지 않으므로 그 행이 다시 쓰이지 않는다.
     *
     * <p>확정된 배치는 취소할 수 없다 — 소재는 이미 소비됐고 개체는 이미 만들어졌다.
     * 그걸 되돌리는 일은 취소가 아니라 별도의 보정이다.
     */
    @Transactional
    public ProductionBatchResponse cancel(Long producerId, Long batchId) {
        requireActiveBrand(producerId);
        ProductionBatch batch = editableBatch(batchId, producerId);

        LocalDateTime now = LocalDateTime.now(clock);
        for (MaterialLock lock : lockRepository.findByReferenceForUpdate(
                MaterialLockReferenceType.PRODUCTION_BATCH, batchId)) {
            // 이미 풀렸거나 소비된 잠금은 건너뛴다. release 가 상태를 검사해 던지는데,
            // 취소 경로에서 그건 사용자가 할 수 있는 일이 없는 실패다.
            if (lock.getStatus() == MaterialLockStatus.ACTIVE) {
                lock.release(now);
            }
            eventRecorder.record(
                    lock.getMaterialId(), MaterialEventType.PRODUCTION_BATCH_CANCELED, producerId,
                    MaterialEventReferenceType.PRODUCTION_BATCH, batchId, null,
                    lock.getAmount(), "생산 배치 취소 및 수량 해제",
                    MaterialEventRecorder.key(
                            MaterialEventType.PRODUCTION_BATCH_CANCELED,
                            lock.getMaterialId(), batchId));
        }
        batch.cancel();

        return ProductionBatchResponse.of(batch, inputsOf(batchId));
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
        long balance = balanceRepository.findForUpdate(materialId, ownerId)
                .orElseThrow(() ->
                        new BusinessException(ProductionErrorCode.PRODUCTION_MATERIAL_NOT_OWNED))
                .getQuantity();
        // 규칙은 MaterialAvailabilityPolicy 가, 읽는 방식은 여기가 정한다.
        // 판매 등록과 같은 답을 해야 하고, 그것을 MaterialAvailabilityConsistencyTests 가 고정한다.
        long available = MaterialAvailabilityPolicy.available(
                balance,
                lockRepository.findActiveForUpdate(materialId, ownerId),
                LocalDateTime.now(clock));
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
