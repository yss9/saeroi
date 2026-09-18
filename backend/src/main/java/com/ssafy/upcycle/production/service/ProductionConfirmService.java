package com.ssafy.upcycle.production.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialEventReferenceType;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.entity.MaterialLineageRelationType;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockStatus;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.material.service.MaterialCodeGenerator;
import com.ssafy.upcycle.material.service.MaterialIssuer;
import com.ssafy.upcycle.material.service.MaterialLineageRegistrar;
import com.ssafy.upcycle.material.service.MaterialProcessHistoryResolver;
import com.ssafy.upcycle.material.service.MaterialEventRecorder;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.service.ProductQrTokenGenerator;
import com.ssafy.upcycle.production.ProductionErrorCode;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.entity.ProductionBatch;
import com.ssafy.upcycle.production.entity.ProductionInput;
import com.ssafy.upcycle.production.entity.ProductionOutput;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
import com.ssafy.upcycle.production.repository.ProductionInputRepository;
import com.ssafy.upcycle.production.repository.ProductionOutputRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 생산 배치 확정. <b>이 프로젝트에서 가장 무거운 트랜잭션이다.</b>
 *
 * <p>한 번에 다섯 가지가 일어난다.
 *
 * <ol>
 *   <li>투입 잠금을 {@code CONSUMED} 로 바꾸고 보유량을 차감한다</li>
 *   <li>{@code product_item} 을 생산 개수만큼 만든다 (QR 토큰 발급)</li>
 *   <li>업사이클 비율을 계산해 배치에 스냅샷으로 굳힌다</li>
 *   <li>자투리를 새 소재로 발행한다 ({@code PRODUCTION_SCRAP})</li>
 *   <li>새 소재와 부모를 {@code material_lineage} 로 잇는다</li>
 * </ol>
 *
 * <h2>검증을 모두 끝낸 뒤에 처음으로 무엇인가를 바꾼다</h2>
 *
 * <p>다섯 가지가 여러 테이블에 걸쳐 있어서, 중간에 실패하면 <b>소재는 소비됐는데 개체는
 * 없는</b> 상태가 만들어질 수 있다. 트랜잭션이 롤백해 주지만 그건 마지막 방어선이고,
 * 애초에 거절할 요청을 반쯤 처리하다 되돌리는 것과 시작 전에 거절하는 것은 다르다 —
 * 거래 수령 확인이 같은 순서를 쓴다 ({@code TradeReceiptService}).
 *
 * <h2>되돌릴 수 없다</h2>
 *
 * <p>확정된 배치는 취소할 수 없다. 소재는 이미 소비됐고 개체는 이미 만들어졌으며, 그
 * 개체에는 QR 이 붙어 소비자에게 나간다. 그래서 배치 행을 {@code findByIdForUpdate} 로
 * 잠그고 상태를 확인한다 — 두 요청이 동시에 통과하면 <b>개체가 두 배</b>로 만들어지고
 * 소재는 두 번 차감된다.
 */
@Service
@RequiredArgsConstructor
public class ProductionConfirmService {

    /**
     * 폐소재가 제품이 되기 전에 반드시 거쳐야 하는 정제 공정.
     *
     * <p>세 가지를 한 자리에 모아 둔다 — 규칙이 바뀌면 여기만 고친다. 검사하는 곳과
     * 오류 메시지 둘로 훩어지면 한쪽만 바뀔 때 안내가 거짓말이 된다.
     */
    private static final Set<ProcessType> REQUIRED_PROCESS_TYPES = EnumSet.of(
            ProcessType.CLEANING, ProcessType.DISASSEMBLY, ProcessType.SORTING);

    private final ProductionBatchRepository batchRepository;
    private final ProductionInputRepository inputRepository;
    private final ProductionOutputRepository outputRepository;
    private final ProductItemRepository productItemRepository;
    private final ProductQrTokenGenerator qrTokenGenerator;
    private final MaterialRepository materialRepository;
    private final MaterialBalanceRepository balanceRepository;
    private final MaterialLockRepository lockRepository;
    private final MaterialCodeGenerator materialCodeGenerator;
    private final MaterialLineageRegistrar lineageRegistrar;
    private final MaterialProcessHistoryResolver processHistoryResolver;
    private final MaterialIssuer materialIssuer;
    private final UserRepository userRepository;
    private final Clock clock;
    private final MaterialEventRecorder eventRecorder;

