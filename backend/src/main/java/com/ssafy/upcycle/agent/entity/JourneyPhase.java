package com.ssafy.upcycle.agent.entity;

/**
 * 대화가 지금 어느 단계에 있는지.
 *
 * <p>이 값의 진짜 용도는 화면 표시가 아니라 <b>LLM 에게 넘길 tool 을 고르는 것</b>이다.
 * 전체 여정의 tool 을 한꺼번에 넘기면 40개가 넘어가고, 그러면 모델이 엉뚱한 단계의 tool 을
 * 부르기 시작한다. 단계별로 10개 안쪽만 노출한다 ({@code AgentToolRegistry} 참고).
 *
 * <p>DB 에는 VARCHAR 로 저장한다. 단계는 앞으로 늘어날 값이라 DB ENUM 을 쓰면 값을 하나
 * 추가할 때마다 스키마 마이그레이션이 따라붙는다.
 */
public enum JourneyPhase {

    /** 무엇을 만들고 싶은지 듣고 부족한 정보를 되묻는 단계. 아직 아무것도 조회하지 않는다. */
    INTAKE,

    /** 요구사항에 맞는 소재를 찾아 구매까지 가는 단계. */
    MATERIAL_SOURCING,

    /** 가공업체를 찾고 견적을 비교해 선택·결제하는 단계. */
    PROCESSING,

    /** 확보한 소재로 생산 배치를 열고 투입을 확정하는 단계. */
    PRODUCTION,

    /** 제품 모델을 등록하고 QR 을 발행하는 단계. */
    PRODUCT,

    /** 여정이 끝났다. 더 이상 쓰기 tool 을 노출하지 않는다. */
    DONE
}
