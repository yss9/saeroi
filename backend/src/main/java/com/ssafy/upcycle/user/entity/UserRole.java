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

    /**
     * 소재를 등록할 수 있는 역할인가.
     *
     * <p>{@code SUPPLIER} 와 {@code BRAND} 만이다. <b>{@code PROCESSOR} 가 빠진 것이 핵심이다</b> —
     * 가공업체가 만드는 소재는 가공 확정의 산출물({@code PROCESSED})이지 직접 등록하는 대상이
     * 아니다. 등록을 열어 주면 가공 이력 없는 소재를 가공업체 이름으로 올릴 수 있게 되고,
     * 그러면 "모든 폐소재는 등록 가공업체를 거쳐 상태·수량이 검증된다" 는 전제가 무너진다.
     * {@code CONSUMER}·{@code ADMIN} 은 공급 주체가 아니다.
     *
     * <p>{@link #canOwnWallet} 과 목록이 <b>다르다</b>. 지갑을 가질 수 있다는 것과 소재를
     * 올릴 수 있다는 것은 별개 권한이며, 한 판정으로 합치면 둘 중 하나가 조용히 넓어진다.
     */
    public boolean canRegisterMaterial() {
        return this == SUPPLIER || this == BRAND;
    }
}
