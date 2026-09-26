package com.ssafy.upcycle.inquiry.service;

import static com.ssafy.upcycle.inquiry.InquiryErrorCode.CONSUMER_INQUIRY_FORBIDDEN;
import static com.ssafy.upcycle.inquiry.InquiryErrorCode.INQUIRY_ORDER_NOT_FOUND;
import static com.ssafy.upcycle.inquiry.InquiryErrorCode.INQUIRY_TARGET_REQUIRED;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.inquiry.dto.ConsumerInquiryCreateRequest;
import com.ssafy.upcycle.inquiry.dto.ConsumerInquiryResponse;
import com.ssafy.upcycle.inquiry.entity.ConsumerInquiry;
import com.ssafy.upcycle.inquiry.repository.ConsumerInquiryRepository;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductOrder;
import com.ssafy.upcycle.product.entity.ProductOrderItem;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.repository.ProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CONSUMER 계정 본인의 문의. {@code ConsumerInquiryController} 가 호출한다.
 *
 * <p>배송지·찜과 같은 "내 리소스" 관례를 따른다 — 경로에 사용자 id 가 없고, 항상 JWT
 * {@code sub} 에서 얻는다.
 */
@Service
@RequiredArgsConstructor
public class ConsumerInquiryService {

    private final UserRepository users;
    private final ConsumerInquiryRepository inquiries;
    private final ProductOrderRepository productOrders;
    private final ProductOrderItemRepository productOrderItems;
    private final ProductModelRepository productModels;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ConsumerInquiryResponse> myInquiries(Long userId) {
        requireConsumer(userId);
        return inquiries.findAllByUserIdOrderByIdDesc(userId).stream()
                .map(ConsumerInquiryResponse::from)
                .toList();
    }

    @Transactional
    public ConsumerInquiryResponse create(Long userId, ConsumerInquiryCreateRequest request) {
        requireConsumer(userId);

        String targetLabel = null;
        if (request.productOrderId() != null) {
            targetLabel = targetLabelOf(userId, request.productOrderId());
        } else if (request.type().requiresOrder()) {
            throw new BusinessException(INQUIRY_TARGET_REQUIRED);
        }

        ConsumerInquiry saved = inquiries.save(ConsumerInquiry.register(
                userId,
                request.type(),
                request.productOrderId(),
                targetLabel,
                request.content(),
                LocalDateTime.now(clock)));
        return ConsumerInquiryResponse.from(saved);
    }

    /**
     * 주문이 본인 것인지 확인하고, 등록 시점의 상품명 스냅샷을 만든다.
     *
     * <p>여러 상품을 담은 주문이면 첫 항목의 상품명 뒤에 "외 N건"을 붙인다 — 문의 내용은
     * 어차피 자유 텍스트라 어떤 항목인지는 거기 적으면 되고, 여기서는 "이 주문에 대한
     * 문의"라는 것만 식별되면 된다.
     */
    private String targetLabelOf(Long userId, Long productOrderId) {
        ProductOrder order = productOrders.findById(productOrderId)
                .filter(o -> o.getBuyerId().equals(userId))
                .orElseThrow(() -> new BusinessException(INQUIRY_ORDER_NOT_FOUND));

        List<ProductOrderItem> items = productOrderItems.findAllByProductOrderId(order.getId());
        if (items.isEmpty()) {
            return order.getOrderCode();
        }

        String firstName = productModels.findById(items.get(0).getProductModelId())
                .map(ProductModel::getName)
                .orElse(order.getOrderCode());
        return items.size() == 1 ? firstName : firstName + " 외 " + (items.size() - 1) + "건";
    }

    private User requireConsumer(Long userId) {
        return users.findById(userId)
                .filter(user -> user.isActive() && user.getRole().canFileInquiry())
                .orElseThrow(() -> new BusinessException(CONSUMER_INQUIRY_FORBIDDEN));
    }
}
