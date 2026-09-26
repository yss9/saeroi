package com.ssafy.upcycle.user.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.ConsumerProfileErrorCode;
import com.ssafy.upcycle.user.dto.ConsumerProfileUpdateRequest;
import com.ssafy.upcycle.user.dto.UserSummaryResponse;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소비자 본인의 개인정보 조회·수정. {@code ConsumerProfileController} 가 호출한다.
 *
 * <h2>연락처가 두 곳에 있다</h2>
 *
 * <p>여기서 고치는 것은 <b>계정 연락처({@code users.phone})</b> 하나다. 배송지의 수령인
 * 연락처({@code consumer_address.receiver_phone})는 건드리지 않는다.
 *
 * <p>가입 시점에는 두 값이 같다 — {@code SignupService.registerConsumer} 가 계정 정보를
 * 복사해 첫 배송지를 만든다. 그래서 같은 값으로 보이지만 <b>같은 값이어야 할 이유가
 * 없다.</b> 가족 집이나 회사로 받으면 받는 사람이 본인이 아니고, 여기서 함께 덮어쓰면
 * 사용자가 일부러 다르게 둔 값을 예고 없이 바꾸게 된다.
 *
 * <p>배송지는 {@code ConsumerAddressService} 가 맡는다.
 *
 * <h2>소비자만 쓴다</h2>
 *
 * <p>사업자는 {@code CompanyProfileService} 가 같은 {@code users.name}/{@code users.phone} 을
 * 고친다. 한 경로로 합치지 않은 이유는 <b>연락처 형식이 다르기 때문</b>이다 — 소비자는
 * 휴대전화만 받고, 사업자의 그 칸에는 유선번호({@code 02-...})가 들어간다.
 *
 * <p>역할은 DB 에서 다시 읽는다. 토큰은 발급 시점의 스냅샷이라 정지되거나 역할이 바뀐
 * 계정이 만료 전까지 통과한다 — 장바구니·찜과 같은 검사다.
 */
@Service
@RequiredArgsConstructor
public class ConsumerProfileService {

    private final UserRepository users;

    @Transactional(readOnly = true)
    public UserSummaryResponse myProfile(Long userId) {
        return UserSummaryResponse.from(requireActiveConsumer(userId));
    }

    /**
     * 이름·연락처를 바꾼다.
     *
     * <p>이메일은 받지 않는다 — 로그인 식별자라 바꾸려면 새 주소의 소유 확인이 필요하고,
     * 그건 이 경로가 할 일이 아니다.
     *
     * <p>연락처는 받은 문자열을 그대로 저장한다. 하이픈을 떼거나 붙이지 않는다 — 가입이
     * 그렇게 저장하고 있어, 여기서만 정규화하면 같은 계정의 값이 가입 전후로 달라진다.
     */
    @Transactional
    public UserSummaryResponse updateMyProfile(Long userId, ConsumerProfileUpdateRequest request) {
        User user = requireActiveConsumer(userId);
        user.updateContact(request.name().trim(), request.phone().trim());
        return UserSummaryResponse.from(user);
    }

    private User requireActiveConsumer(Long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ConsumerProfileErrorCode.CONSUMER_PROFILE_NOT_FOUND));
        if (!user.isActive() || user.getRole() != UserRole.CONSUMER) {
            throw new BusinessException(ConsumerProfileErrorCode.CONSUMER_PROFILE_FORBIDDEN);
        }
        return user;
    }
}
