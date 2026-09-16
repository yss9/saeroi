package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.product.ProductErrorCode;
import com.ssafy.upcycle.product.dto.ProductOrderItemResponse;
import com.ssafy.upcycle.product.dto.ProductOrderResponse;
import com.ssafy.upcycle.product.dto.ProductOrderSearchCondition;
import com.ssafy.upcycle.product.dto.ProductOrderShippingResponse;
import com.ssafy.upcycle.product.dto.ProductOrderStatusSummaryResponse;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductOrder;
import com.ssafy.upcycle.product.entity.ProductOrderItem;
import com.ssafy.upcycle.product.entity.ProductOrderStatus;
import com.ssafy.upcycle.product.entity.ProductShipping;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.repository.ProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderRepository;
import com.ssafy.upcycle.product.repository.ProductShippingRepository;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
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

/** 소비자 본인의 제품 주문 조회. */
@Service
@RequiredArgsConstructor
public class ProductOrderQueryService {

    private final ProductOrderRepository productOrderRepository;
    private final ProductOrderItemRepository productOrderItemRepository;
    private final ProductShippingRepository productShippingRepository;
    private final ProductItemRepository productItemRepository;
    private final ProductModelRepository productModelRepository;
    private final UserRepository userRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final ProductQrCodeGenerator productQrCodeGenerator;

