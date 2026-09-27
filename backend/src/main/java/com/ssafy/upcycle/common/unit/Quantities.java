package com.ssafy.upcycle.common.unit;

/**
 * 수량을 <b>사람이 읽는 문자열로</b> 바꾼다. 이 한 곳에서만 바꾼다.
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>저장·전송은 gram 정수이고 화면은 kg 이다. 그 변환이 흩어져 있으면 형식이 제각각이 되고,
 * 무엇보다 <b>변환 자체를 틀린다.</b>
 *
 * <p>실제로 에이전트가 잔량 94,000g 을 <b>"94g"</b> 이라고 말했다. 나눗셈은 맞았고 단위만
 * 틀렸는데, 그 숫자로 "필요한 12.5kg 에 한참 못 미친다" 는 결론을 내고 담당자에게 주문
 * 수량을 줄이라고 권했다. 실제로는 94kg 이라 넉넉했다.
 *
 * <p>모델에게 환산을 시키지 않으려면 서버가 <b>읽어서 그대로 옮길 문장</b>을 줘야 한다.
 * 그 문장을 만드는 곳이 여기다. 프론트의 {@code utils/quantity.ts} 와 짝이다.
 *
 * <h2>소수점</h2>
 *
 * <p>자리를 고정하지 않는다. 20,000g 을 "20.000kg" 으로 보여줄 이유가 없고 20,500g 은
 * "20.5kg" 이어야 한다. 1kg 미만도 kg 으로 말한다 — 한 화면에서 단위가 섞이면 비교가 안 된다.
 */
public final class Quantities {

    private static final long GRAMS_PER_KG = 1_000L;

    private Quantities() {
    }

    /** gram → 표시 문자열. 예: {@code 94000 → "94kg"}, {@code 2500 → "2.5kg"}. */
    public static String text(long gram) {
        if (gram % GRAMS_PER_KG == 0) {
            return "%,dkg".formatted(gram / GRAMS_PER_KG);
        }
        // 소수 셋째 자리까지. 1g 단위가 그대로 보이되 "20.000kg" 같은 꼬리는 남기지 않는다.
        String text = "%,.3f".formatted(gram / (double) GRAMS_PER_KG);
        return trimZeros(text) + "kg";
    }

    private static String trimZeros(String decimal) {
        String trimmed = decimal;
        while (trimmed.endsWith("0")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.endsWith(".") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
