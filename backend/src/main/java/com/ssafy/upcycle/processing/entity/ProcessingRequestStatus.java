package com.ssafy.upcycle.processing.entity;

/**
 * 가공 요청의 일생.
 *
 * <pre>
 *   REQUESTED ──(가공사 수락)──▶ ACCEPTED
 *       │
 *       ├──(가공사 거절)──▶ REJECTED
 *       ├──(브랜드 취소)──▶ CANCELED
 *       └──(기한 경과)────▶ EXPIRED
 * </pre>
 *
 * <p><b>견적 단계가 사라졌다.</b> 예전에는 {@code QUOTING}(견적 받는 중)에서 브랜드가 견적
 * 하나를 고르면 {@code SELECTED} 가 됐다. 이제 브랜드가 검색 결과에서 가격과 소요일을 보고
 * 바로 고르므로 받을 견적이 없고, 대신 <b>가공사가 받을지 말지를 답한다.</b>
 *
 * <p>그래서 두 값의 이름이 바뀌었다 — {@code QUOTING → REQUESTED},
 * {@code SELECTED → ACCEPTED}. 자리는 같지만 누가 다음 수를 두는지가 반대다: 예전에는
 * 브랜드가 골랐고 지금은 가공사가 받는다.
 *
 * <p>{@code REQUESTED} 구간에서 두 가지 기한이 차례로 흐른다 — 먼저 브랜드의 결제 기한,
 * 결제되면 가공사의 수락 기한이다. 둘 다 {@code ProcessingRequest#expiresAt} 하나를 쓴다
 * (결제 승인이 그 값을 수락 기한으로 밀어 준다). 어느 쪽이 지나도 {@code EXPIRED} 다.
 */
public enum ProcessingRequestStatus {

    /** 브랜드가 요청했고, 결제와 가공사의 수락을 기다린다. */
    REQUESTED,

    /** 가공사가 받았다. 여기서부터는 작업({@code processing_job})이 흐름을 이어받는다. */
    ACCEPTED,

    /** 브랜드가 물렀다. */
    CANCELED,

    /** 가공사가 거절했다. 결제가 끝난 뒤라면 환불이 함께 일어난다. */
    REJECTED,

    /** 결제 기한이나 수락 기한이 지났다. */
    EXPIRED
}
