package com.ssafy.upcycle.material.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.MaterialErrorCode;
import com.ssafy.upcycle.material.dto.MaterialBalanceResponse;
import com.ssafy.upcycle.material.dto.MaterialDetailResponse;
import com.ssafy.upcycle.material.dto.MaterialVerificationResponse;
import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialVerificationState;
import com.ssafy.upcycle.material.repository.MaterialBalanceQueryRepository;
import com.ssafy.upcycle.material.repository.MaterialCompositionRepository;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소재 조회. 등록({@link MaterialRegistrationService})과 나눈 이유는 두 쪽이 자라는 방향이
 * 다르기 때문이다 — 읽기는 잔량 집계를, 쓰기는 온체인 확정 단계를 얹으며 커진다.
 *
 * <p>조회는 인증만 요구하고 역할은 보지 않는다. 소재 정보는 추적성이 목적이라 누가 보든
 * 같은 값이어야 한다. 소유자만 볼 수 있어야 하는 것은 잠금 내역 쪽이고 그건 별도 경로다.
 */
@Service
@RequiredArgsConstructor
public class MaterialQueryService {

    private final MaterialRepository materialRepository;
    private final MaterialBalanceRepository materialBalanceRepository;
    private final MaterialBalanceQueryRepository materialBalanceQueryRepository;
    private final MaterialVerificationStateResolver verificationStateResolver;
    private final BlockchainVerification blockchainVerification;
    private final MaterialCompositionRepository materialCompositionRepository;

    /**
     * 소재 상세.
     *
     * <p>보유자를 {@code material_balance} 에서 따로 읽는다 — {@code registrant_id} 는 누가
     * 올렸는지일 뿐이고, 거래가 일어나면 보유자는 바뀐다. 구성도 별도 테이블이라 단건
     * 조회의 쿼리 수는 소재 · 보유자 · 구성 · 검증 근거로 늘어난다. 단건이라 N+1 이 아니다.
     *
     * <p>보유 행이 없으면 <b>404 가 아니라 빈 목록</b>이다. 소재는 존재하는데 아직 아무도
     * 들고 있지 않은 상태(또는 Projection 동기화 전)와 소재가 없는 것은 다른 상황이며,
     * 이를 같은 응답으로 묶으면 화면이 "없는 소재" 라고 잘못 말한다.
     *
     * <p>{@code readOnly = true} 는 성능 표시가 아니라 의도 표시다 — 조회 경로에서 엔티티가
     * 변경되면 flush 되지 않고 조용히 사라지는 편이, 예상 못 한 UPDATE 가 나가는 것보다 낫다.
     */
    @Transactional(readOnly = true)
    public MaterialDetailResponse findDetail(Long materialId) {
        Material material = materialRepository
                .findById(materialId)
                .orElseThrow(() -> new BusinessException(MaterialErrorCode.MATERIAL_NOT_FOUND));
        MaterialVerificationState verificationState = verificationStateResolver.resolve(materialId);
        return MaterialDetailResponse.of(
                material,
                materialBalanceRepository.findAllByMaterialId(materialId),
                materialCompositionRepository
                        .findByMaterialIdOrderByPercentageDescFiberTypeAsc(materialId),
                verificationState,
                verificationEvidence(materialId, verificationState));
    }

    /**
     * {@code SOURCE_VERIFIED} 일 때만 근거를 채운다. 다른 단은 근거의 종류가 달라
     * (거래 서명 등) {@link MaterialVerificationResponse} 의 모양(지갑·Tx 해시)이 맞지
     * 않는다 — {@code null} 로 둔다.
     */
    private MaterialVerificationResponse verificationEvidence(
            Long materialId, MaterialVerificationState state) {
        if (state != MaterialVerificationState.SOURCE_VERIFIED) {
            return null;
        }
        return blockchainVerification
                .findSourceVerification(materialId)
                .map(evidence -> MaterialVerificationResponse.of(state, evidence))
                .orElse(null);
    }

    /**
     * 한 소재의 보유자별 잔량.
     *
     * <p><b>소재가 없으면 404 지만, 보유자가 없으면 빈 목록이다.</b> 둘은 다른 상황이고
     * 같은 응답으로 묶으면 화면이 "없는 소재" 라고 잘못 말한다.
     *
     * <p>사용 가능 수량은 {@code MaterialBalanceQueryRepository} 가 ACTIVE 잠금 합계를
     * 빼서 한 번의 쿼리로 계산한다 — 여기서 다시 계산하지 않는다.
     */
    @Transactional(readOnly = true)
    public List<MaterialBalanceResponse> findBalances(Long materialId) {
        if (!materialRepository.existsById(materialId)) {
            throw new BusinessException(MaterialErrorCode.MATERIAL_NOT_FOUND);
        }
        return materialBalanceQueryRepository.findBalancesByMaterialId(materialId);
    }

    /**
     * 내가 보유한 소재 목록.
     *
     * <p>보유 행이 없으면 빈 목록이다. 계정이 아직 아무 소재도 갖고 있지 않은 것이
     * 오류는 아니다 — 화면은 이 경우 안내 문구를 보여주면 된다.
     */
    @Transactional(readOnly = true)
    public List<MyMaterialResponse> findMyMaterials(Long ownerId) {
        return materialBalanceQueryRepository.findMyMaterials(ownerId);
    }
}
