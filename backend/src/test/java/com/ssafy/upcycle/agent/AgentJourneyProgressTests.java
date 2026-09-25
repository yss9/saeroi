package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.agent.dto.JourneyProgressResponse;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.repository.AgentConversationRepository;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import com.ssafy.upcycle.agent.service.AgentJourneyProgress;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.production.dto.ProductionBatchOpenRequest;
import com.ssafy.upcycle.production.dto.ProductionInputRequest;
import com.ssafy.upcycle.production.dto.ProductionInputsRequest;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 생산 진행 막대가 <b>지금 하는 일</b>을 가리키는가.
 *
 * <p>여기서 잡으려는 함정이 둘이다. 둘 다 실제로 화면에 틀린 안내를 띄웠다.
 *
 * <ol>
 *   <li><b>가장 오래된 배치를 따라갔다.</b> 지난 테스트에서 버려 둔 빈 배치가 목록 앞에
 *       있으면, 방금 확정하고 체인 기록까지 끝낸 배치가 아니라 그 빈 배치를 가리켰다 —
 *       다 끝냈는데 막대는 "투입할 소재를 정할 차례입니다" 라고 말했다. 이제는 이 대화에서
 *       연 배치만 본다.
 *   <li><b>투입 여부를 {@code totalUsedQuantity} 로 판정했다.</b> 그 값은 확정 시점에 제품에
 *       실제로 쓰인 양으로 채워지고 그 전까지 언제나 0 이다. 그래서 소재를 넣어 둔 배치도
 *       "아직 투입 안 됨" 으로 보였다.
 * </ol>
 */
@SpringBootTest
@Transactional
class AgentJourneyProgressTests {

    private static final String WALLET = "0x3333333333333333333333333333333333333333";

    @Autowired private AgentJourneyProgress progress;
    @Autowired private AgentConversationRepository conversations;
    @Autowired private UserRepository users;
    @Autowired private UserWalletRepository wallets;
    @Autowired private MaterialRepository materials;
    @Autowired private MaterialBalanceRepository balances;
    @Autowired private ProductionBatchService batchService;
    @Autowired private AgentPendingActionRepository cards;

    private Long brandId;
    private Long materialId;
    private Long conversationId;

    private com.ssafy.upcycle.agent.entity.AgentConversation conversation() {
        return conversations.findById(conversationId).orElseThrow();
    }

    @BeforeEach
    void setUp() {
        brandId = users.save(User.register(
                        "agent-progress@test.com", "{noop}x", "brand", "010-0000-0000", UserRole.BRAND))
                .getId();
        wallets.save(UserWallet.link(brandId, WALLET, 31221L));

        Material material = materials.save(Material.register(
                "MAT-920001", MaterialType.SILK, MaterialRootOrigin.VIRGIN, "실크 원단", null, "A",
                10_000L, null, null, brandId));
        materialId = material.getId();
        balances.save(MaterialBalance.of(materialId, brandId, WALLET, 10_000L));

        var conversation = conversations.save(
                com.ssafy.upcycle.agent.entity.AgentConversation.start(brandId, "생산"));
        conversation.moveTo(JourneyPhase.PRODUCTION);
        conversations.save(conversation);
        conversationId = conversation.getId();
    }

    /**
     * 막대는 <b>이 대화에서 연 배치</b>를 가리킨다. 더 최근에 연 배치가 있어도 그것이 홈페이지나
     * 지난 대화의 것이면 따라가지 않는다 — "가장 최근 배치" 는 서버가 이 대화의 배치를 모를 때
     * 쓰던 대리값이었다.
     */
    @Test
    void 더_최근_배치가_아니라_이_대화의_배치를_가리킨다() {
        Long mine = batchService
                .open(brandId, new ProductionBatchOpenRequest("지금 하는 배치", null, 5))
                .productionBatchId();
        batchService.open(brandId, new ProductionBatchOpenRequest("홈페이지에서 연 배치", null, 10));
        ProjectCards.openedBatch(cards, conversationId, mine);

        JourneyProgressResponse response = progress.of(brandId, conversation());

        assertThat(response.subject()).isEqualTo("지금 하는 배치");
    }

    @Test
    void 소재를_투입하면_투입_칸이_채워진다() {
        Long batchId = batchService
                .open(brandId, new ProductionBatchOpenRequest("투입한 배치", null, 5))
                .productionBatchId();
        batchService.addInputs(brandId, batchId, new ProductionInputsRequest(List.of(
                new ProductionInputRequest(materialId, 4_000L, 4_000L, 0L, 0L, null))));
        ProjectCards.openedBatch(cards, conversationId, batchId);

        JourneyProgressResponse response = progress.of(brandId, conversation());

        // 확정 전이라 batch.totalUsedQuantity() 는 0 이다. 투입 목록을 봐야 알 수 있다.
        assertThat(response.nextTodo()).isEqualTo("생산을 확정할 차례입니다. 되돌릴 수 없습니다.");
        assertThat(response.steps())
                .anySatisfy(step -> {
                    assertThat(step.label()).isEqualTo("소재 투입");
                    assertThat(step.done()).isTrue();
                });
    }
}
