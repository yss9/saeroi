package com.ssafy.upcycle.material.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.MaterialErrorCode;
import com.ssafy.upcycle.material.dto.MaterialQuantityUnit;
import com.ssafy.upcycle.material.dto.MaterialRegisterRequest;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialEventRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.material.dto.MaterialRegisterResponse;
import com.ssafy.upcycle.trade.dto.ListingRegisterRequest;
import com.ssafy.upcycle.trade.TradeErrorCode;
import com.ssafy.upcycle.trade.service.ListingCommandService;
import com.ssafy.upcycle.trade.service.MaterialAvailability;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

/**
 * <b>등록한 소재를 실제로 팔 수 있는가.</b>
 *
 * <p>이 파일이 생긴 이유는 두 도메인의 테스트가 각자의 절반만 보고 있었기 때문이다.
 * {@code MaterialControllerTests} 는 소재가 저장되는 것까지 보고, {@code MarketplaceApiTests}
 * 는 보유 행을 직접 만들어 두고 판매 등록을 본다. <b>둘 다 통과하는데 이어 붙이면 끊겼다</b> —
 * 등록이 {@code material_balance} 를 만들지 않아 판매 등록이 보유량 0 으로 거절됐다.
 *
 * <p>시드 SQL 로 보유 행을 채워 놓고 손으로 확인하면 이 구멍이 보이지 않는다. 실제로 그렇게
 * 가려져 있었다. 그래서 이 테스트는 <b>등록 API 가 만든 상태만으로</b> 판매까지 간다.
 *
 * <p>{@code @Transactional} 을 붙이지 않았다. 등록과 판매 등록이 서로 다른 트랜잭션에서
 * 커밋된 상태를 보는 것이 실제 흐름이고, {@code MaterialAvailability} 도 그렇게 읽는다.
 * 대신 앞뒤로 직접 정리한다.
 */
@SpringBootTest
class MaterialIssueTests {

    private static final String WALLET = "0xd00d000000000000000000000000000000000001";
    private static final long SSAFY_CHAIN_ID = 31221L;

    @Autowired
    private MaterialRegistrationService registrationService;

    @Autowired
    private ListingCommandService listingCommandService;

    @Autowired
    private MaterialAvailability materialAvailability;

    @Autowired
    private MaterialBalanceRepository balanceRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialEventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserWalletRepository walletRepository;

    private Long supplierId;

    @BeforeEach
    void setUp() {
        clean();
        User supplier = userRepository.save(User.register(
                "issue-supplier@example.com", "{noop}x", "공급자", "010-9000-0000", UserRole.SUPPLIER));
        supplierId = supplier.getId();
        walletRepository.save(UserWallet.link(supplierId, WALLET, SSAFY_CHAIN_ID));
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void registrationGivesTheRegistrantTheWholeQuantity() {
        Long materialId = register(30L).materialId();

        MaterialBalance balance = balanceRepository
                .findByMaterialIdAndOwnerId(materialId, supplierId)
                .orElseThrow();

        assertThat(balance.getQuantity())
                .as("30kg 을 등록하면 등록자가 30,000g 을 보유한다")
                .isEqualTo(30_000L);
        assertThat(balance.getWalletAddress())
                .as("보유 행은 등록 시점의 활성 지갑을 스냅샷으로 들고 있어야 한다")
                .isEqualTo(WALLET);
        assertThat(eventRepository.findAllByMaterialIdOrderByOccurredAtAscIdAsc(
                        materialId, PageRequest.of(0, 10)).getContent())
                .extracting(event -> event.getEventType())
                .containsExactly(MaterialEventType.MATERIAL_REGISTERED);
    }

    /**
     * <b>이 테스트가 원래의 구멍을 지킨다.</b>
     *
     * <p>등록만 하고 아무것도 손대지 않은 상태에서 판매 등록이 성공해야 한다. 발행이 빠지면
     * {@code LISTING_QUANTITY_EXCEEDS_AVAILABLE} 로 실패한다.
     */
    @Test
    void aFreshlyRegisteredMaterialCanBeListedForSale() {
        Long materialId = register(30L).materialId();

        var listing = listingCommandService.register(supplierId, new ListingRegisterRequest(
                materialId, 30L, MaterialQuantityUnit.KG, 4_000L));

        assertThat(listing.listingId()).isPositive();
        assertThat(materialAvailability.availableQuantity(materialId, supplierId))
                .as("전량을 판매에 걸면 남은 가용량은 0 이다")
                .isZero();
        assertThat(eventRepository.findAllByMaterialIdOrderByOccurredAtAscIdAsc(
                        materialId, PageRequest.of(0, 10)).getContent())
                .extracting(event -> event.getEventType())
                .containsExactly(
                        MaterialEventType.MATERIAL_REGISTERED,
                        MaterialEventType.MARKETPLACE_LISTED);
    }

    /** 판매 등록은 보유량을 넘을 수 없다. 위 테스트가 우연히 통과하는 것이 아님을 보인다. */
    @Test
    void listingMoreThanRegisteredIsRejected() {
        Long materialId = register(30L).materialId();

        assertThatThrownBy(() -> listingCommandService.register(supplierId, new ListingRegisterRequest(
                        materialId, 31L, MaterialQuantityUnit.KG, 4_000L)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(TradeErrorCode.LISTING_QUANTITY_EXCEEDS_AVAILABLE);
    }

    /**
     * 지갑이 없으면 소재를 등록할 수 없다.
     *
     * <p>{@code material_balance.wallet_address} 가 {@code NOT NULL} 이라 기술적으로도 값이
     * 필요하지만, 근거는 그보다 앞선다 — 소재를 소유하려면 지갑이 있어야 한다는 규칙이
     * 거래 생성에 이미 서 있다. 등록만 예외였다.
     *
     * <p><b>소재 행도 남지 않아야 한다.</b> 발행 실패가 등록 트랜잭션을 롤백하지 않으면
     * 팔 수도 가공에 넣을 수도 없는 소재가 조회에만 뜬다.
     */
    @Test
    void registrationIsRejectedWhenTheRegistrantHasNoWallet() {
        User walletless = userRepository.save(User.register(
                "issue-nowallet@example.com", "{noop}x", "지갑없음", "010-9001-0000", UserRole.SUPPLIER));

        assertThatThrownBy(() -> registrationService.register(walletless.getId(), request(10L)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(MaterialErrorCode.MATERIAL_WALLET_NOT_LINKED);

        assertThat(materialRepository.findAll())
                .as("발행이 실패하면 소재 행도 롤백되어야 한다")
                .isEmpty();
    }

    private MaterialRegisterResponse register(long kg) {
        return registrationService.register(supplierId, request(kg));
    }

    private MaterialRegisterRequest request(long kg) {
        return new MaterialRegisterRequest(
                MaterialType.DENIM,
                MaterialRootOrigin.WASTE,
                "발행 확인용 데님",
                null,
                "A",
                kg,
                MaterialQuantityUnit.KG,
                null,
                null,
                null,
                null,
                List.of());
    }

    private void clean() {
        eventRepository.deleteAll();
        balanceRepository.deleteAll();
        materialRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }
}
