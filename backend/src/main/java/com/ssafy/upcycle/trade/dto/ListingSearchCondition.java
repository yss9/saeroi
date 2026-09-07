package com.ssafy.upcycle.trade.dto;

import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.trade.entity.ListingStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

/**
 * Marketplace 목록의 검색·필터·정렬·페이지 조건.
 *
 * <p>전 필드가 {@code null} 을 허용한다 — 아무것도 주지 않으면 "판매 중인 글 전체, 최신순,
 * 첫 페이지" 다. Bean Validation 을 붙이지 않은 것은 의도적이다. 목록 조회는 잘못된 값에
 * 400 을 던지기보다 <b>기본값으로 접는 편</b>이 낫다: 사용자가 URL 을 손으로 고쳤을 때
 * 빈 화면과 에러 화면 중 어느 쪽이 나은지의 문제이고, 여기서는 전자다.
 *
 * <p><b>가격 범위가 뒤집혀 있어도 그대로 넘긴다</b> ({@code min > max}). 결과가 0 건이 되며,
 * 그것이 사용자가 입력한 조건의 정직한 답이다. 조용히 바꿔치기하면 화면의 필터 UI 와
 * 결과가 어긋난다.
 */
public record ListingSearchCondition(
        ListingStatus status,
        Long sellerId,
        MaterialType materialType,
        MaterialRootOrigin rootOrigin,
        String grade,
        String keyword,
        Long minPricePerKg,
        Long maxPricePerKg,
        Long minAvailableQuantity,
        ListingSort sort,
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

    /** 기본은 판매 중인 글만. 품절·취소된 글이 섞이면 목록의 의미가 흐려진다. */
    public ListingStatus statusOrDefault() {
        return status == null ? ListingStatus.ON_SALE : status;
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

    /**
     * 공백만 들어온 검색어는 조건이 아니다.
     *
     * <p>빈 문자열을 그대로 넘기면 {@code like '%%'} 가 되어 <b>전건 일치</b>가 된다.
     * 결과는 같아 보이지만 인덱스를 못 타는 조건이 하나 더 붙는다.
     */
    public String keywordOrNull() {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    /** 등급도 같은 이유로 접는다. */
    public String gradeOrNull() {
        return grade == null || grade.isBlank() ? null : grade.trim();
    }

    /**
     * 정렬 조건.
     *
     * <p>{@link JpaSort#unsafe} 를 쓴다. 쿼리가 {@code MaterialListing l} 과
     * {@code Material m} 두 별칭을 쓰는 entity join 이라 정렬 프로퍼티도 별칭까지 적어야
     * 어느 테이블의 컬럼인지 정해진다. 일반 {@link Sort} 는 도메인 타입의 프로퍼티명만
     * 허용해서 {@code l.createdAt} 형태를 거부한다.
     *
     * <p>"unsafe" 는 문자열이 그대로 JPQL 에 들어간다는 뜻이라 주입 위험을 뜻하지만,
     * 값은 {@link ListingSort} 열거형이 정한 상수뿐이고 사용자 입력이 아니다.
     * 열거형에 없는 값은 Spring 이 바인딩 단계에서 거부한다.
     */
    public Sort toSort() {
        return (sort == null ? ListingSort.LATEST : sort).toJpaSort();
    }

    /** 정렬 기준. 문자열이 아니라 열거형인 이유는 {@link #toSort()} 주석 참고. */
    public enum ListingSort {

        /** 최신 등록순. */
        LATEST(Sort.Direction.DESC, "l.createdAt"),

        /** kg 단가 낮은 순. */
        PRICE_ASC(Sort.Direction.ASC, "l.pricePerKg"),

        /** kg 단가 높은 순. */
        PRICE_DESC(Sort.Direction.DESC, "l.pricePerKg"),

        /** 잔여 수량 많은 순. 대량 매입을 찾는 브랜드가 쓴다. */
        QUANTITY_DESC(Sort.Direction.DESC, "l.availableQuantity");

        /**
         * 마지막 정렬 키. <b>이것이 없으면 페이지가 깨진다.</b> 단가나 등록 시각이 같은 행이
         * 여러 개일 때 DB 는 순서를 보장하지 않는다. 그러면 2페이지에 1페이지에서 본 글이
         * 다시 나오거나 어떤 글은 아예 안 보인다. {@code id} 는 유일하므로 순서를 확정한다.
         */
        private static final String TIE_BREAKER = "l.id";

        private final Sort.Direction direction;
        private final String expression;

        ListingSort(Sort.Direction direction, String expression) {
            this.direction = direction;
            this.expression = expression;
        }

        private Sort toJpaSort() {
            return JpaSort.unsafe(direction, expression)
                    .andUnsafe(Sort.Direction.DESC, TIE_BREAKER);
        }
    }
}
