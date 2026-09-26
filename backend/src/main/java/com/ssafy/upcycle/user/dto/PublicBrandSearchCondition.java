package com.ssafy.upcycle.user.dto;

/**
 * 비회원도 사용할 수 있는 브랜드 목록 조건.
 *
 * <p>상품 목록 조건({@code PublicProductModelSearchCondition})과 같은 모양을 쓴다 —
 * {@code keyword}·{@code sort}·{@code page}·{@code size}. 두 목록이 다른 규칙으로 페이지를
 * 세면 화면이 그 차이를 흡수해야 한다.
 *
 * <p>검색어가 없거나 공백뿐이면 조건이 붙지 않는다. 잘못된 값에 400 을 던지기보다 기본값으로
 * 접는 것이 목록 조회의 관례다.
 */
public record PublicBrandSearchCondition(
        String keyword,
        PublicBrandSort sort,
        Integer page,
        Integer size) {

    /** 한 페이지 기본 개수. 브랜드 카드는 상품 카드보다 크므로 상품(12)보다 적게 잡는다. */
    private static final int DEFAULT_SIZE = 8;

    /** 상한이 없으면 요청 한 번으로 전 브랜드를 끌어올 수 있다. 상품 목록과 같은 값이다. */
    private static final int MAX_SIZE = 100;

    public String keywordOrNull() {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    public PublicBrandSort sortOrDefault() {
        return sort == null ? PublicBrandSort.PRODUCT_COUNT : sort;
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
