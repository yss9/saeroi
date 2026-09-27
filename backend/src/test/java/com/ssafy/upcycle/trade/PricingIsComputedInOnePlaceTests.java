package com.ssafy.upcycle.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 거래 금액은 <b>{@code TradePricing} 에서만</b> 계산한다.
 *
 * <h2>왜 테스트로 막는가</h2>
 *
 * <p>수량은 gram 정수이고 가격은 kg 당 원이라 곱한 뒤 1000 으로 나눠야 한다. 그 변환이
 * 흩어지면 <b>반올림 방향이 조금씩 달라져 1원씩 벌어진다.</b> 화면이 보여준 금액과 승인
 * 요청 금액이 다르면 서버가 {@code PAYMENT_AMOUNT_MISMATCH} 로 거절한다.
 *
 * <p>{@code TradePricing} 의 주석에 "이 한 곳에서만 계산한다" 고 적혀 있었는데도, 확인 카드에
 * 보일 금액을 만들면서 {@code Math.round(pricePerKg * (quantityGram / 1000.0))} 를 새로 썼다.
 * 정본은 버림이라 {@code 1,500g × 3,333원/kg} 에서 4,999원과 5,000원으로 갈렸다 — 카드에 보인
 * 금액과 실제 청구가 다른 상태다.
 *
 * <p>주석은 읽는 사람에게만 닿는다. 규칙이 지켜지는지는 테스트가 본다.
 */
class PricingIsComputedInOnePlaceTests {

    private static final Path MAIN = Path.of("src", "main", "java");

    /**
     * 금액 계산의 <b>정본</b>들.
     *
     * <p>둘인 이유는 반올림 정책이 다르기 때문이다. 거래 금액은 <b>버림</b>이다 — 화면이
     * 미리 보여준 금액과 승인 요청 금액이 같아야 결제가 통과한다. 가공비는 <b>올림</b>이다 —
     * 1g 단위로 깎이면 업체가 손해를 본다.
     *
     * <p>새 파일을 여기 더하기 전에 물어야 할 것: <b>정말 새 정책인가, 아니면 기존 것을
     * 옮겨 쓴 것인가.</b> 후자라면 그 함수를 부르면 된다.
     */
    private static final List<String> ALLOWED = List.of(
            "TradePricing.java",
            "ProcessingEstimate.java");

    /** {@code pricePerKg} 가 곱셈·나눗셈에 쓰이는 줄. 조회나 전달은 걸리지 않는다. */
    private static final Pattern ARITHMETIC = Pattern.compile(
            "pricePerKg\\s*[*/]|[*/]\\s*[a-zA-Z.()]*pricePerKg");

    @Test
    void 금액_계산은_TradePricing_밖에_없다() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (ALLOWED.contains(file.getFileName().toString())) {
                    continue;
                }
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int index = 0; index < lines.size(); index++) {
                    String line = lines.get(index);
                    if (isComment(line)) {
                        continue;
                    }
                    if (ARITHMETIC.matcher(line).find()) {
                        offenders.add("%s:%d  %s".formatted(
                                MAIN.relativize(file), index + 1, line.strip()));
                    }
                }
            }
        }

        assertThat(offenders)
                .as("""
                        금액은 TradePricing.totalPrice 로만 계산합니다.
                        여기서 직접 곱하면 반올림 방향이 달라져 화면 금액과 청구 금액이 어긋나고,
                        결제 승인이 PAYMENT_AMOUNT_MISMATCH 로 거절됩니다.""")
                .isEmpty();
    }

    private boolean isComment(String line) {
        String stripped = line.strip();
        return stripped.startsWith("//") || stripped.startsWith("*") || stripped.startsWith("/*");
    }
}
