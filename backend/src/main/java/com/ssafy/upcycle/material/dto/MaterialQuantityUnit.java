package com.ssafy.upcycle.material.dto;

/**
 * 등록 요청이 쓸 수 있는 수량 단위.
 *
 * <p><b>DB 에는 단위 컬럼이 없다.</b> {@code material.total_quantity} 는 항상 gram 정수이고,
 * 화면의 kg 표기는 표현 계층 책임이다. 그런데 gram 만 받으면 환산 책임이 모든 클라이언트로
 * 흩어지고, 자릿수를 잘못 곱한 요청을 서버가 구분할 방법이 없다.
 *
 * <p>그래서 요청은 {@code quantity} 와 {@code unit} 을 함께 받고 서버가 환산한다.
 * <b>단위 자체는 저장하지 않는다</b> — 저장 표현을 하나로 유지하면 스키마 변경이 필요 없다.
 *
 * <p>여기에 단위를 추가할 때는 정수 환산이 가능한 것만 넣는다. 예를 들어 파운드는
 * gram 으로 정확히 나누어떨어지지 않아 반올림 정책을 먼저 정해야 한다.
 */
public enum MaterialQuantityUnit {

    /** gram. 저장 단위와 같아 환산이 없다. */
    G(1L),

    /** kilogram. 1kg = 1000g. */
    KG(1_000L);

    private final long gramsPerUnit;

    MaterialQuantityUnit(long gramsPerUnit) {
        this.gramsPerUnit = gramsPerUnit;
    }

    /**
     * 이 단위의 수량을 gram 정수로 환산한다.
     *
     * <p>{@link Math#multiplyExact} 를 쓰는 것은 의도적이다. 곱셈이 {@code long} 범위를 넘으면
     * 조용히 음수로 감기는데, 그 값이 그대로 {@code total_quantity} 에 들어가면
     * {@code chk_material_quantity} 위반으로 설명할 수 없는 500 이 난다.
     * 여기서 {@link ArithmeticException} 을 던지고 호출부가 도메인 오류로 바꾼다.
     */
    public long toGrams(long quantity) {
        return Math.multiplyExact(quantity, gramsPerUnit);
    }
}
