package com.ssafy.upcycle.user.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.PasswordChangeErrorCode;
import com.ssafy.upcycle.user.dto.PasswordChangeRequest;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 변경. <b>역할과 무관한 계정 기능이라 모든 역할이 쓴다.</b>
 *
 * <p>소비자 개인정보 수정({@code ConsumerProfileService})과 나눈 이유가 이것이다. 이름·연락처는
 * 소비자와 사업자의 형식 규칙이 서로 다르지만, 비밀번호는 같은 규칙이고 지금까지 <b>어느
 * 역할도 바꿀 방법이 없었다.</b>
 *
 * <h2>로그인 경로를 부르지 않는다</h2>
 *
 * <p>검증만 하려고 {@code EmailLoginService} 를 부르면 성공할 때마다 Refresh Token 이 한 벌
 * 더 발급된다. 같은 {@link PasswordEncoder} 빈으로 대조만 한다.
 *
 * <p>로그인이 쓰는 더미 해시(계정 존재 여부를 가리는 타이밍 방어)도 쓰지 않는다 — 이미
 * 인증을 통과한 요청이라 감출 대상이 없다.
 *
 * <h2>바꾸면 모든 세션이 끊긴다</h2>
 *
 * <p>비밀번호를 바꾸는 이유 중 하나가 계정 탈취 의심이다. 그때 침입자의 세션이 살아 있으면
 * 바꾼 의미가 없다. <b>본인의 다른 기기도, 지금 쓰는 세션도 함께 끊긴다.</b>
 */
@Service
@RequiredArgsConstructor
public class PasswordChangeService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    /**
     * 현재 비밀번호를 확인하고 새 비밀번호로 바꾼다.
     *
     * <p>순서가 정해져 있다 — <b>대조 → 같은 값 거부 → 저장 → 세션 폐기</b>. 폐기를 먼저 하면
     * 현재 비밀번호를 틀린 요청도 남의 세션을 끊을 수 있다.
     *
     * <p>정지된 계정도 막지 않는다. 정지는 이용 제한이지 계정 소유권을 빼앗는 것이 아니고,
     * 비밀번호를 못 바꾸게 하면 탈취가 의심되는 계정이 그대로 남는다.
     */
    @Transactional
    public void change(Long userId, PasswordChangeRequest request) {
        User user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        PasswordChangeErrorCode.PASSWORD_CHANGE_ACCOUNT_NOT_FOUND));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException(PasswordChangeErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            // 막지 않으면 "변경되었습니다" 를 받고도 바뀐 것 없이 다시 로그인만 하게 된다.
            throw new BusinessException(PasswordChangeErrorCode.PASSWORD_SAME_AS_CURRENT);
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAllFor(userId);
    }
}
