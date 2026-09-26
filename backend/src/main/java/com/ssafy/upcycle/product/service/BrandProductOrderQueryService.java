package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.product.ProductErrorCode;
import com.ssafy.upcycle.product.dto.BrandProductOrderItemResponse;
import com.ssafy.upcycle.product.dto.BrandProductOrderResponse;
import com.ssafy.upcycle.product.dto.BrandProductOrderShippingResponse;
import com.ssafy.upcycle.product.dto.ProductOrderSearchCondition;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductOrder;
import com.ssafy.upcycle.product.entity.ProductOrderItem;
import com.ssafy.upcycle.product.entity.ProductShipping;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.repository.ProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderRepository;
import com.ssafy.upcycle.product.repository.ProductShippingRepository;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 브랜드에게 들어온 제품 주문 목록.
 *
 * <p><b>이것이 없으면 발송 API 가 쓸모가 없다.</b> {@code PATCH /{orderId}/ship} 은 주문
 * 번호를 받는데, 브랜드가 그 번호를 알 길이 이 목록뿐이다.
 *
 * <p>구매자용 {@code ProductOrderQueryService} 와 나눈 이유는 <b>보는 사람이 다르기
 * 때문</b>이다. 역할 판정이 반대이고(BRAND vs CONSUMER), 싣는 값도 다르다 — 이쪽은
 * 배송지가 들어 있고 제품 여정 주소가 없다. 한 클래스에 두면 어느 분기가 어느 쪽 화면을
 * 위한 것인지 곧 알 수 없게 된다.
 *
 * <p>범위는 언제나 JWT 의 사용자 id 다. 경로나 조건으로 브랜드를 지정하게 하면 남의 주문과
 * 그 배송지를 들여다볼 길이 생긴다 — 배송지는 남의 집 주소다.
 */
@Service
@RequiredArgsConstructor
public class BrandProductOrderQueryService {

    private final ProductOrderRepository orders;
    private final ProductOrderItemRepository orderItems;
    private final ProductShippingRepository shippings;
    private final ProductItemRepository items;
    private final ProductModelRepository models;
    private final UserRepository users;

    /**
     * 브랜드 본인에게 들어온 주문. 최신순이다.
     *
     * <p>상태를 생략하면 전부 나온다. 발송할 건만 보려면 {@code status=PAID} 로 거른다 —
     * 결제가 끝났고 아직 보내지 않은 주문이 정확히 그 상태다.
     *
     * <p>주문과 항목마다 개별 조회하지 않고 현재 페이지에 필요한 데이터를 종류별로 한 번씩
     * 모아 읽는다. 구매자 목록과 같은 방식이다.
     */
    @Transactional(readOnly = true)
    public PageResponse<BrandProductOrderResponse> findIncomingOrders(
            Long brandId, ProductOrderSearchCondition condition) {
        requireActiveBrand(brandId);

        Pageable pageable = PageRequest.of(
                condition.pageOrDefault(),
                condition.sizeOrDefault(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<ProductOrder> page = condition.status() == null
                ? orders.findAllByBrandId(brandId, pageable)
                : orders.findAllByBrandIdAndStatus(brandId, condition.status(), pageable);

        Lookup lookup = loadLookup(page.getContent());
        return PageResponse.from(page.map(order -> toResponse(order, lookup)));
    }

    private Lookup loadLookup(List<ProductOrder> page) {
        if (page.isEmpty()) {
            return Lookup.empty();
        }

        List<Long> orderIds = page.stream().map(ProductOrder::getId).toList();
        List<ProductOrderItem> rows =
                orderItems.findAllByProductOrderIdInOrderByIdAsc(orderIds);

        Map<Long, ProductItem> itemsById = items
                .findAllById(rows.stream().map(ProductOrderItem::getProductItemId).toList())
                .stream()
                .collect(Collectors.toMap(ProductItem::getId, Function.identity()));
        Map<Long, ProductModel> modelsById = models
                .findAllById(rows.stream().map(ProductOrderItem::getProductModelId).toList())
                .stream()
                .collect(Collectors.toMap(ProductModel::getId, Function.identity()));
        Map<Long, ProductShipping> shippingByOrderId =
                shippings.findAllByProductOrderIdIn(orderIds).stream()
                        .collect(Collectors.toMap(
                                ProductShipping::getProductOrderId, Function.identity()));
        Map<Long, String> buyerNames = users
                .findAllById(page.stream().map(ProductOrder::getBuyerId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getName));

        return new Lookup(
                rows.stream().collect(Collectors.groupingBy(ProductOrderItem::getProductOrderId)),
                itemsById,
                modelsById,
                shippingByOrderId,
                buyerNames);
    }

    private BrandProductOrderResponse toResponse(ProductOrder order, Lookup lookup) {
        List<BrandProductOrderItemResponse> orderItemResponses =
                lookup.itemsByOrderId().getOrDefault(order.getId(), Collections.emptyList())
                        .stream()
                        .map(orderItem -> BrandProductOrderItemResponse.of(
                                orderItem,
                                requireRelated(
                                        lookup.itemsById().get(orderItem.getProductItemId()),
                                        "product item"),
                                requireRelated(
                                        lookup.modelsById().get(orderItem.getProductModelId()),
                                        "product model")))
                        .toList();

        ProductShipping shipping = requireRelated(
                lookup.shippingByOrderId().get(order.getId()), "product shipping");

        return new BrandProductOrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getStatus(),
                lookup.buyerNames().getOrDefault(order.getBuyerId(), "알 수 없는 구매자"),
                order.getTotalPrice(),
                order.getCreatedAt(),
                order.getPaidAt(),
                order.getShippedAt(),
                order.getDeliveredAt(),
                orderItemResponses,
                BrandProductOrderShippingResponse.from(shipping));
    }

    /**
     * 브랜드 계정인지.
     *
     * <p>토큰의 역할 클레임이 아니라 DB 의 현재 역할로 판단한다 — 토큰은 발급 시점의
     * 스냅샷이라, 정지되거나 역할이 바뀐 계정이 만료 전까지 남의 주문과 배송지를 계속 볼 수
     * 있다.
     */
    private User requireActiveBrand(Long brandId) {
        User brand = users.findById(brandId)
                .orElseThrow(() -> new BusinessException(
                        ProductErrorCode.PRODUCT_BRAND_NOT_FOUND));
        if (!brand.isActive()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_BRAND_SUSPENDED);
        }
        if (brand.getRole() != UserRole.BRAND) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ROLE_NOT_ALLOWED);
        }
        return brand;
    }

    private static <T> T requireRelated(T value, String relation) {
        if (value == null) {
            throw new IllegalStateException("Product order has no related " + relation);
        }
        return value;
    }

    private record Lookup(
            Map<Long, List<ProductOrderItem>> itemsByOrderId,
            Map<Long, ProductItem> itemsById,
            Map<Long, ProductModel> modelsById,
            Map<Long, ProductShipping> shippingByOrderId,
            Map<Long, String> buyerNames) {

        private static Lookup empty() {
            return new Lookup(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }
}
