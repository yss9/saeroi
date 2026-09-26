package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.agent.project.AgentProject;
import com.ssafy.upcycle.agent.project.AgentProjects;
import com.ssafy.upcycle.agent.service.AgentPendingWork;
import com.ssafy.upcycle.agent.service.ConversationListings;
import com.ssafy.upcycle.agent.service.OpenCards;
import com.ssafy.upcycle.agent.service.step.BrandStepResolver;
import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.service.MaterialQueryService;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.product.service.ProductModelQueryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 시스템 프롬프트의 상태 블록에 <b>지금 보유한 소재</b>가 서버 값으로 들어간다.
 *
 * <p>이게 없던 동안 모델은 소재 번호를 대화 기억에서 꺼냈다. 가공을 거치면 결과물이 새 번호로
 * 발행되는데 모델은 가공 전 번호(34·35·36)를 넘겼고, 실제 보유는 40·41·42 였다. 양도 모델이
 * gram 을 직접 환산하다 94,000g 을 "94g" 이라고 말한 적이 있어, 읽을 수 있는 문장으로 준다.
 *
 * <p><b>이 프로젝트의 소재를 먼저, 따로 적는다.</b> 대화 하나는 제품 하나라, 섞어 두면 모델이
 * 홈페이지에서 산 다른 소재를 이 제품에 넣자고 먼저 꺼낸다({@link AgentProject}).
 */
class AgentHoldingsBlockTests {

    private MaterialQueryService materialQuery;
    private AgentProjects projects;
    private AgentPendingWork pendingWork;

    @BeforeEach
    void setUp() {
        materialQuery = mock(MaterialQueryService.class);
        projects = mock(AgentProjects.class);
        // 남은 일도, 떠 있는 카드도 없는 상태 — mock 의 기본값이 빈 목록이다.
        pendingWork = new AgentPendingWork(
                mock(BrandStepResolver.class),
                mock(OpenCards.class),
                materialQuery,
                projects,
                mock(ProductModelQueryService.class),
                mock(ConversationListings.class));
    }

    @Test
    void 이_프로젝트의_소재를_먼저_따로_적고_나머지는_그_밖으로_둔다() {
        givenProject(new AgentProject.Builder().trade(1L).material(42L).build());
        givenHeld(
                material(35L, "폴리에스터 원단", MaterialRootOrigin.WASTE, 0, List.of()),
                material(42L, "세척 폴리에스터 원단", MaterialRootOrigin.WASTE, 94_000,
                        List.of(ProcessType.CLEANING)),
                material(41L, "신규 안감", MaterialRootOrigin.VIRGIN, 12_500, List.of()));

        String block = pendingWork.describe(1L, 10L);

        assertThat(block).contains("## 이 프로젝트의 소재");
        assertThat(block).contains("- 소재 42 · 세척 폴리에스터 원단 · 폐소재 · 사용 가능 94kg · 거친 공정 CLEANING");
        assertThat(block).contains("### 그 밖에 보유한 소재");
        assertThat(block).contains("- 소재 41 · 신규 안감 · 신소재 · 사용 가능 12.5kg");
        assertThat(block.indexOf("소재 42")).isLessThan(block.indexOf("그 밖에 보유한 소재"));
        assertThat(block.indexOf("그 밖에 보유한 소재")).isLessThan(block.indexOf("소재 41"));
        assertThat(block).contains("먼저 권하지 않는다");
        // 가공에 다 넘어가 남은 게 없는 원래 소재는 고를 수 없으니 싣지 않는다.
        assertThat(block).doesNotContain("소재 35");
    }

    /** 새 대화에서는 가진 소재 전부가 고를 거리다. 가진 소재로 가공부터 시작할 수 있다. */
    @Test
    void 아직_시작한_일이_없으면_보유_소재_전부를_보이고_시작_안내를_붙인다() {
        givenProject(AgentProject.empty());
        givenHeld(
                material(42L, "세척 폴리에스터 원단", MaterialRootOrigin.WASTE, 94_000, List.of()),
                material(41L, "신규 안감", MaterialRootOrigin.VIRGIN, 12_500, List.of()));

        String block = pendingWork.describe(1L, 10L);

        assertThat(block).contains("아직 이 대화에서 시작한 일이 없다");
        assertThat(block).contains("## 지금 보유한 소재");
        assertThat(block).doesNotContain("이 프로젝트의 소재");
        // 최근 번호가 위로 온다 — 지금 쓰는 것은 대개 방금 돌아온 결과물이다.
        assertThat(block.indexOf("소재 42")).isLessThan(block.indexOf("소재 41"));
    }

    @Test
    void 쓸_수_있는_소재가_없으면_소재_블록을_붙이지_않는다() {
        givenProject(new AgentProject.Builder().trade(1L).build());
        givenHeld(material(35L, "폴리에스터 원단", MaterialRootOrigin.WASTE, 0, List.of()));

        String block = pendingWork.describe(1L, 10L);

        assertThat(block).doesNotContain("보유한 소재").doesNotContain("프로젝트의 소재");
    }

    /** 보유 조회가 흔들려도 대화는 이어진다. 모델은 get_my_materials 로 볼 수 있다. */
    @Test
    void 보유_조회가_실패해도_상태_블록은_만들어진다() {
        givenProject(AgentProject.empty());
        when(materialQuery.findMyMaterials(any())).thenThrow(new IllegalStateException("db down"));

        String block = pendingWork.describe(1L, 10L);

        assertThat(block).contains("아직 이 대화에서 시작한 일이 없다");
        assertThat(block).doesNotContain("보유한 소재");
    }

    private void givenProject(AgentProject project) {
        when(projects.of(any(), any())).thenReturn(project);
    }

    /** 목록을 먼저 만든 뒤 넘긴다 — 만드는 중에 다른 stub 을 열면 Mockito 가 거절한다. */
    private void givenHeld(MyMaterialResponse... held) {
        List<MyMaterialResponse> list = List.of(held);
        when(materialQuery.findMyMaterials(any())).thenReturn(list);
    }

    private MyMaterialResponse material(
            Long id, String name, MaterialRootOrigin origin, long available, List<ProcessType> processes) {
        MyMaterialResponse held = mock(MyMaterialResponse.class);
        when(held.materialId()).thenReturn(id);
        when(held.name()).thenReturn(name);
        when(held.rootOrigin()).thenReturn(origin);
        when(held.available()).thenReturn(available);
        when(held.completedProcessTypes()).thenReturn(processes);
        return held;
    }
}
