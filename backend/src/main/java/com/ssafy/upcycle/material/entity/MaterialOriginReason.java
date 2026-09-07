package com.ssafy.upcycle.material.entity;

/**
 * {@code material.origin_reason} — <b>왜 폐기물이 되었나.</b>
 *
 * <p>{@link MaterialGenerationType} 과 역할이 다르다. 그쪽은 <b>어떻게 생겼나</b>
 * (최초 등록 / 가공 산출 / 생산 자투리)를 말한다. 그래서 이 목록에는
 * {@code PRODUCTION_SCRAP} 을 <b>넣지 않았다</b> — 같은 사실을 두 컬럼에 적으면 반드시
 * 어긋나고, 어긋났을 때 어느 쪽이 맞는지 판정할 방법이 없다.
 *
 * <p><b>자유 텍스트로 두지 않은 이유.</b> {@code grade} 가 {@code VARCHAR(50)} 인 결과를
 * 보면 안다 — 같은 뜻을 "A급 · 상 · Grade A" 로 다르게 적으면 필터도 정렬도 통계도 안 된다.
 * 발생 사유는 업사이클 비율 리포트의 분류축이 될 값이라 표준화가 특히 중요하다.
 *
 * <p>{@link #ETC} 를 두고 메모를 붙이게 한 이유는, 목록에 없는 경우를 억지로 다른 값에
 * 밀어 넣는 것을 막기 위해서다. 밀어 넣으면 그 소재는 영구히 잘못 분류된다.
 *
 * <p>근거: {@code docs/superpowers/specs/2026-09-07-material-composition-and-origin-reason.md}
 */
public enum MaterialOriginReason {

    /** 팔리지 않고 남은 재고 원단 */
    DEADSTOCK,

    /** 불량·B급으로 정상 유통에서 빠진 것 */
    DEFECT,

    /** 재단 후 남은 자투리 */
    OFFCUT,

    /** 사용을 마친 제품에서 나온 것 */
    END_OF_LIFE,

    /** 수거·회수 경로로 모인 것 */
    COLLECTED,

    /** 위에 없음. {@code origin_reason_note} 가 필수다 */
    ETC;

    /** {@link #ETC} 만 설명을 요구한다. 나머지는 값 자체가 뜻을 담고 있다. */
    public boolean requiresNote() {
        return this == ETC;
    }
}