    /**
     * 배치를 확정한다.
     *
     * <p><b>{@code DRAFT} 에서 한 번에 {@code CONFIRMED} 까지 간다.</b> 상태 기계에는
     * {@code SIGNING} 이 있지만 지금은 그 창이 열리지 않는다 — 온체인 발행이 아직 없어
     * ({@code app.material.on-chain=false}) 서버가 기다릴 서명이 없기 때문이다. 컨트랙트가
     * 붙으면 이 메서드가 둘로 갈라진다: 서명 요청을 만들고 {@code SIGNING} 으로 두는 쪽과,
     * 영수증을 받아 확정하는 쪽이다. 상태를 미리 만들어 둔 이유가 그것이다.
     */
    @Transactional
    public ProductionBatchResponse confirm(Long producerId, Long batchId) {
        requireActiveBrand(producerId);

        // 배치 행을 잠근다. 두 요청이 동시에 통과하면 개체가 두 배로 만들어진다.
        ProductionBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(
                        () -> new BusinessException(ProductionErrorCode.PRODUCTION_BATCH_NOT_FOUND));
        if (!batch.getProducerId().equals(producerId)) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_FORBIDDEN);
        }
        if (!batch.isEditable()) {
            // 이미 확정됐거나 서명 중이거나 취소된 배치다.
            throw new BusinessException(ProductionErrorCode.PRODUCTION_BATCH_NOT_EDITABLE);
        }

        List<ProductionInput> inputs = inputRepository.findAllByProductionBatchId(batchId);
        if (inputs.isEmpty()) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_NO_INPUT);
        }

        // ── 여기까지 검증. 아래에서 처음으로 무엇인가를 바꾼다 ──

        Consumption consumption = validateAndCollect(inputs, producerId);
        requireRefinedWasteMaterials(consumption);
        LocalDateTime now = LocalDateTime.now(clock);

        consumeMaterials(consumption, batchId, producerId, now);
        issueScrapMaterials(inputs, consumption, batch, producerId);
        produceItems(batch);

        batch.startSigning();
        batch.confirm(consumption.virginUsed, consumption.upcycledUsed, now);

        return ProductionBatchResponse.of(batch, List.of());
    }

    /**
     * 소비할 것들을 모두 모으고 검증한다. <b>아무것도 바꾸지 않는다.</b>
     *
     * <p>잠금·보유 행을 모두 <b>잠그고</b> 읽는다. 확정 도중 다른 요청이 같은 소재를 가져가면
     * 보유량이 음수가 될 수 있다.
     *
     * <p>업사이클 비율의 분자·분모도 여기서 나온다. 세는 것은 {@code product_used_quantity}
     * 뿐이다 — 자투리와 폐기는 제품에 남아 있지 않으므로 "이 제품의 몇 %가 업사이클인가" 에
     * 셀 수 없다.
     */
    private Consumption validateAndCollect(List<ProductionInput> inputs, Long producerId) {
        Consumption consumption = new Consumption();
        for (ProductionInput input : inputs) {
            MaterialLock lock = lockRepository.findById(input.getMaterialLockId())
                    .orElseThrow(() -> new BusinessException(
                            ProductionErrorCode.PRODUCTION_LOCK_NOT_ACTIVE));
            if (lock.getStatus() != MaterialLockStatus.ACTIVE) {
                // 확정 시점에 잠금이 이미 풀렸다는 것은 데이터가 어긋났다는 신호다.
                // 조용히 넘어가면 잠기지 않은 소재를 소비하게 된다.
                throw new BusinessException(ProductionErrorCode.PRODUCTION_LOCK_NOT_ACTIVE);
            }

            // 보유 행이 사라졌다는 것은 수량이 모자란 것과 다르다. 투입 시점에는 있었고
            // 잠금까지 걸려 있으므로, 확정 시점에 없다면 소유가 통째로 옮겨 갔다는 뜻이다.
            MaterialBalance balance =
                    balanceRepository.findForUpdate(input.getMaterialId(), producerId)
                            .orElseThrow(() -> new BusinessException(
                                    ProductionErrorCode.PRODUCTION_MATERIAL_NOT_OWNED));
            if (balance.getQuantity() < input.getInputQuantity()) {
                throw new BusinessException(ProductionErrorCode.PRODUCTION_INSUFFICIENT_BALANCE);
            }

            Material material = materialRepository.findById(input.getMaterialId())
                    .orElseThrow(() -> new BusinessException(
                            ProductionErrorCode.PRODUCTION_MATERIAL_NOT_FOUND));

            consumption.locks.add(lock);
            consumption.balances.add(balance);
            consumption.amounts.add(input.getInputQuantity());
            consumption.materials.put(input.getMaterialId(), material);
            if (material.getRootOrigin() == MaterialRootOrigin.WASTE) {
                consumption.upcycledUsed += input.getProductUsedQuantity();
            } else {
                consumption.virginUsed += input.getProductUsedQuantity();
            }
        }
        if (consumption.virginUsed + consumption.upcycledUsed <= 0) {
            // 전량이 자투리·폐기라는 뜻이다. 제품을 만들지 못하고 비율도 0/0 이 된다.
            throw new BusinessException(ProductionErrorCode.PRODUCTION_NO_PRODUCT_MATERIAL);
        }
        return consumption;
    }


    /**
     * 폐소재는 <b>세척·해체·선별을 모두 거친 것만</b> 제품이 된다.
     *
     * <p>투입 자격({@code PRODUCTION_MATERIAL_NOT_ELIGIBLE})은 "가공을 한 번이라도 거쳤는가"
     * 만 보므로, 재단만 한 폐데님도 통과한다. 그러면 씻지 않았고 분류되지도 않은 폐기물이
     * 업사이클 제품으로 팔린다 — 화면도 같은 규칙으로 고를 수 없게 막고, 서버가 마지막에
     * 한 번 더 본다.
     *
     * <p><b>신소재는 보지 않는다.</b> 새 원단에는 해체할 것도 선별할 것도 없다. 이 규칙이
     * 지키려는 것은 폐기물의 정제 이력이지 모든 소재의 공정 이력이 아니다.
     *
     * <p>판정 근거는 {@code material_lineage} 를 거슬러 올라가 모은 가공 종류다
     * ({@link MaterialProcessHistoryResolver}). 소재에 적힌 값이 아니라 조회 시점 계산이라,
     * 부모가 세척된 뒤 갈라져 나온 자식도 세척된 것으로 센다.
     */
    private void requireRefinedWasteMaterials(Consumption consumption) {
        List<Long> wasteMaterialIds = consumption.materials.values().stream()
                .filter(material -> material.getRootOrigin() == MaterialRootOrigin.WASTE)
                .map(Material::getId)
                .toList();
        if (wasteMaterialIds.isEmpty()) {
            return;
        }
        Map<Long, List<ProcessType>> completed =
                processHistoryResolver.resolveAll(wasteMaterialIds);
        for (Long materialId : wasteMaterialIds) {
            if (!completed.getOrDefault(materialId, List.of())
                    .containsAll(REQUIRED_PROCESS_TYPES)) {
                throw new BusinessException(
                        ProductionErrorCode.PRODUCTION_MATERIAL_NOT_PROCESSED);
            }
        }
    }

    /**
     * 잠금을 소비로 바꾸고 보유량을 차감한다.
     *
     * <p>둘을 함께 해야 한다. 잠금만 소비하면 보유량이 그대로라 같은 소재를 또 쓸 수 있고,
     * 보유량만 줄이면 잠금이 남아 가용량이 두 번 깎인다.
     */
    private void consumeMaterials(
            Consumption consumption, Long batchId, Long producerId, LocalDateTime now) {
        for (int i = 0; i < consumption.locks.size(); i++) {
            MaterialLock lock = consumption.locks.get(i);
            lock.consume(now);
            consumption.balances.get(i).decrease(consumption.amounts.get(i));
            eventRecorder.record(
                    lock.getMaterialId(), MaterialEventType.MATERIAL_CONSUMED, producerId,
                    MaterialEventReferenceType.PRODUCTION_BATCH, batchId, null,
                    consumption.amounts.get(i), "생산 확정으로 소재 소비",
                    MaterialEventRecorder.key(
                            MaterialEventType.MATERIAL_CONSUMED,
                            lock.getMaterialId(), batchId));
        }
    }

    /**
     * 자투리를 새 소재로 되살린다.
     *
     * <p>연결이 개설 시점에 예정해 둔 {@code production_output} 행을 채우는 방식이다 —
     * 그 행이 "자투리가 이만큼 나올 것" 이었고, 여기서 "이 소재가 됐다" 로 바뀐다.
     *
     * <p>새 소재는 부모에서 {@code root_origin} 을 물려받는다
     * ({@link Material#produceScrap}). 물려받지 않으면 폐데님에서 나온 자투리가 VIRGIN 이
     * 되어 다음 생산의 업사이클 비율이 거짓이 된다.
     *
     * <p>혈통 간선을 함께 잇는다. 간선이 없으면 QR 이력이 자투리에서 끊긴다 —
     * 추적성 플랫폼에서 그건 기능이 없는 것과 같다.
     */
    private void issueScrapMaterials(
            List<ProductionInput> inputs,
            Consumption consumption,
            ProductionBatch batch,
            Long producerId) {
        for (ProductionInput input : inputs) {
            if (!input.hasReusableScrap()) {
                continue;
            }
            Material parent = consumption.materials.get(input.getMaterialId());
            for (ProductionOutput output : outputRepository.findAllByProductionInputId(input.getId())) {
                Material scrap = materialRepository.save(Material.produceScrap(
                        materialCodeGenerator.placeholder(),
                        parent,
                        output.getQuantity(),
                        producerId));
                // 임시 코드가 밖으로 새면 유일하지만 의미 없는 코드가 남는다.
                scrap.assignCode(materialCodeGenerator.of(scrap.getId()));
                materialIssuer.issue(scrap.getId(), producerId, output.getQuantity());
                lineageRegistrar.link(
                        scrap.getId(),
                        parent.getId(),
                        MaterialLineageRelationType.PRODUCTION_SCRAP,
                        batch.getId());
                output.linkNewMaterial(scrap.getId());
            }
        }
    }

    /**
     * 개체를 만든다. <b>여기서 처음으로 팔 수 있는 물건이 생긴다.</b>
     *
     * <p>순번은 배치 안에서 1 부터다 — UNIQUE {@code (production_batch_id, serial_number)} 가
     * 그것을 요구한다. {@code item_code} 는 배치 코드에 순번을 붙여 만든다: 사람이 읽을 때
     * 어느 배치의 몇 번째인지 바로 보인다.
     *
     * <p>QR 토큰은 {@code ProductQrTokenGenerator} 가 만든다. 개체 id 나 순번을 쓰면 값을
     * 맞혀 남의 제품 이력을 열람할 수 있다 — QR 조회는 인증 없이 열린 경로다.
     */
    private void produceItems(ProductionBatch batch) {
        for (int serialNumber = 1; serialNumber <= batch.getProducedQuantity(); serialNumber++) {
            productItemRepository.save(ProductItem.produce(
                    batch.getId(),
                    batch.getProductModelId(),
                    serialNumber,
                    itemCodeOf(batch, serialNumber),
                    qrTokenGenerator.generate()));
        }
    }

    /** {@code PB-000001-0001}. {@code item_code} 는 VARCHAR(50) 이라 충분하다. */
    private String itemCodeOf(ProductionBatch batch, int serialNumber) {
        return batch.getBatchCode() + "-" + String.format("%04d", serialNumber);
    }

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

    /**
     * 검증 단계가 모은 것들. 확정에 필요한 행을 <b>한 번씩만</b> 읽기 위한 그릇이다.
     *
     * <p>같은 행을 검증 때 읽고 변경 때 또 읽으면 그 사이에 달라질 수 있고, 무엇보다
     * 잠금을 두 번 잡는 셈이 된다.
     */
    private static final class Consumption {
        private final List<MaterialLock> locks = new ArrayList<>();
        private final List<MaterialBalance> balances = new ArrayList<>();
        private final List<Long> amounts = new ArrayList<>();
        private final Map<Long, Material> materials = new HashMap<>();
        private long virginUsed;
        private long upcycledUsed;
    }
}
