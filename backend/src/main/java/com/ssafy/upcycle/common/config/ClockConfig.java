package com.ssafy.upcycle.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 애플리케이션 전체가 공유하는 {@link Clock}.
 *
 * <h2>왜 별도 설정 클래스인가</h2>
 *
 * <p>이 빈은 원래 {@code JwtSecurityConfig} 안에 {@code jwtClock()} 이라는 이름으로 있었다.
 * 이름과 위치가 "JWT 전용" 처럼 보였기 때문에 {@code Clock.systemUTC()} 가 무해해 보였고,
 * 실제로 JWT 에는 무해하다 — {@code JwtTokenService} 는 {@code clock.instant()} 만 쓰고
 * {@link java.time.Instant} 는 시간대와 무관한 절대 시각이다.
 *
 * <p><b>그런데 이 빈을 주입받는 클래스가 12개다.</b> JWT 를 뺀 11개는 모두
 * {@code LocalDateTime.now(clock)} 을 쓴다 — 지갑 nonce 만료, 가공 요청 만료,
 * 소재 잠금 만료 판정, 거래 코드 날짜, 결제·발송·수령 시각. 이들은 시간대에 따라 값이 바뀐다.
 *
 * <p>그래서 스코프가 드러나는 위치와 이름으로 옮겼다. 시간 기준은 보안 설정의 세부사항이
 * 아니라 애플리케이션 전체의 계약이다.
 *
 * <h2>왜 systemDefaultZone 인가</h2>
 *
 * <p>이 서버의 모든 시각 소스가 {@code Asia/Seoul} 이다.
 *
 * <pre>
 *   호스트                 timedatectl set-timezone Asia/Seoul  (init-ec2.sh)
 *   mysql 컨테이너         TZ=Asia/Seoul                        (docker-compose.yml)
 *   JDBC                   serverTimezone=Asia/Seoul
 *   backend JVM            TZ=Asia/Seoul, -Duser.timezone=Asia/Seoul  (docker/Dockerfile)
 *   Spring auditing        LocalDateTime.now()  → JVM 기본 = KST
 *   ApiResponse.timestamp  LocalDateTime.now()  → JVM 기본 = KST
 *   MySQL DEFAULT CURRENT_TIMESTAMP             → 컨테이너 TZ = KST
 * </pre>
 *
 * <p>여기서 이 빈만 UTC 였다. 결과로 <b>같은 행 안에서 {@code created_at}(KST)과
 * {@code expires_at}(UTC)이 9시간 어긋났고</b>, 응답 봉투의 {@code timestamp}(KST)와
 * {@code data.expiresAt}(UTC)도 어긋나 클라이언트가 보면 이미 만료된 값으로 보였다.
 * 서버 내부 비교는 같은 빈을 쓰므로 일관됐지만, 그 일관성은 우연이었다.
 *
 * <p>{@code systemDefaultZone()} 으로 스택 전체와 맞춘다. JVM 이 컨테이너에서
 * {@code Asia/Seoul} 로 고정돼 있으므로 결과는 KST 다.
 *
 * <p><b>JWT 는 영향받지 않는다.</b> {@code clock.instant()} 는 두 구현이 같은 값을 돌려준다.
 *
 * <p>UTC 저장으로 가는 선택도 있었지만 그러려면 컬럼 타입을 {@code TIMESTAMP} 로 바꾸고
 * auditing·MySQL 기본값·JDBC 설정을 모두 함께 돌려야 한다. 스키마를 방금 적용한 시점에
 * 그 범위를 건드리는 것보다, 이미 KST 로 정해진 스택에 이 빈 하나를 맞추는 편이 맞다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
