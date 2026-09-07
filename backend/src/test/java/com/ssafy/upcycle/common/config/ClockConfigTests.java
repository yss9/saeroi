package com.ssafy.upcycle.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/**
 * 애플리케이션 {@link Clock} 의 시간대가 JVM 기본과 같아야 한다.
 *
 * <p>이 테스트가 막는 것은 <b>같은 행 안에서 시각이 어긋나는 상황</b>이다.
 * {@code created_at} 은 Spring auditing 이 {@code LocalDateTime.now()}(JVM 기본)로 쓰고,
 * {@code expires_at} 은 서비스가 {@code LocalDateTime.now(clock)} 으로 쓴다.
 * 두 시간대가 다르면 한 행 안에서 만료가 생성보다 과거인 데이터가 만들어진다.
 *
 * <p>실제로 그랬다 — 빈이 {@code Clock.systemUTC()} 였고 JVM 은 {@code Asia/Seoul} 이라
 * 9시간 어긋났다. 응답 봉투의 {@code timestamp}(KST)와 {@code data.expiresAt}(UTC)도
 * 어긋나 클라이언트가 보면 이미 만료된 값으로 보였다.
 *
 * <p>UTC 로 통일하는 선택도 유효하지만, 그때는 auditing·MySQL 기본값·JDBC
 * {@code serverTimezone} 을 함께 돌려야 한다. <b>이 테스트는 "UTC 를 쓰지 마라" 가 아니라
 * "한 곳만 다르게 하지 마라" 를 고정한다.</b>
 */
class ClockConfigTests {

    private final Clock clock = new ClockConfig().clock();

    @Test
    void usesTheSameZoneAsTheJvm() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.systemDefault());
    }

    @Test
    void agreesWithTheTimeSpringAuditingWrites() {
        // auditing 과 ApiResponse 는 LocalDateTime.now() 를 쓴다. 같은 순간을 읽었을 때
        // 둘의 차이가 몇 초를 넘으면 시간대가 어긋난 것이다 (어긋나면 9시간 차가 난다).
        LocalDateTime viaClock = LocalDateTime.now(clock);
        LocalDateTime viaDefault = LocalDateTime.now();

        assertThat(Math.abs(ChronoUnit.SECONDS.between(viaClock, viaDefault)))
                .as("clock 과 JVM 기본 시각이 어긋나면 created_at 과 expires_at 이 갈라진다")
                .isLessThan(5);
    }

    @Test
    void reportsTheSameInstantRegardlessOfZone() {
        // JWT 는 clock.instant() 만 쓴다. Instant 는 절대 시각이라 시간대를 바꿔도
        // 값이 같다 — 이 빈을 고쳐도 토큰 유효성에 영향이 없다는 근거다.
        assertThat(Math.abs(ChronoUnit.SECONDS.between(clock.instant(), Clock.systemUTC().instant())))
                .isLessThan(5);
    }
}
