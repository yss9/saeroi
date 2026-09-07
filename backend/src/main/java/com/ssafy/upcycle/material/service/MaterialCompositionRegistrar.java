package com.ssafy.upcycle.material.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.MaterialErrorCode;
import com.ssafy.upcycle.material.dto.MaterialCompositionRequest;
import com.ssafy.upcycle.material.entity.MaterialComposition;
import com.ssafy.upcycle.material.entity.MaterialFiberType;
import com.ssafy.upcycle.material.repository.MaterialCompositionRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 소재 구성을 기록한다.
 *
 * <p><b>여기가 여러 행에 걸친 규칙을 지키는 유일한 곳이다.</b> 행 하나로 판정되는 것
 * (비율 범위, 섬유 이름 길이)은 {@link MaterialComposition} 이 막지만, 아래 둘은 목록
 * 전체를 함께 봐야 알 수 있다.
 *
 * <ul>
 *   <li><b>합계 ≤ 100</b> — {@code CHECK} 로 표현되지 않는다(여러 행에 걸친 조건)</li>
 *   <li><b>섬유 중복 금지</b> — DB UNIQUE 가 막긴 하지만 그건 500 이 된다</li>
 * </ul>
 *
 * <h2>합계 100 을 요구하지 않는다</h2>
 *
 * <p>정확히 100 을 요구하면 <b>모르는 부분을 지어내게 된다.</b> 수거 소재는 "면 60% 는
 * 확실하고 나머지는 모른다" 가 흔한데, 그때 "기타 40%" 를 넣게 만들면 없는 정보를 만든
 * 것이고 그 값이 Upcycle Ratio 집계로 흘러간다. 빈 부분은 빈 채로 둔다.
 *
 * <p>근거: {@code docs/superpowers/specs/2026-09-07-material-composition-and-origin-reason.md}
 */
@Component
@RequiredArgsConstructor
public class MaterialCompositionRegistrar {

    private final MaterialCompositionRepository compositionRepository;

    /**
     * 구성을 저장한다. 비어 있거나 {@code null} 이면 아무것도 하지 않는다.
     *
     * <p>구성은 <b>선택 정보</b>다. 모르는 소재를 등록하지 못하게 만들면 사용자가 아무
     * 값이나 채워 넣는다. 그건 빈칸보다 나쁘다.
     */
    public List<MaterialComposition> register(
            Long materialId, List<MaterialCompositionRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        requireNoDuplicateFiber(requests);
        requireAllowedFiberTypes(requests);
        requireTotalWithinLimit(requests);

        return compositionRepository.saveAll(requests.stream()
                .map(request -> MaterialComposition.of(
                        materialId, request.fiberType(), request.percentage()))
                .toList());
    }

    /**
     * 같은 섬유가 두 번 오면 거절한다.
     *
     * <p>DB {@code uk_material_composition_fiber} 가 막아 주지만 그건 제약 위반 예외라
     * 사용자에게 500 으로 보인다. 그리고 <b>대소문자를 접어서</b> 비교해야 한다 —
     * 컬레이션이 {@code utf8mb4_0900_ai_ci} 라 DB 는 {@code cotton} 과 {@code COTTON} 을
     * 같게 보므로, 여기서 구분해 통과시키면 DB 에서 터진다.
     */
    private void requireNoDuplicateFiber(List<MaterialCompositionRequest> requests) {
        Set<String> seen = new HashSet<>();
        for (MaterialCompositionRequest request : requests) {
            String fiber = request.fiberType() == null
                    ? ""
                    : request.fiberType().trim().toUpperCase(Locale.ROOT);
            if (!seen.add(fiber)) {
                throw new BusinessException(MaterialErrorCode.MATERIAL_COMPOSITION_DUPLICATE);
            }
        }
    }

    /**
     * 허용 목록 밖의 섬유 이름을 거절한다.
     *
     * <p>컬럼이 {@code VARCHAR(50)} 이라 DB 는 무엇이든 받는다. 강제하지 않으면
     * {@code grade} 에서 이미 안고 있는 문제가 온다 — "면 · 코튼 · COTTON" 이 섞여
     * 집계가 갈라진다.
     */
    private void requireAllowedFiberTypes(List<MaterialCompositionRequest> requests) {
        for (MaterialCompositionRequest request : requests) {
            if (!MaterialFiberType.isAllowed(request.fiberType())) {
                throw new BusinessException(MaterialErrorCode.MATERIAL_FIBER_TYPE_NOT_SUPPORTED);
            }
        }
    }

    private void requireTotalWithinLimit(List<MaterialCompositionRequest> requests) {
        BigDecimal total = requests.stream()
                .map(MaterialCompositionRequest::percentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(MaterialComposition.MAX_TOTAL_PERCENTAGE) > 0) {
            throw new BusinessException(MaterialErrorCode.MATERIAL_COMPOSITION_TOTAL_EXCEEDED);
        }
    }
}
