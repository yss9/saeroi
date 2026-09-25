package com.ssafy.upcycle.agent.entity;

/**
 * 대화가 지금 어느 단계에 있는지.
 *
 * <p>화면의 진행 표시와 시스템 프롬프트의 "현재 단계" 안내를 고르는 데 쓴다. <b>tool 을
 * 가리지는 않는다</b> — 예전에는 단계별로 tool 을 좁혔는데, 단계를 옮기지 않은 모델이 할 수
 * 있는 일을 "못 한다" 고 답하는 사고가 반복돼 전부 열었다({@code AgentToolRegistry} 참고).
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

    /** 여정이 끝났다. */
    DONE
}
