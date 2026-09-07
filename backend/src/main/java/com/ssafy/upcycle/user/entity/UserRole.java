package com.ssafy.upcycle.user.entity;

/**
 * users.role ENUM 과 1:1 로 대응한다.
 *
 * <p>이 이름이 그대로 JWT 의 {@code roles} 클레임에 들어가고, 읽는 쪽에서
 * {@code JwtAuthorityConverter} 가 {@code ROLE_} 접두어를 붙인다.
 * 따라서 여기에 {@code ROLE_} 를 넣으면 안 된다 — {@code JwtTokenService} 가 거부한다.
 */
public enum UserRole {
    SUPPLIER,
    BRAND,
    PROCESSOR,
    CONSUMER,
    ADMIN;

    /**
     * 지갑을 가질 수 있는 역할인가.
     *
     * <p>정책상 {@code user_wallet} 행은 SUPPLIER / BRAND / PROCESSOR 만 갖는다.
     * CONSUMER 는 지갑 없이 서비스를 쓰고, ADMIN 은 온체인 작업 주체가 아니다.
     *
     * <p><b>DB 에는 이걸 막는 제약이 없다.</b> 시드나 향후 등록 API 가 잘못 넣으면
     * 지갑 로그인이 그 계정에 토큰을 발급해 버리므로, 역할 경계를 코드에서 강제한다.
     * 역할 목록을 여기 한 곳에만 두어 지갑 등록 API 가 생겨도 같은 기준을 쓰게 한다.
     */
    public boolean canOwnWallet() {
        return this == SUPPLIER || this == BRAND || this == PROCESSOR;
    }
}
