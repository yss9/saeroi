package com.ssafy.upcycle.material.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import com.ssafy.upcycle.material.MaterialErrorCode;
import com.ssafy.upcycle.material.dto.MaterialCompositionRequest;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialComposition;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialCompositionRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * 여러 행을 함께 봐야 아는 규칙을 검증한다. 행 하나로 판정되는 것은
 * {@link MaterialComposition} 의 몫이다.
 *
 * <p><b>실제 DB 로 돈다.</b> 리포지토리를 스텁으로 바꾸면 {@code CHECK}·UNIQUE 가 실제로
 * 어떻게 반응하는지 알 수 없고, 이 기능에서 확인해야 할 것 중 하나가 "애플리케이션 검사와
 * DB 제약이 같은 조건을 보는가" 다 — 어긋나면 400 대신 500 이 나간다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, MaterialCompositionRegistrar.class})
class MaterialCompositionRegistrarTests {

    @Autowired
    private MaterialCompositionRegistrar registrar;

    @Autowired
    private MaterialCompositionRepository compositionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Long materialId;

    @BeforeEach
    void setUp() {
        // material_composition.material_id 는 material FK 라 실제 행이 있어야 한다.
        User owner = entityManager.persistAndFlush(User.register(
                "supplier@example.com", "{noop}x", "공급자", "010-0000-0000", UserRole.SUPPLIER));
        materialId = entityManager
                .persistAndFlush(Material.register(
                        "MAT-000901",
                        MaterialType.DENIM,
                        MaterialRootOrigin.WASTE,
                        "폐데님 원단",
                        null,
                        "B",
                        10_000L,
                        null,
                        null,
                        owner.getId()))
                .getId();
    }

    private static MaterialCompositionRequest of(String fiber, String percentage) {
        return new MaterialCompositionRequest(fiber, new BigDecimal(percentage));
    }

    private List<MaterialComposition> stored() {
        return compositionRepository.findByMaterialIdOrderByPercentageDescFiberTypeAsc(materialId);
    }

    // ── 저장 ────────────────────────────────────────────────

    @Test
    void savesEveryRequestedLine() {
        registrar.register(materialId, List.of(of("POLYESTER", "40"), of("COTTON", "60")));

        assertThat(stored())
                .extracting(MaterialComposition::getFiberType)
                .as("비율 내림차순으로 고정한다 — 같은 소재를 두 번 열었을 때 줄 순서가 달라 보이면 안 된다")
                .containsExactly("COTTON", "POLYESTER");
    }

    @Test
    void normalizesFiberTypeToUpperCase() {
        // UNIQUE 컬레이션이 대소문자를 무시하므로 저장 값도 통일해야 화면이 섞이지 않는다.
        registrar.register(materialId, List.of(of(" cotton ", "50")));

        assertThat(stored()).singleElement().satisfies(c ->
                assertThat(c.getFiberType()).isEqualTo("COTTON"));
    }

    // ── 구성은 선택 정보다 ───────────────────────────────────

    @Test
    void emptyCompositionIsAccepted() {
        // 모르는 소재를 등록하지 못하게 만들면 사용자가 아무 값이나 채워 넣는다.
        assertThat(registrar.register(materialId, List.of())).isEmpty();
        assertThat(stored()).isEmpty();
    }

    @Test
    void nullCompositionIsAccepted() {
        assertThat(registrar.register(materialId, null)).isEmpty();
        assertThat(stored()).isEmpty();
    }

    // ── 합계 ────────────────────────────────────────────────

    @Test
    void acceptsATotalBelowOneHundred() {
        // "면 60% 는 확실하고 나머지는 모른다" 가 흔하다. 100 을 강제하면 사용자가
        // "기타 40%" 를 지어내고 그 값이 Upcycle Ratio 집계로 흘러간다.
        assertThatCode(() -> registrar.register(materialId, List.of(of("COTTON", "60"))))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsATotalOfExactlyOneHundred() {
        assertThatCode(() -> registrar.register(
                        materialId, List.of(of("COTTON", "60"), of("POLYESTER", "40"))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsATotalAboveOneHundred() {
        assertThatThrownBy(() -> registrar.register(
                        materialId, List.of(of("COTTON", "60"), of("POLYESTER", "41"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MaterialErrorCode.MATERIAL_COMPOSITION_TOTAL_EXCEEDED);
    }

    @Test
    void decimalPercentagesSumExactly() {
        // double 이면 33.33 * 3 이 99.99000000000001 이 되어 정상 입력이 거절된다.
        assertThatCode(() -> registrar.register(
                        materialId,
                        List.of(
                                of("COTTON", "33.33"),
                                of("POLYESTER", "33.33"),
                                of("WOOL", "33.34"))))
                .doesNotThrowAnyException();
    }

    // ── 중복 ────────────────────────────────────────────────

    @Test
    void rejectsTheSameFiberTwice() {
        // DB UNIQUE 가 막긴 하지만 그건 사용자에게 500 으로 보인다. 앞에서 400 을 만든다.
        assertThatThrownBy(() -> registrar.register(
                        materialId, List.of(of("COTTON", "30"), of("COTTON", "30"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MaterialErrorCode.MATERIAL_COMPOSITION_DUPLICATE);
    }

    @Test
    void rejectsDuplicatesThatDifferOnlyByCase() {
        // 컬레이션이 utf8mb4_0900_ai_ci 라 DB 는 cotton 과 COTTON 을 같게 본다.
        // 여기서 구분해 통과시키면 DB 에서 터진다.
        assertThatThrownBy(() -> registrar.register(
                        materialId, List.of(of("cotton", "30"), of("COTTON", "30"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MaterialErrorCode.MATERIAL_COMPOSITION_DUPLICATE);
    }

    // ── 허용 목록 ───────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"면", "코튼", "Cotton100", "UNKNOWN_FIBER", "  "})
    void rejectsFiberTypesOutsideTheWhitelist(String fiber) {
        // 컬럼이 VARCHAR(50) 이라 DB 는 무엇이든 받는다. 강제하지 않으면 grade 처럼
        // 같은 뜻이 여러 표기로 섞여 집계가 갈라진다.
        assertThatThrownBy(() -> registrar.register(materialId, List.of(of(fiber, "50"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MaterialErrorCode.MATERIAL_FIBER_TYPE_NOT_SUPPORTED);
    }

    @Test
    void nothingIsSavedWhenValidationFails() {
        // 검증이 목록 전체를 보고 판정하므로 앞부분만 저장되고 끊기면 안 된다.
        assertThatThrownBy(() -> registrar.register(
                        materialId, List.of(of("COTTON", "60"), of("UNKNOWN_FIBER", "10"))))
                .isInstanceOf(BusinessException.class);

        assertThat(stored()).isEmpty();
    }
}
