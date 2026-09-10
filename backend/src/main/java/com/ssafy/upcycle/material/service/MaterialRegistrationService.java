package com.ssafy.upcycle.material.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.MaterialErrorCode;
import com.ssafy.upcycle.material.dto.MaterialRegisterRequest;
import com.ssafy.upcycle.material.dto.MaterialRegisterResponse;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialEventReferenceType;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
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
 *
 * <p><b>보유 관계({@code material_balance})는 여기서 함께 만든다.</b> 온체인 여부와 무관하게
 * "등록자가 전량을 보유한다" 는 사실은 성립하고, 그 행이 없으면 소재를 팔 수도 가공에 넣을
 * 수도 없다. 발행 방식은 {@link MaterialIssuer} 가 정하므로 이 클래스는 mint 를 알지 못한다.
 */
@Service
@RequiredArgsConstructor
public class MaterialRegistrationService {

    private final MaterialRepository materialRepository;
    private final UserRepository userRepository;
    private final MaterialCodeGenerator codeGenerator;
    private final MaterialCompositionRegistrar compositionRegistrar;
    private final MaterialIssuer materialIssuer;
    private final MaterialEventRecorder eventRecorder;

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
        describeOrigin(saved, request);
        // 구성은 소재 id 가 확정된 뒤에만 저장할 수 있다 (FK). 같은 트랜잭션이라
        // 구성 검증이 실패하면 소재 행도 함께 롤백된다 — 구성 없는 소재가 남지 않는다.
        compositionRegistrar.register(saved.getId(), request.composition());
        // 보유 관계도 같은 트랜잭션에서 만든다. 이것이 빠져 있으면 소재는 등록되는데
        // 판매 등록이 LISTING_QUANTITY_EXCEEDS_AVAILABLE 로 막힌다 — 보유량 조회가
        // 행을 못 찾아 0 을 돌려주기 때문이다. 발행 방식은 MaterialIssuer 가 정한다.
        materialIssuer.issue(saved.getId(), registrantId, totalQuantity);
        eventRecorder.record(
                saved.getId(),
                MaterialEventType.MATERIAL_REGISTERED,
                registrantId,
                MaterialEventReferenceType.MATERIAL,
                saved.getId(),
                null,
                totalQuantity,
                "소재 최초 등록",
                MaterialEventRecorder.key(
                        MaterialEventType.MATERIAL_REGISTERED, saved.getId(), saved.getId()));
        return MaterialRegisterResponse.from(saved);
    }

    /**
     * 발생 사유를 기록한다.
     *
     * <p><b>폐기물에 사유가 없는 것을 아직 거절하지 않는다.</b> 명세는 "{@code WASTE} 이면
     * 필수" 로 정했지만, 지금 그렇게 만들면 <b>배포된 등록 폼이 400 을 받는다</b> —
     * 프론트엔드(S15P21E104-152)는 이 필드를 보내지 않고, 입력칸은 S15P21E104-437 에서
     * 붙는다. 서버를 먼저 조이면 그 사이 소재 등록이 전부 막힌다.
     *
     * <p>그래서 순서는 이렇다: 이 커밋(선택) → -437 이 입력칸 추가 → 그때 필수로 전환.
     * 전환은 이 메서드 한 곳만 고치면 된다.
     *
     * <p>값이 <b>들어온 경우</b>의 규칙은 지금부터 지킨다 — 신소재에 사유가 붙거나
     * {@code ETC} 에 설명이 없으면 거절한다. 그건 기존 요청을 깨지 않는다.
     *
     * <p>{@code IllegalArgumentException} 을 도메인 오류로 바꾸는 것이 이 메서드의 존재
     * 이유다. 엔티티는 출처와의 모순(신소재에 사유가 붙음)과 {@code ETC} 의 설명 누락을
     * 막는데, 그 실패를 그대로 흘리면 사용자에게 <b>설명할 수 없는 500</b> 이 된다.
     */
    private void describeOrigin(Material material, MaterialRegisterRequest request) {
        try {
            material.describeOrigin(request.originReason(), request.originReasonNote());
        } catch (IllegalArgumentException invalid) {
            throw new BusinessException(MaterialErrorCode.MATERIAL_ORIGIN_REASON_INVALID);
        }
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
