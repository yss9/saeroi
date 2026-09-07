package com.ssafy.upcycle.material.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.MaterialErrorCode;
import com.ssafy.upcycle.material.dto.MaterialRegisterRequest;
import com.ssafy.upcycle.material.dto.MaterialRegisterResponse;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소재 등록. <b>DB 만 채운다</b> — 온체인 mint 와 그 결과 기록(confirm)은 별도 경로다.
 *
 * <p>그래서 등록된 소재는 {@code token_id}·{@code chain_id}·{@code contract_address} 가
 * 모두 {@code NULL} 이다. 이것이 "등록됐지만 아직 온체인에 없음" 상태이며, 조회 응답이
 * 그대로 내려보내 화면이 구분할 수 있게 한다.
 *
 * <p>조회는 {@link MaterialQueryService} 가 맡는다. 나눈 이유는 읽기 쪽이 앞으로
 * 잔량 집계를 얹으며 커지는데, 쓰기 쪽은 confirm 단계를 얹으며 커지기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class MaterialRegistrationService {

    private final MaterialRepository materialRepository;
    private final UserRepository userRepository;
    private final MaterialCodeGenerator codeGenerator;

    /**
     * 소재를 등록한다.
     *
     * <p><b>코드 발급이 2단계인 것에 주의한다.</b> {@code material_code} 는 저장으로 확정되는
     * {@code id} 에서 파생하는데 컬럼이 {@code NOT NULL} UNIQUE 라 INSERT 시점에 값이 필요하다.
     * 임시 코드로 저장한 뒤 같은 트랜잭션에서 교체한다 — 이 메서드가
     * {@code @Transactional} 인 것이 그 계약이다. 예외가 나면 임시 코드 행도 함께 롤백된다.
     *
     * @param registrantId JWT {@code sub} 에서 온 사용자 id. 요청 바디로 받지 않는다
     */
    @Transactional
    public MaterialRegisterResponse register(Long registrantId, MaterialRegisterRequest request) {
        requireMaterialCapableUser(registrantId);
        long totalQuantity = toGrams(request);

        Material saved = materialRepository.save(Material.register(
                codeGenerator.placeholder(),
                request.materialType(),
                request.rootOrigin(),
                request.name(),
                request.description(),
                request.grade(),
                totalQuantity,
                request.lengthMm(),
                request.widthMm(),
                registrantId));

        saved.assignCode(codeGenerator.of(saved.getId()));
        return MaterialRegisterResponse.from(saved);
    }

    /**
     * 요청 단위를 gram 정수로 환산한다.
     *
     * <p>{@code long} 범위를 넘는 곱셈은 조용히 음수로 감긴다. 그 값이
     * {@code total_quantity} 에 들어가면 {@code chk_material_quantity} 위반으로
     * 사용자에게 설명할 수 없는 500 이 되므로, 여기서 도메인 오류로 바꿔 400 을 만든다.
     */
    private long toGrams(MaterialRegisterRequest request) {
        try {
            return request.unit().toGrams(request.quantity());
        } catch (ArithmeticException overflow) {
            throw new BusinessException(MaterialErrorCode.MATERIAL_QUANTITY_INVALID);
        }
    }

    /**
     * 등록 자격을 확인한다.
     *
     * <p>JWT 의 {@code roles} 클레임을 믿지 않고 DB 를 다시 읽는다. 토큰은 발급 시점의
     * 스냅샷이므로, 정지되거나 역할이 바뀐 계정이 만료 전까지 등록을 계속할 수 있다.
     * 소재 등록은 온체인 발행으로 이어지는 되돌리기 어려운 행위라 최신 상태를 본다.
     */
    private void requireMaterialCapableUser(Long registrantId) {
        User user = userRepository
                .findById(registrantId)
                .orElseThrow(() ->
                        new BusinessException(MaterialErrorCode.MATERIAL_REGISTRANT_NOT_FOUND));
        if (!user.isActive()) {
            throw new BusinessException(MaterialErrorCode.MATERIAL_REGISTRANT_SUSPENDED);
        }
        if (!user.getRole().canRegisterMaterial()) {
            throw new BusinessException(MaterialErrorCode.MATERIAL_ROLE_NOT_ALLOWED);
        }
    }
}
