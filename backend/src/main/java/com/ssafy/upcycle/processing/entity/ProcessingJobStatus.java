package com.ssafy.upcycle.processing.entity;

/**
 * 가공 작업의 상태.
 *
 * <pre>
 *   CREATED ──(결제)──▶ PAID ──(가공사 수락)──▶ ACCEPTED ──(소재 발송)──▶ SHIPPED
 *      │                 │                                                  │
 *      │                 └──(거절·무응답)──▶ DECLINED  (환불)          (소재 수령)
 *      │                                                                    ▼
 *      └──(결제 기한 경과)──▶ EXPIRED                                   RECEIVED
 *                                                                           │
 *   CONFIRMED ◀──(브랜드 승인)── RESULT_SUBMITTED ◀──(결과 제출)── PROCESSING
 *                                      │
 *                                      └──(브랜드 반려)──▶ REJECTED ──▶ RETURN_SHIPPED
 * </pre>
 *
 * <h2>{@link #ACCEPTED} 와 {@link #DECLINED} 가 새로 생긴 이유</h2>
 *
 * <p>견적이 사라지면서 <b>가공사가 일을 받을지 답하는 자리</b>가 필요해졌다. 예전에는
 * 견적을 낸다는 것 자체가 수락이었고, 그래서 {@code PAID} 다음이 곧장 {@code SHIPPED} 였다.
 * 이제는 브랜드가 먼저 결제하고 가공사가 답하므로, 그 사이에 한 칸이 있어야 한다 —
 * 없으면 브랜드가 돈을 낸 직후에 소재를 보내고, 가공사는 받고 나서야 못 하겠다고 말한다.
 *
 * <h2>{@link #DECLINED} 와 {@link #REJECTED} 는 다른 일이다</h2>
 *
 * <p>{@code DECLINED} 는 <b>일 자체를 안 받는다</b>(작업 시작 전, 환불 대상)이고,
 * {@code REJECTED} 는 <b>나온 결과가 기대와 다르다</b>(브랜드의 결과 반려, 재작업 또는
 * 반송)다. 한 값으로 묶으면 "거절된 가공" 이 환불 대상인지 반송 대상인지 알 수 없다.
 */
public enum ProcessingJobStatus {

    CREATED,
    PAID,

    /** 가공사가 일을 받았다. 이제 브랜드가 소재를 보낸다. */
    ACCEPTED,

    SHIPPED,
    RECEIVED,
    PROCESSING,
    RESULT_SUBMITTED,
    RETURN_SHIPPED,
    CONFIRMED,

    /** 브랜드가 <b>결과</b>를 반려했다. */
    REJECTED,

    /** 가공사가 <b>일</b>을 거절했거나 수락 기한 안에 답하지 않았다. 결제분은 환불된다. */
    DECLINED,

    EXPIRED,
    CANCELED
}
