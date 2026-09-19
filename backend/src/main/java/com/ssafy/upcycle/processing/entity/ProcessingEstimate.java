package com.ssafy.upcycle.processing.entity;

/**
 * 가공비와 소요일을 내는 <b>유일한 계산식</b>.
 *
 * <p>견적이 사라지면서 금액과 기간을 사람이 적지 않고 서버가 계산한다. 그 계산이 세 군데서
 * 필요하다 — 가공사 검색의 예상치, 요청 생성 시 확정 금액, 그리고 화면에 보여 줄 값이다.
 * 식이 흩어지면 <b>화면이 보여 준 금액과 실제로 청구되는 금액이 달라진다.</b> 그건 사용자가
 * 결제창에서야 알게 되는 종류의 오류다.
 *
 * <p>그래서 검색도 요청 생성도 이 클래스를 부른다. 화면은 계산하지 않고 서버가 준
 * {@code estimatedPrice}·{@code estimatedDays} 를 그대로 쓴다.
 *
 * <h2>왜 올림인가</h2>
 *
 * <p>수량은 gram 이고 단가는 kg 당이라 나누어떨어지지 않는다. 내림하면 1kg 미만이 공짜가
 * 되고, 반올림하면 같은 수량에 대해 올림과 내림이 섞여 "왜 이 금액인가" 를 설명할 수 없다.
 * 올림은 한 가지 규칙이고, 가공사가 손해 보지 않는 쪽이다.
 *
 * <p>소요일도 같다. 하루 처리량이 1000g 인 업체에 1500g 을 맡기면 이틀이다 — 1.5일짜리
 * 일정이라는 것은 없다.
 */
public final class ProcessingEstimate {

    /** gram 과 kg 사이. 수량은 전부 gram 정수로 다룬다 ({@code 02-domain-rules.md}). */
    private static final long GRAM_PER_KG = 1_000L;

    private ProcessingEstimate() {
    }

    /**
     * 가공비. {@code 단가(원/kg) × 수량(g) ÷ 1000}, 올림.
     *
     * @throws IllegalArgumentException 단가가 음수이거나 수량이 양수가 아닌 경우
     */
    public static long fee(long pricePerKg, long quantityGram) {
        if (pricePerKg < 0) {
            throw new IllegalArgumentException("Processing price per kg must not be negative");
        }
        requirePositiveQuantity(quantityGram);
        return ceilDiv(pricePerKg * quantityGram, GRAM_PER_KG);
    }

    /**
     * 소요일. {@code ⌈수량(g) ÷ 하루 처리량(g/일)⌉}, 최소 1일.
     *
     * <p>하루 처리량보다 적은 수량도 하루는 걸린다 — 0일에 끝나는 가공은 없다.
     *
     * @throws IllegalArgumentException 하루 처리량이나 수량이 양수가 아닌 경우
     */
    public static int leadDays(long dailyCapacity, long quantityGram) {
        if (dailyCapacity <= 0) {
            throw new IllegalArgumentException("Processing daily capacity must be positive");
        }
        requirePositiveQuantity(quantityGram);
        long days = ceilDiv(quantityGram, dailyCapacity);
        // 하루 처리량이 1g 이고 수량이 아주 크면 int 를 넘을 수 있다. 그런 일정은 어차피
        // 성립하지 않지만, 넘칠 때 음수가 되어 "마이너스 3일" 이 화면에 나가는 것은 막는다.
        return days > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) days;
    }

    private static void requirePositiveQuantity(long quantityGram) {
        if (quantityGram <= 0) {
            throw new IllegalArgumentException("Processing quantity must be positive");
        }
    }

    private static long ceilDiv(long dividend, long divisor) {
        return -Math.floorDiv(-dividend, divisor);
    }
}
