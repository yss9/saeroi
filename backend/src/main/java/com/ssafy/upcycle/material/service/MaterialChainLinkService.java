package com.ssafy.upcycle.material.service;

import com.ssafy.upcycle.blockchain.BlockchainErrorCode;
import com.ssafy.upcycle.blockchain.dto.ChainLinkRequest;
import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.service.ChainTransactionRecorder;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.MaterialErrorCode;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialEventReferenceType;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소재 발행(mint) 결과를 DB 에 연결한다.
 *
 * <p><b>서버가 mint 를 보내지 않는다.</b> {@code MaterialToken.mintMaterial} 은
 * {@code SUPPLIER_ROLE} 을 가진 지갑이 서명해야 하는데 서버에는 개인키가 없다. 그래서 화면이
 * 지갑으로 보내고, 영수증의 {@code MaterialMinted} 이벤트에서 {@code tokenId} 를 꺼내
 * 이 경로로 알려 준다.
 *
 * <p><b>보유 행도 여기서 만든다.</b> 온체인이 정본인 설정에서는 등록이 보유를 만들지
 * 않는다 — 서명 전에 만들어 두면 지갑 팝업에서 취소한 소재가 보유 목록에 남고, 체인에는
 * 없는 수량을 팔거나 가공에 넣을 수 있게 된다.
 *
 * <p>{@link MaterialRegistrationService} 를 건드리지 않고 따로 둔 이유가 여기 있다 —
 * 등록과 발행은 <b>다른 시점, 다른 주체</b>의 일이다. 등록은 서버 트랜잭션 하나로 끝나지만
 * 발행은 사용자가 지갑 팝업을 확인하고 블록이 채굴되기를 기다려야 한다. 그 사이 사용자가
 * 이탈할 수 있고, 그 소재는 {@code token_id} 가 {@code NULL} 인 채로 남는다 —
 * {@code MaterialRegistrationService} 주석이 말하는 "등록됐지만 아직 온체인에 없음" 상태다.
 */
@Service
@RequiredArgsConstructor
public class MaterialChainLinkService {

    private final MaterialRepository materialRepository;
    private final ChainTransactionRecorder recorder;
    private final MaterialEventRecorder eventRecorder;
    private final MaterialBalanceInitializer balanceInitializer;

    /**
     * 발행 결과를 연결한다. <b>등록자 본인만 할 수 있다.</b>
     *
     * <p>남이 부를 수 있으면 임의의 소재에 아무 {@code tokenId} 나 붙일 수 있고, 한 번 붙으면
     * 되돌릴 수 없다({@link Material#linkOnChain} 이 재배정을 막는다).
     *
     * <p>이미 연결된 소재는 409 로 거절한다. 화면이 뒤로 가기로 같은 요청을 두 번 보내는
     * 경로가 실제로 있고, 그때 두 번째 mint 의 토큰으로 덮어쓰면 첫 토큰이 미아가 된다.
     */
    @Transactional
    public void link(Long registrantId, Long materialId, ChainLinkRequest request) {
        recorder.requireConfigured();

        Material material = materialRepository
                .findById(materialId)
                .orElseThrow(() -> new BusinessException(MaterialErrorCode.MATERIAL_NOT_FOUND));
        if (!material.getRegistrantId().equals(registrantId)) {
            throw new BusinessException(MaterialErrorCode.MATERIAL_ROLE_NOT_ALLOWED);
        }
        if (material.getTokenId() != null) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_ALREADY_LINKED);
        }

        material.linkOnChain(
                request.onChainId(), recorder.chainId(), recorder.materialTokenAddress());
        // 보유 행은 여기서 만들어진다. 온체인이 정본인 설정에서는 등록 시점에 만들지 않기
        // 때문이다 (OnChainMaterialIssuer) — 서명 전에 만들어 두면 지갑에서 취소한 소재가
        // 보유 목록에 남고, 체인에 없는 수량을 팔거나 가공에 넣을 수 있게 된다.
        //
        // 오프체인 설정에서는 등록 때 이미 만들어져 있고, 그때는 아무것도 하지 않는다.
        balanceInitializer.open(materialId, registrantId, material.getTotalQuantity());
        recorder.record(
                registrantId,
                ChainTransactionRecorder.Contract.MATERIAL_TOKEN,
                "mintMaterial",
                BlockchainReferenceType.MATERIAL,
                materialId,
                request.txHash());
        eventRecorder.record(
                materialId,
                MaterialEventType.MATERIAL_MINTED,
                registrantId,
                MaterialEventReferenceType.MATERIAL,
                materialId,
                null,
                material.getTotalQuantity(),
                "Material Token 발행 확정",
                MaterialEventRecorder.key(MaterialEventType.MATERIAL_MINTED, materialId, materialId));
    }
}
