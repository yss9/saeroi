package com.ssafy.upcycle.user.dto;

import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserStatus;
import java.time.LocalDateTime;

/**
 * 클라이언트에 내보내는 계정 정보.
 *
 * <p><b>{@code password} 는 여기 없다.</b> 엔티티에는 매핑돼 있으므로 무심코 필드를 늘리면
 * 그대로 새어 나간다. 지갑 주소도 넣지 않는다 — Frontend 는 방금 검증한 주소를 이미 알고 있고,
 * 서버가 굳이 한 번 더 실어 보낼 이유가 없다.
 *
 * <p>필드 구성은 Frontend 의 {@code UserProfile} 과 1:1 로 맞춘 것이다.
 */
public record UserSummaryResponse(
        Long id,
        String email,
        String name,
        String phone,
        UserRole role,
        UserStatus status,
        LocalDateTime createdAt) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt());
    }
}
