package com.ssafy.upcycle.production.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.production.ProductionErrorCode;
import com.ssafy.upcycle.production.dto.ProductionManifestResponse;
import com.ssafy.upcycle.production.entity.ProductionBatch;
import com.ssafy.upcycle.production.entity.ProductionInput;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
import com.ssafy.upcycle.production.repository.ProductionInputRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 온체인에 올릴 생산 manifest 를 만들고 해시한다.
 *
 * <h2>해시 계산 주체가 하나여야 한다</h2>
 *
 * <p>화면이 만들면 같은 배치에서 브라우저마다 다른 해시가 나올 수 있다 — 필드 순서·공백·
 * 인코딩이 조금만 달라도 해시는 완전히 달라진다. 그러면 나중에 재현해 대조할 수 없고,
 * <b>추적성의 마지막 한 칸이 그 대조에 걸려 있다.</b>
 *
 * <p>그래서 서버가 만들고, 화면은 <b>받은 해시를 그대로 서명에 넣는다.</b>
 *
 * <h2>확정된 배치만 만들 수 있다</h2>
 *
 * <p>manifest 에는 개체 코드와 업사이클 비율이 들어가는데, 둘 다 확정이 만들어 내는 값이다.
 * 확정 전에 만들면 나중에 내용이 달라지고, 그때는 이미 체인에 옛 해시가 올라가 있다.
 */
@Service
@RequiredArgsConstructor
public class ProductionManifestService {

    private final ProductionBatchRepository batchRepository;
    private final ProductionInputRepository inputRepository;
    private final ProductItemRepository productItemRepository;
    private final MaterialRepository materialRepository;
    private final ObjectMapper objectMapper;

    /**
     * 배치의 manifest 와 해시.
     *
     * <p>같은 배치에 대해 <b>몇 번을 불러도 같은 값</b>이어야 한다 — 확정된 배치의 내용은
     * 더 바뀌지 않으므로 그것이 성립한다. 정렬을 고정하는 이유도 그것이다.
     */
    @Transactional(readOnly = true)
    public ProductionManifestResponse build(Long producerId, Long batchId) {
        ProductionBatch batch = batchRepository.findById(batchId)
                .orElseThrow(
                        () -> new BusinessException(ProductionErrorCode.PRODUCTION_BATCH_NOT_FOUND));
        if (!batch.getProducerId().equals(producerId)) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_FORBIDDEN);
        }
        if (!batch.isConfirmed()) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_BATCH_NOT_CONFIRMED);
        }

        ProductionManifestResponse.Manifest manifest = manifestOf(batch);
        return new ProductionManifestResponse(manifest, hashOf(manifest));
    }

    private ProductionManifestResponse.Manifest manifestOf(ProductionBatch batch) {
        List<ProductionInput> inputs = inputRepository.findAllByProductionBatchId(batch.getId());
        Map<Long, Material> materials = materialRepository
                .findAllById(inputs.stream().map(ProductionInput::getMaterialId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));

        // 정렬을 고정한다. 조회 순서에 기대면 DB 나 드라이버가 바뀔 때 같은 배치의 해시가
        // 달라지고, 그때는 이미 체인에 옛 해시가 올라가 있다.
        List<ProductionManifestResponse.ManifestInput> manifestInputs = inputs.stream()
                .map(input -> new ProductionManifestResponse.ManifestInput(
                        materials.get(input.getMaterialId()).getMaterialCode(),
                        input.getInputQuantity(),
                        input.getProductUsedQuantity(),
                        input.getReusableScrapQuantity(),
                        input.getWasteQuantity()))
                .sorted(Comparator.comparing(
                        ProductionManifestResponse.ManifestInput::materialCode))
                .toList();

        List<String> itemCodes = productItemRepository
                .findAllByProductionBatchId(batch.getId())
                .stream()
                .sorted(Comparator.comparingInt(ProductItem::getSerialNumber))
                .map(ProductItem::getItemCode)
                .toList();

        return new ProductionManifestResponse.Manifest(
                batch.getBatchCode(),
                batch.getProductModelId(),
                batch.getProducedQuantity(),
                batch.getProducerWallet(),
                // 문자열로 담는다. 소수 표기가 언어마다 달라지면 해시가 갈라진다.
                batch.getUpcycleRatio().toPlainString(),
                batch.getTotalUsedQuantity(),
                batch.getVirginUsedQuantity(),
                batch.getUpcycledUsedQuantity(),
                manifestInputs,
                itemCodes);
    }

    /**
     * {@code 0x} + SHA-256 64 hex.
     *
     * <p>증빙 파일 해시와 같은 알고리즘이다 ({@code FileStorageService} 가 저장하며 계산하는
     * 값). 형식도 {@code evidence_file.file_hash CHAR(66)} 과 같아서, 나중에 두 해시를 같은
     * 방식으로 다룰 수 있다.
     */
    private String hashOf(ProductionManifestResponse.Manifest manifest) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(manifest);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
            return "0x" + HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException impossible) {
            // 직렬화 대상이 우리가 만든 record 이고 SHA-256 은 JDK 필수 알고리즘이다.
            throw new IllegalStateException("Failed to hash production manifest", impossible);
        }
    }

    /**
     * 요청이 보낸 해시가 서버가 계산한 것과 같은가.
     *
     * <p>다르면 <b>화면이 서버가 모르는 내용을 체인에 올린 것</b>이고, 그 해시는 나중에
     * 재현할 수 없다. 온체인 기록을 받을 때 이 검사를 거친다.
     */
    @Transactional(readOnly = true)
    public void requireMatchingHash(Long producerId, Long batchId, String evidenceHash) {
        String expected = build(producerId, batchId).evidenceHash();
        if (!expected.equalsIgnoreCase(evidenceHash)) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_MANIFEST_HASH_MISMATCH);
        }
    }
}
