package com.ssafy.upcycle.trade.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * {@code trade_code} 발급.
 *
 * <p><b>{@code MaterialCodeGenerator} 와 방식이 다르다.</b> 저쪽은 임시 코드로 저장한 뒤
 * 확정된 {@code id} 로 교체하는 2단계인데, 여기는 한 번에 만든다. 이유는 두 가지다.
 *
 * <ul>
 *   <li>{@code MaterialTrade} 에 코드 교체 메서드가 없다. 만들면 거래 코드가 저장 후에도
 *       바뀔 수 있는 값이 된다 — 결제 화면과 PG 주문번호에 이미 노출된 뒤라 곤란하다.</li>
 *   <li>거래 코드는 순번일 필요가 없다. 소재 코드와 달리 사람이 "몇 번째 소재" 로 부르지
 *       않고, 오히려 하루 거래량이 코드로 드러나지 않는 편이 낫다.</li>
 * </ul>
 *
 * <p>형식은 {@code TRD-20260907-<hex 12>} 로 25자다
 * ({@code trade_code} 는 {@code VARCHAR(30)}). 날짜를 앞에 두는 것은 CS 대응 때 사람이
 * 눈으로 시점을 알아보기 위해서고, 유일성은 전적으로 뒤쪽 무작위 48비트가 담당한다.
 *
 * <p><b>날짜는 주입된 {@link Clock} 기준이며 지금 그 빈은 {@code Clock.systemUTC()} 다</b>
 * ({@code JwtSecurityConfig#jwtClock}). 즉 한국 시간 오전 9시 이전에 만들어진 거래는 코드에
 * 전날 날짜가 찍힌다. 여기서만 KST 로 바꾸면 이 클래스 안에 시간 기준이 둘이 되므로 그러지
 * 않았다 — 서버 전체의 시간대 기준을 정하는 것이 맞는 해결이고, 그때 이 클래스는 따라온다.
 *
 * <p>{@link SecureRandom} 은 비밀이 필요해서가 아니라 같은 밀리초의 두 요청이 같은 값을
 * 뽑지 않게 하려는 것이다. 그래도 충돌하면 {@code uk_material_trade_code} 가 잡아낸다 —
 * 조용히 덮어쓰이는 것보다 실패하는 편이 낫다.
 */
@Component
public class TradeCodeGenerator {

    private static final String PREFIX = "TRD-";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int RANDOM_BYTES = 6; // hex 12자
    private static final int CODE_MAX_LENGTH = 30;

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    /**
     * {@link Clock} 을 주입받는다. 날짜 부분을 테스트가 고정할 수 있어야 하고,
     * {@code LocalDate.now()} 를 직접 부르면 자정 경계에서만 깨지는 테스트가 된다.
     */
    public TradeCodeGenerator(Clock clock) {
        this.clock = clock;
    }

    public String generate() {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        String code = PREFIX
                + LocalDate.now(clock).format(DATE_FORMAT)
                + "-"
                + HexFormat.of().formatHex(bytes);
        if (code.length() > CODE_MAX_LENGTH) {
            throw new IllegalStateException(
                    "Trade code must not exceed " + CODE_MAX_LENGTH + " characters: " + code);
        }
        return code;
    }
}
