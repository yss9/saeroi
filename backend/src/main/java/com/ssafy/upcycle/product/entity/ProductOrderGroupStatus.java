package com.ssafy.upcycle.product.entity;

/**
 * 주문 묶음의 결제 상태.
 *
 * <p>주문의 상태({@link ProductOrderStatus})보다 짧다. 발송·수령은 브랜드마다 따로 일어나므로
 * 주문이 갖는 사실이고, 묶음이 아는 것은 <b>한 번의 결제가 끝났는가</b> 뿐이다.
 *
 * <p>취소 상태가 없는 것도 같은 이유다 — 취소된 묶음은 주문과 마찬가지로 행을 남기지 않고
 * 이력으로 옮긴다({@code CanceledProductOrder} 문서 참고).
 */
public enum ProductOrderGroupStatus {

    /** 주문은 만들어졌고 개체는 잡혀 있다. 아직 청구되지 않았다. */
    CREATED,

    /** 승인이 끝나 실제로 청구됐다. 속한 주문들도 함께 결제된 것으로 본다. */
    PAID
}
