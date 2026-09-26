package com.ssafy.upcycle.user.service;

import static com.ssafy.upcycle.user.ConsumerAddressErrorCode.CONSUMER_ADDRESS_FORBIDDEN;
import static com.ssafy.upcycle.user.ConsumerAddressErrorCode.CONSUMER_ADDRESS_LIMIT_EXCEEDED;
import static com.ssafy.upcycle.user.ConsumerAddressErrorCode.CONSUMER_ADDRESS_NOT_FOUND;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.dto.ConsumerAddressRequest;
import com.ssafy.upcycle.user.dto.ConsumerAddressResponse;
import com.ssafy.upcycle.user.entity.ConsumerAddress;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.repository.ConsumerAddressRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CONSUMER 계정 본인의 배송지 주소록. {@code ConsumerAddressController} 가 호출한다.
 *
 * <p><b>대상 역할</b>은 CONSUMER 뿐이다({@code UserRole.hasDeliveryAddressBook()}). 사업자
 * 3종은 {@code company_profile} 이 주소를 갖고, 그쪽은 {@code CompanyProfileService} 가
 * 담당한다 — 한 계정이 두 주소 체계를 갖게 두면 "어디로 보내야 하는가" 에 답이 둘이 된다.
 *
 * <p><b>기본 배송지는 사용자당 최대 하나</b>다. MySQL 8 에는 부분 UNIQUE 인덱스가 없어
 * DB 가 이를 막지 못하므로 이 클래스가 유일한 안전장치다. 그래서 기본 지정은
 * {@link #makeDefault} 한 곳을 반드시 거치게 하고, 나머지 행을 내리는 일과 같은 트랜잭션에
 * 묶는다.
 *
 * <p><b>주소가 0건인 계정이 정상 상태다.</b> 주소 입력은 이번에 가입 폼에 추가된 것이라
 * 그 전에 가입한 소비자에게는 행이 없다. 그래서 마이그레이션으로 빈 값을 채워 넣지
 * 않는다 — 아무도 모르는 가짜 주소가 기본 배송지로 앉아 있는 편이 훨씬 위험하다. 목록이
 * 비면 화면이 "배송지를 추가해 주세요" 를 띄우면 되고, 주문 단계는 어차피 기본 배송지가
 * 없는 경우를 다뤄야 한다.
 */
@Service
@RequiredArgsConstructor
public class ConsumerAddressService {

    /**
     * 한 계정이 가질 수 있는 배송지 수.
     *
     * <p>상한이 없으면 로그인한 계정 하나가 호출 횟수만큼 행을 만들 수 있다. 실제 사람이
     * 쓰는 개수(집·회사·부모님 댁 정도)보다 넉넉하되, 목록 조회가 한 화면에 담기는 선에서
     * 정했다.
     */
    static final int MAX_ADDRESSES = 20;

    private final UserRepository users;
    private final ConsumerAddressRepository addresses;

    @Transactional(readOnly = true)
    public List<ConsumerAddressResponse> myAddresses(Long userId) {
        requireConsumer(userId);
        return addresses.findAllByUserIdOrderByIsDefaultDescIdAsc(userId).stream()
                .map(ConsumerAddressResponse::from)
                .toList();
    }

    @Transactional
    public ConsumerAddressResponse add(Long userId, ConsumerAddressRequest request) {
        requireConsumer(userId);
        if (addresses.countByUserId(userId) >= MAX_ADDRESSES) {
            throw new BusinessException(CONSUMER_ADDRESS_LIMIT_EXCEEDED);
        }

        ConsumerAddress saved = addresses.save(ConsumerAddress.register(
                userId,
                request.alias(),
                request.receiverName(),
                request.receiverPhone(),
                request.zipCode(),
                request.address(),
                request.addressDetail()));

        // 첫 배송지는 물어볼 것이 없다 — 고를 수 있는 것이 그것뿐이다.
        if (addresses.countByUserId(userId) == 1) {
            makeDefault(userId, saved);
        }
        return ConsumerAddressResponse.from(saved);
    }

    @Transactional
    public ConsumerAddressResponse update(
            Long userId, Long addressId, ConsumerAddressRequest request) {
        requireConsumer(userId);
        ConsumerAddress address = requireOwnAddress(userId, addressId);

        address.update(
                request.alias(),
                request.receiverName(),
                request.receiverPhone(),
                request.zipCode(),
                request.address(),
                request.addressDetail());
        return ConsumerAddressResponse.from(address);
    }

    /**
     * 배송지를 지운다. 마지막 한 건도 지울 수 있다.
     *
     * <p>"최소 한 건은 남겨야 한다" 로 막지 않는 이유는, 이 기능 이전에 가입한 소비자가
     * 이미 0건이기 때문이다. 그들에게는 허용되고 새 가입자에게만 금지되는 규칙은 설명할
     * 방법이 없다. 주문 단계가 어차피 0건을 다뤄야 하므로 여기서 억지로 막지 않는다.
     *
     * <p>지운 것이 기본 배송지였다면 남은 것 중 가장 먼저 등록한 것을 올린다. 기본 배송지가
     * 없는 채로 두면 다음 주문에서 화면이 아무것도 고르지 못한 상태로 시작한다.
     */
    @Transactional
    public void delete(Long userId, Long addressId) {
        requireConsumer(userId);
        ConsumerAddress address = requireOwnAddress(userId, addressId);
        boolean wasDefault = address.isDefault();

        addresses.delete(address);
        addresses.flush();

        if (wasDefault) {
            addresses.findAllByUserIdOrderByIsDefaultDescIdAsc(userId).stream()
                    .findFirst()
                    .ifPresent(next -> next.markDefault(true));
        }
    }

    @Transactional
    public ConsumerAddressResponse setDefault(Long userId, Long addressId) {
        requireConsumer(userId);
        ConsumerAddress address = requireOwnAddress(userId, addressId);
        makeDefault(userId, address);
        return ConsumerAddressResponse.from(address);
    }

    /**
     * 기본 배송지를 이 행으로 옮긴다.
     *
     * <p>먼저 내리고 나중에 올린다. 순서를 뒤집으면 잠깐이지만 기본이 둘인 상태가 생기고,
     * 같은 트랜잭션 안에서 목록을 읽는 코드가 그것을 본다.
     */
    private void makeDefault(Long userId, ConsumerAddress address) {
        addresses.findByUserIdAndIsDefaultTrue(userId)
                .filter(current -> !current.getId().equals(address.getId()))
                .ifPresent(current -> current.markDefault(false));
        address.markDefault(true);
    }

    private User requireConsumer(Long userId) {
        return users.findById(userId)
                .filter(user -> user.isActive() && user.getRole().hasDeliveryAddressBook())
                .orElseThrow(() -> new BusinessException(CONSUMER_ADDRESS_FORBIDDEN));
    }

    /** 남의 배송지는 "없음" 으로 답한다 — 이유는 {@code CONSUMER_ADDRESS_NOT_FOUND} 참고. */
    private ConsumerAddress requireOwnAddress(Long userId, Long addressId) {
        return addresses.findById(addressId)
                .filter(address -> address.isOwnedBy(userId))
                .orElseThrow(() -> new BusinessException(CONSUMER_ADDRESS_NOT_FOUND));
    }
}
