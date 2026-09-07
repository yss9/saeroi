package com.ssafy.upcycle.trade.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockPurpose;
import com.ssafy.upcycle.material.entity.MaterialLockReferenceType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceQueryRepository;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * <b>"이 소재 얼마나 팔 수 있나" 를 묻는 두 경로가 같은 답을 하는지.</b>
 *
 * <pre>
 *   화면  GET /api/materials/me
 *         → MaterialBalanceQueryRepository#findMyMaterials  (QueryDSL, SQL 집계)
 *         → MyMaterialResponse.available          → 드롭다운의 "판매 가능 N kg"
 *
 *   검증  POST /api/v1/listings
 *         → MaterialAvailability#availableQuantity          (자바 집계)
 *         → 초과면 LISTING_QUANTITY_EXCEEDS_AVAILABLE
 * </pre>
 *
 * <p><b>같은 도메인 규칙이 두 곳에 따로 구현되어 있다.</b> 계산식은
 * {@code 보유량 − 유효한 ACTIVE 잠금 합계} 로 같고 정본은
 * {@code .harness/reference/02-domain-rules.md} 의 Lock 상태 표다. 그런데 한쪽은 SQL,
 * 한쪽은 자바다.
 *
 * <p><b>그래서 이 테스트가 있다.</b> 각 도메인의 기존 테스트는 자기 경로만 본다 —
 * {@code MarketplaceApiTests} 는 판매 등록만, Balance 쪽 테스트는 조회만. 둘이 갈라져도
 * 양쪽 테스트는 통과한다. 잠금 정책이 바뀔 때 한쪽만 고치면 <b>화면은 팔 수 있다고 하는데
 * 등록은 거절되는</b> 상태가 되고, 사용자에게는 설명할 수 없는 409 로 보인다.
 *
 * <p>근본 해결은 계산을 한 곳으로 모으는 것이지만 {@code material} 도메인 파일을 건드리는
 * 일이라 담당자 협의가 필요하다. 그때까지 이 테스트가 안전망이다.
 *
 * <p>{@code @Transactional} 을 붙이지 않았다. 두 구현이 서로 다른 방식으로 DB 를 읽으므로
 * (한쪽은 상관 서브쿼리, 한쪽은 두 번의 단건 조회) 커밋된 같은 상태를 보게 해야 비교가
 * 성립한다. 대신 앞뒤로 직접 정리한다.
 */
@SpringBootTest
class MaterialAvailabilityConsistencyTests {

    private static final String WALLET = "0xc000000000000000000000000000000000000001";

    @Autowired
    private MaterialAvailability materialAvailability;

    @Autowired
    private MaterialBalanceQueryRepository balanceQueryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialBalanceRepository balanceRepository;

    @Autowired
    private MaterialLockRepository lockRepository;

    @Autowired
    private Clock clock;

    private Long ownerId;
    private Long materialId;

    @BeforeEach
    void setUp() {
        clean();
        User owner = userRepository.save(User.register(
                "availability-owner@example.com", "{noop}x", "공급자", "010-8000-0000",
                UserRole.SUPPLIER));
        ownerId = owner.getId();

        Material material = materialRepository.save(Material.register(
                "MAT-980001", MaterialType.DENIM, MaterialRootOrigin.WASTE,
                "가용량 대조용", null, "A", 30_000L, null, null, ownerId));
        materialId = material.getId();

        balanceRepository.save(MaterialBalance.of(materialId, ownerId, WALLET, 30_000L));
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void agreeWhenNothingIsLocked() {
        assertBothAgree(30_000L);
    }

    @Test
    void agreeWhenPartOfTheBalanceIsLocked() {
        lock(20_000L, null);

        assertBothAgree(10_000L);
    }

    @Test
    void agreeWhenEverythingIsLocked() {
        lock(30_000L, null);

        assertBothAgree(0L);
    }

    /**
     * 만료된 잠금은 <b>양쪽 모두</b> 차감하지 않아야 한다.
     *
     * <p>도메인 규칙이 {@code EXPIRED} 를 "전환 전이라도 제외" 로 못 박았는데, 상태를 바꿔
     * 주는 배치가 없어서 만료된 잠금이 DB 에는 {@code ACTIVE} 로 남아 있다. 한쪽만
     * {@code expires_at} 을 보면 두 경로가 다른 값을 답한다.
     */
    @Test
    void agreeThatExpiredLocksAreNotDeducted() {
        lock(20_000L, LocalDateTime.now(clock).minusMinutes(1));

        assertBothAgree(30_000L);
    }

    /** 만료 전 잠금은 양쪽 모두 차감한다. 위 테스트의 대조군이다. */
    @Test
    void agreeThatUnexpiredLocksAreDeducted() {
        lock(20_000L, LocalDateTime.now(clock).plusHours(1));

        assertBothAgree(10_000L);
    }

    /**
     * <b>잠금 합이 보유량을 넘는 파손 상태에서도 같은 값을 답해야 한다.</b>
     *
     * <p>이것이 이 파일에서 실제로 두 구현을 갈라놨던 지점이다. 검증 쪽이
     * {@code Math.max(available, 0)} 으로 음수를 접고 있어서 화면은 {@code -5kg} 를,
     * 검증은 {@code 0} 을 말했다. 판매 거절이라는 결과는 같았지만 <b>검증 경로에서만
     * 파손 신호가 사라졌다.</b>
     *
     * <p>음수는 온체인 잔액 동기화가 어긋났다는 뜻이고, 그 신호를 남기는 것이
     * {@code InventoryItem} 주석이 못 박은 설계다 — *"음수 가능 — 데이터 파손 신호다"*.
     */
    @Test
    void agreeWhenLocksExceedTheBalance() {
        lock(35_000L, null);

        assertBothAgree(-5_000L);
    }

    /**
     * 두 경로가 같은 값을 답하는지, 그리고 그 값이 기대치인지 함께 본다.
     *
     * <p>둘을 비교만 하면 <b>둘 다 똑같이 틀린 경우</b>를 놓친다. 그래서 기대치도 받는다.
     */
    private void assertBothAgree(long expected) {
        long fromValidation = materialAvailability.availableQuantity(materialId, ownerId);

        List<MyMaterialResponse> myMaterials = balanceQueryRepository.findMyMaterials(ownerId);
        assertThat(myMaterials).hasSize(1);
        long fromScreen = myMaterials.get(0).available();

        assertThat(fromValidation)
                .as("판매 등록 검증이 계산한 가용량")
                .isEqualTo(expected);
        assertThat(fromScreen)
                .as("소재 화면(GET /api/materials/me)이 보여줄 가용량")
                .isEqualTo(expected);
        assertThat(fromValidation)
                .as("두 경로가 같은 값을 답해야 한다 — 다르면 화면과 서버 판단이 어긋난다")
                .isEqualTo(fromScreen);
    }

    /** MARKETPLACE 잠금 하나. {@code expiresAt} 이 {@code null} 이면 만료 없음이다. */
    private void lock(long amount, LocalDateTime expiresAt) {
        lockRepository.save(MaterialLock.create(
                materialId,
                ownerId,
                amount,
                MaterialLockPurpose.MARKETPLACE,
                MaterialLockReferenceType.LISTING,
                1L,
                expiresAt));
    }

    private void clean() {
        lockRepository.deleteAll();
        balanceRepository.deleteAll();
        materialRepository.deleteAll();
        userRepository.deleteAll();
    }
}
