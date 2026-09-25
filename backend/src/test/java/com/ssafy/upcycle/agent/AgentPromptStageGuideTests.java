package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.service.AgentPromptBuilder;
import org.junit.jupiter.api.Test;

/**
 * 시스템 프롬프트에는 <b>모든 단계의 안내</b>가 늘 들어간다.
 *
 * <p>예전에는 대화의 지금 단계 것만 실었다. 단계는 카드가 끝나야 넘어가는데 도구는 늘 열려 있어서, 폐소재를
 * 산 직후("소재 확보") 모델이 가공 안내도 생산 안내도 없이 생산 배치 카드부터 띄웠다.
 */
class AgentPromptStageGuideTests {

    private final AgentPromptBuilder builder = new AgentPromptBuilder();

    @Test
    void 소재를_사는_중에도_가공과_생산과_제품_안내가_들어_있다() {
        String prompt = builder.build(conversationAt(JourneyPhase.MATERIAL_SOURCING));

        assertThat(prompt)
                .contains("## 소재를 찾고 살 때")
                .contains("## 가공을 맡길 때")
                .contains("## 생산할 때")
                .contains("## 제품을 등록하고 판매할 때")
                // 생산 전에 필수 공정을 확인하라는 규칙이 이 단계에서도 보인다.
                .contains("**배치를 열기 전에** 투입할 소재마다 `check_processing_plan`")
                .contains("# 지금 화면의 진행 단계: 소재를 찾고 살 때");
    }

    @Test
    void 안내_안의_절_제목은_단계_제목보다_한_단계_아래다() {
        String prompt = builder.build(conversationAt(JourneyPhase.INTAKE));

        assertThat(prompt).contains("\n### 필수 공정은 지어내지 않습니다");
        assertThat(prompt).doesNotContain("\n## 필수 공정은 지어내지 않습니다");
    }

    @Test
    void 가공_안내는_담당자_차례만_알리고_체인_용어를_쓰지_않게_한다() {
        String prompt = builder.build(conversationAt(JourneyPhase.PROCESSING));

        assertThat(prompt)
                .contains("### 담당자에게는 흐름을 한 번에, 담당자의 말로 알립니다")
                .contains("**담당자 차례가 됐을 때만** 먼저 알립니다")
                .contains("\"업체가 작업을 확인하는 중\"");
    }

    @Test
    void 업사이클링_제품이면_폐소재를_먼저_권한다() {
        assertThat(builder.build(conversationAt(JourneyPhase.INTAKE)))
                .contains("**업사이클링 제품이면 폐소재(WASTE)를 먼저 권합니다.**");
    }

    @Test
    void 끝난_대화의_안내는_끝났을_때만_붙는다() {
        assertThat(builder.build(conversationAt(JourneyPhase.PRODUCT))).doesNotContain("여정이 끝났습니다");
        assertThat(builder.build(conversationAt(JourneyPhase.DONE))).contains("여정이 끝났습니다");
    }

    /** 저장하지 않으면 대화가 길어졌을 때 잊는다 — "지갑 10개" 를 가공 뒤에 다시 물었다. */
    @Test
    void 요구사항이_없으면_저장하라고_알린다() {
        assertThat(builder.build(conversationAt(JourneyPhase.INTAKE)))
                .contains("아직 저장된 것이 없습니다")
                .contains("**그 턴에 `save_requirement` 를 부릅니다.**");
    }

    @Test
    void 저장된_요구사항은_그대로_싣는다() {
        AgentConversation conversation = conversationAt(JourneyPhase.PROCESSING);
        conversation.updateRequirement("{\"productDescription\":\"울·데님 지갑\",\"productQuantity\":10}");

        assertThat(builder.build(conversation))
                .contains("\"productQuantity\":10")
                .doesNotContain("아직 저장된 것이 없습니다");
    }

    /** 서명 카드를 띄우자마자 조회하고는 "아직 대기 중" 과 "서명이 완료됐습니다" 를 한 메시지에 썼다. */
    @Test
    void 카드를_띄운_턴에는_결과를_조회하지_않게_한다() {
        assertThat(builder.build(conversationAt(JourneyPhase.PRODUCTION)))
                .contains("**카드를 띄운 턴에는 그 결과를 조회하지 않습니다.**");
    }

    @Test
    void 소재를_보내는_서명_두_번은_먼저_설명하게_한다() {
        assertThat(builder.build(conversationAt(JourneyPhase.PROCESSING)))
                .contains("### 소재를 보내는 서명 두 번은 먼저 설명합니다")
                .contains("**가공비 결제가 끝나면**");
    }

    /** 바지 100벌에 "5~8kg" 이라고 했다 — 한 벌에 50~80g, 실제보다 열 배 적었다. */
    @Test
    void 필요한_양을_추천할_때는_한_개에_드는_양부터_밝힌다() {
        assertThat(builder.build(conversationAt(JourneyPhase.INTAKE)))
                .contains("### 필요한 양을 추천해 달라고 하면 — 한 개에 드는 양부터 밝힙니다")
                .contains("**합계만 말하지 않습니다.**");
    }

    @Test
    void 담당자에게는_번호_대신_이름으로_말하게_한다() {
        assertThat(builder.build(conversationAt(JourneyPhase.PRODUCTION)))
                .contains("**담당자에게는 번호 대신 이름과 양으로 말합니다.**");
    }

    private static AgentConversation conversationAt(JourneyPhase phase) {
        AgentConversation conversation = AgentConversation.start(1L, "지갑");
        conversation.moveTo(phase);
        return conversation;
    }
}
