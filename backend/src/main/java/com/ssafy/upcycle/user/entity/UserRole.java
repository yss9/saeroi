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
    ADMIN
}