    /**
     * 본인 주문 목록. 주문과 항목마다 개별 조회하지 않고 현재 페이지에 필요한 데이터를
     * 종류별로 한 번씩 모아 읽는다.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductOrderResponse> findMyOrders(
            Long buyerId, ProductOrderSearchCondition condition) {
        requireActiveConsumer(buyerId);

        Pageable pageable = PageRequest.of(
                condition.pageOrDefault(),
                condition.sizeOrDefault(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<ProductOrder> orders = condition.status() == null
                ? productOrderRepository.findAllByBuyerId(buyerId, pageable)
                : productOrderRepository.findAllByBuyerIdAndStatus(
                        buyerId, condition.status(), pageable);

        OrderLookup lookup = loadLookup(orders.getContent());
        return PageResponse.from(orders.map(order -> toResponse(order, lookup)));
    }

    /**
     * 본인 주문 한 건.
     *
     * <p>주문 생성 직후 응답을 만드는 데도 쓴다({@code ProductOrderCommandService}).
     * 응답 조립을 두 곳에 두면 목록과 생성 응답이 서로 다른 모양으로 갈라진다.
     *
     * <p>{@code orderId} 를 받지만 본인 주문이 아니면 "없음" 으로 답한다 — 403 은
     * 남의 주문이 존재한다는 사실을 흘린다.
     */
    @Transactional(readOnly = true)
    public ProductOrderResponse findMyOrder(Long buyerId, Long orderId) {
        requireActiveConsumer(buyerId);
        ProductOrder order = productOrderRepository.findById(orderId)
                .filter(found -> found.getBuyerId().equals(buyerId))
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_ORDER_NOT_FOUND));
        return toResponse(order, loadLookup(List.of(order)));
    }

    /** 목록 페이지 크기와 무관한 상태별 전체 주문 수. */
    @Transactional(readOnly = true)
    public ProductOrderStatusSummaryResponse summarizeMyOrders(Long buyerId) {
        requireActiveConsumer(buyerId);
        return new ProductOrderStatusSummaryResponse(
                count(buyerId, ProductOrderStatus.CREATED),
                count(buyerId, ProductOrderStatus.PAID),
                count(buyerId, ProductOrderStatus.SHIPPED),
                count(buyerId, ProductOrderStatus.DELIVERED));
    }

    private long count(Long buyerId, ProductOrderStatus status) {
        return productOrderRepository.countByBuyerIdAndStatus(buyerId, status);
    }

    private User requireActiveConsumer(Long buyerId) {
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

    private OrderLookup loadLookup(List<ProductOrder> orders) {
        if (orders.isEmpty()) {
            return OrderLookup.empty();
        }

        List<Long> orderIds = orders.stream().map(ProductOrder::getId).toList();
        List<ProductOrderItem> orderItems =
                productOrderItemRepository.findAllByProductOrderIdInOrderByIdAsc(orderIds);

        Map<Long, ProductItem> itemsById = productItemRepository
                .findAllById(orderItems.stream().map(ProductOrderItem::getProductItemId).toList())
                .stream()
                .collect(Collectors.toMap(ProductItem::getId, Function.identity()));
        Map<Long, ProductModel> modelsById = productModelRepository
                .findAllById(orderItems.stream().map(ProductOrderItem::getProductModelId).toList())
                .stream()
                .collect(Collectors.toMap(ProductModel::getId, Function.identity()));
        // 목록 한 페이지분을 한 번에 읽는다. 주문마다 따로 읽으면 줄 수만큼 쿼리가 나간다.
        Map<Long, ProductShipping> shippingByOrderId =
                productShippingRepository.findAllByProductOrderIdIn(orderIds).stream()
                        .collect(Collectors.toMap(
                                ProductShipping::getProductOrderId, Function.identity()));
        Map<Long, String> brandNames = loadBrandNames(orders);
        Map<Long, List<ProductOrderItem>> itemsByOrderId = orderItems.stream()
                .collect(Collectors.groupingBy(ProductOrderItem::getProductOrderId));

        return new OrderLookup(
                itemsByOrderId, itemsById, modelsById, shippingByOrderId, brandNames);
    }

    private Map<Long, String> loadBrandNames(List<ProductOrder> orders) {
        List<Long> brandIds = orders.stream().map(ProductOrder::getBrandId).distinct().toList();
        Map<Long, String> userNames = userRepository.findAllById(brandIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName));
        Map<Long, String> companyNames = companyProfileRepository.findAllById(brandIds).stream()
                .collect(Collectors.toMap(CompanyProfile::getUserId, CompanyProfile::getCompanyName));
        return brandIds.stream().collect(Collectors.toMap(
                Function.identity(),
                brandId -> companyNames.getOrDefault(
                        brandId, userNames.getOrDefault(brandId, "알 수 없는 브랜드"))));
    }

    private ProductOrderResponse toResponse(ProductOrder order, OrderLookup lookup) {
        List<ProductOrderItemResponse> items = lookup.itemsByOrderId()
                .getOrDefault(order.getId(), Collections.emptyList())
                .stream()
                .map(orderItem -> toItemResponse(order, orderItem, lookup))
                .toList();
        return new ProductOrderResponse(
                order.getId(),
                order.getOrderCode(),
                lookup.brandNames().getOrDefault(order.getBrandId(), "알 수 없는 브랜드"),
                order.getTotalPrice(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getPaidAt(),
                order.getShippedAt(),
                order.getDeliveredAt(),
                items,
                ProductOrderShippingResponse.from(requireRelated(
                        lookup.shippingByOrderId().get(order.getId()), "product shipping")));
    }

    private ProductOrderItemResponse toItemResponse(
            ProductOrder order, ProductOrderItem orderItem, OrderLookup lookup) {
        ProductItem item = requireRelated(
                lookup.itemsById().get(orderItem.getProductItemId()), "product item");
        ProductModel model = requireRelated(
                lookup.modelsById().get(orderItem.getProductModelId()), "product model");
        String qrToken = item.getQrToken();
        return new ProductOrderItemResponse(
                item.getItemCode(),
                item.getSerialNumber(),
                model.getName(),
                model.getCategory(),
                orderItem.getPrice(),
                orderThumbnailUrl(model),
                order.getStatus() == ProductOrderStatus.CREATED
                        ? null
                        : productQrCodeGenerator.urlFor(qrToken));
    }

    private String orderThumbnailUrl(ProductModel model) {
        if (model.getThumbnailUrl().startsWith("/images/landing/")) {
            return model.getThumbnailUrl();
        }
        return "/api/v1/product-models/" + model.getId() + "/thumbnail";
    }

    private static <T> T requireRelated(T value, String relation) {
        if (value == null) {
            throw new IllegalStateException("Product order has no related " + relation);
        }
        return value;
    }

    private record OrderLookup(
            Map<Long, List<ProductOrderItem>> itemsByOrderId,
            Map<Long, ProductItem> itemsById,
            Map<Long, ProductModel> modelsById,
            Map<Long, ProductShipping> shippingByOrderId,
            Map<Long, String> brandNames) {

        private static OrderLookup empty() {
            return new OrderLookup(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }
}
