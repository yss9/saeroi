package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.agent.project.AgentProject;
import com.ssafy.upcycle.agent.project.AgentProjects;
import com.ssafy.upcycle.agent.service.AgentPendingWork;
import com.ssafy.upcycle.agent.service.ConversationListings;
import com.ssafy.upcycle.agent.service.OpenCards;
import com.ssafy.upcycle.agent.service.step.BrandStepResolver;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.service.MaterialQueryService;
import com.ssafy.upcycle.product.service.ProductModelQueryService;
import com.ssafy.upcycle.trade.dto.ListingResponse;
import com.ssafy.upcycle.trade.entity.ListingStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 상태 블록에 <b>이 대화에서 찾은 판매 글</b>이 번호와 함께 들어간다. 구매할 때 번호를 여기서 고른다.
 *
 * <p>모델이 담당자에게 한 말에는 이름과 단가만 있고 번호는 검색 결과에만 있다. 이력이 잘려 검색 결과가
 * 사라지자 모델은 번호를 짐작해 합의한 것과 다른 소재를 샀다.
 */
class AgentListingsBlockTests {

    private ConversationListings conversationListings;
    private AgentPendingWork pendingWork;

    @BeforeEach
    void setUp() {
        conversationListings = mock(ConversationListings.class);
        AgentProjects projects = mock(AgentProjects.class);
        when(projects.of(any(), any())).thenReturn(AgentProject.empty());
        pendingWork = new AgentPendingWork(
                mock(BrandStepResolver.class),
                mock(OpenCards.class),
                mock(MaterialQueryService.class),
                projects,
                mock(ProductModelQueryService.class),
                conversationListings);
    }

    @Test
    void 찾은_판매_글을_번호_이름_지금_단가와_함께_적는다() {
        List<ListingResponse> found = List.of(
                listing(21L, "폐데님 원단", "B", MaterialRootOrigin.WASTE, 2_000, 100_000, ListingStatus.ON_SALE),
                listing(20L, "면 소재", "A", MaterialRootOrigin.WASTE, 5_000, 100_000, ListingStatus.ON_SALE));
        when(conversationListings.current(any(), anyInt())).thenReturn(found);

        String block = pendingWork.describe(1L, 19L);

        assertThat(block).contains("## 이 대화에서 찾은 판매 글");
        assertThat(block).contains("- 판매 글 21 · 폐데님 원단 · B등급 · 폐소재 · kg당 2,000원 · 남은 양 100kg");
        assertThat(block).contains("- 판매 글 20 · 면 소재 · A등급 · 폐소재 · kg당 5,000원 · 남은 양 100kg");
        assertThat(block).contains("listingId 는 **이 목록에서만** 고른다");
    }

    @Test
    void 지금_살_수_없는_판매_글은_그렇다고_적는다() {
        List<ListingResponse> found = List.of(
                listing(21L, "폐데님 원단", "B", MaterialRootOrigin.WASTE, 2_000, 0, ListingStatus.SOLD_OUT));
        when(conversationListings.current(any(), anyInt())).thenReturn(found);

        assertThat(pendingWork.describe(1L, 19L)).contains("지금은 살 수 없음(SOLD_OUT)");
    }

    @Test
    void 찾은_판매_글이_없으면_블록을_붙이지_않는다() {
        when(conversationListings.current(any(), anyInt())).thenReturn(List.of());

        assertThat(pendingWork.describe(1L, 19L)).doesNotContain("찾은 판매 글");
    }

    private ListingResponse listing(
            Long id, String name, String grade, MaterialRootOrigin origin, long pricePerKg, long available,
            ListingStatus status) {
        ListingResponse listing = mock(ListingResponse.class);
        when(listing.listingId()).thenReturn(id);
        when(listing.materialName()).thenReturn(name);
        when(listing.grade()).thenReturn(grade);
        when(listing.rootOrigin()).thenReturn(origin);
        when(listing.pricePerKg()).thenReturn(pricePerKg);
        when(listing.availableQuantity()).thenReturn(available);
        when(listing.status()).thenReturn(status);
        return listing;
    }
}
