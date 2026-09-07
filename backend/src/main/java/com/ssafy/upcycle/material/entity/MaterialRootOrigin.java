package com.ssafy.upcycle.material.entity;

/**
 * 소재의 출처. {@link MaterialGenerationType} 과는 다른 축이다 — 출처는 가공을 거쳐도 바뀌지 않는다.
 *
 * <p>자식 소재를 만들 때 부모 값을 그대로 복사한다. 덕분에 업사이클 비율을 계산할 때
 * 혈통 트리를 재귀 조회하지 않고 이 컬럼 하나만 보면 된다.
 */
public enum MaterialRootOrigin {

    /** 새 원단 또는 신규 원재료. */
    VIRGIN,

    /** 최초 등록된 폐의류·폐원단·데드스톡. */
    WASTE
}
