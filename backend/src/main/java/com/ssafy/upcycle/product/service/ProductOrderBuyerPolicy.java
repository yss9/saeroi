package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.product.ProductErrorCode;
import com.ssafy.upcycle.user.entity.ConsumerAddress;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.ConsumerAddressRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 주문을 낼 수 있는 사람인지, 보낼 수 있는 주소인지.
 *
 * <p>단일 주문과 묶음 주문이 <b>같은 검사를 지나야 하기 때문에</b> 꺼냈다. 두 경로가 각자
 * 검사를 들고 있으면 한쪽만 고쳐지고, 그 한쪽이 보안 검사라면 그게 그대로 구멍이 된다.
 *
 * <p>옮긴 것뿐이고 판정은 그대로다 — 기존 주문 생성이 하던 것과 같은 순서, 같은 에러 코드다.
 */
@Component
@RequiredArgsConstructor
public class ProductOrderBuyerPolicy {

    private final UserRepository userRepository;
    private final ConsumerAddressRepository consumerAddressRepository;

    /** 조회와 같은 기준으로 판정한다 — 토큰의 역할 클레임이 아니라 DB 의 현재 역할이다. */
    public User requireActiveConsumer(Long buyerId) {
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new BusinessException(
                        ProductErrorCode.PRODUCT_ORDER_BUYER_NOT_FOUND));
        if (!buyer.isActive()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ORDER_BUYER_SUSPENDED);
        }
        if (buyer.getRole() != UserRole.CONSUMER) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ORDER_ROLE_NOT_ALLOWED);
        }
        return buyer;
    }

    /**
     * 본인 배송지인지 확인한다.
     *
     * <p>남의 배송지를 가리키면 "없음" 으로 답한다. 403 은 "그 id 는 존재하고 남의 것" 이라는
     * 사실을 흘리고, 그 값은 남의 집 주소다.
     */
    public ConsumerAddress requireOwnAddress(Long buyerId, Long addressId) {
        return consumerAddressRepository.findById(addressId)
                .filter(address -> address.isOwnedBy(buyerId))
                .orElseThrow(() -> new BusinessException(
                        ProductErrorCode.PRODUCT_ORDER_ADDRESS_NOT_FOUND));
    }
}
