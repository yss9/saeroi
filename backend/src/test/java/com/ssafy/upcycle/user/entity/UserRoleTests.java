package com.ssafy.upcycle.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 지갑 보유 가능 역할은 정책 문장이 아니라 이 테스트로 고정한다.
 * DB 에 CONSUMER 지갑을 막는 제약이 없으므로 이 판정이 유일한 경계다.
 */
class UserRoleTests {

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"SUPPLIER", "BRAND", "PROCESSOR"})
    void businessRolesCanOwnAWallet(UserRole role) {
        assertThat(role.canOwnWallet()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CONSUMER", "ADMIN"})
    void consumerAndAdminCannotOwnAWallet(UserRole role) {
        assertThat(role.canOwnWallet()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"BRAND"})
    void onlyABrandCanRegisterAProduct(UserRole role) {
        assertThat(role.canRegisterProduct()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"SUPPLIER", "PROCESSOR", "CONSUMER", "ADMIN"})
    void everyOtherRoleCannotRegisterAProduct(UserRole role) {
        // 소재를 올릴 수 있다는 것과 제품을 팔 수 있다는 것은 별개 권한이다 —
        // SUPPLIER 가 여기 섞이면 소재 등록 권한이 제품 등록까지 조용히 넓어진 것이다.
        assertThat(role.canRegisterProduct()).isFalse();
    }
}
