package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.entity.ProductSubcategory;

/**
 * 마켓 제품 목록의 필터·페이지 조건.
 *
 * <p>전 필드가 {@code null} 을 허용한다 — 아무것도 주지 않으면 "판매 중인 제품 전체,
 * 최신순, 첫 페이지" 다. Bean Validation 을 붙이지 않은 것은 의도적이며 판매 글 목록
 * ({@code ListingSearchCondition}) 과 같은 판단이다: 목록 조회는 잘못된 값에 400 을
 * 던지기보다 기본값으로 접는 편이 낫다.
 *
 * <p><b>기본이 {@code ON_SALE} 인 것이 핵심이다.</b> {@code DRAFT} 는 브랜드가 준비 중인
 * 제품이라 마켓에 뜨면 안 된다. 브랜드 본인 화면은 {@code GET /me} 가 따로 있고 그쪽이
 * {@code DRAFT} 를 포함한다.
 *
 * <p><b>재고가 0 인 제품을 걸러내지 않는다.</b> 다 팔린 제품은 {@code SOLD_OUT} 으로
 * 전환되어야 하지만 그 전환은 주문 처리가 하고, 그 코드가 아직 없다. 여기서 숨기면
 * 브랜드는 자기 제품이 마켓에서 사라진 것을 보고도 이유를 알 수 없다. 응답의
 * {@code stockQuantity} 를 그대로 내려보내고 화면이 품절로 표시하는 편이 정직하다.
 */
public record ProductModelSearchCondition(
        ProductModelStatus status,
        ProductCategory category,
        ProductSubcategory subcategory,
        Long brandId,
        ProductModelSort sort,
        Integer page,
        Integer size) {

    /** 한 페이지 기본 개수. 카드 3열 배치에서 4줄이다. */
    private static final int DEFAULT_SIZE = 12;

    /**
     * 한 페이지 최대 개수.
     *
     * <p>상한이 없으면 {@code size=100000} 한 번으로 전체 테이블을 끌어올 수 있다.
     * 인증된 사용자만 접근하지만, 실수 한 번이 DB 를 멈추게 하는 경로는 막아둔다.
     */
    private static final int MAX_SIZE = 100;

    /** 기본은 판매 중인 제품만. 준비 중인 제품이 섞이면 마켓의 의미가 흐려진다. */
    public ProductModelStatus statusOrDefault() {
        return status == null ? ProductModelStatus.ON_SALE : status;
    }

    /**
     * 기본은 최신순. 새로 올라온 제품이 먼저 보이는 것이 목록의 기본 기대다.
     *
     * <p><b>상위 분류와 하위 분류를 함께 주면 교집합이다.</b> 둘이 맞지 않는 조합
     * ({@code category=BAG&subcategory=TOP})은 400 이 아니라 빈 목록이 된다 — 목록 조회는
     * 잘못된 조합에 실패하기보다 "그런 제품이 없다" 고 답하는 편이 낫다.
     */
    public ProductModelSort sortOrDefault() {
        return sort == null ? ProductModelSort.LATEST : sort;
    }

    public int pageOrDefault() {
        return page == null || page < 0 ? 0 : page;
    }

    public int sizeOrDefault() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
