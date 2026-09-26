package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.product.entity.CanceledProductOrder;
import com.ssafy.upcycle.product.entity.CanceledProductOrderItem;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductOrder;
import com.ssafy.upcycle.product.entity.ProductOrderItem;
import com.ssafy.upcycle.product.repository.CanceledProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.CanceledProductOrderRepository;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderRepository;
import com.ssafy.upcycle.product.repository.ProductShippingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 취소된 주문을 이력으로 옮기고 원본을 지운다. 개체는 재고로 돌아간다.
 *
 * <h2>왜 꺼냈는가</h2>
 *
 * <p>취소의 입구가 셋이 됐다 — 단일 주문 취소, 묶음 취소, 결제 마감 만료. 셋이 각자 이
 * 순서를 들고 있으면 한 곳만 고쳐지고, 빠뜨리기 쉬운 것이 하필 <b>개체를 재고로 되돌리는
 * 줄</b>이다. 그것을 빠뜨리면 취소할 때마다 팔 수 있는 제품이 한 점씩 사라진다.
 *
 * <p>옮긴 것뿐이고 순서와 동작은 {@code ProductOrderPaymentService} 가 하던 그대로다.
 *
 * <p><b>환불은 여기서 하지 않는다.</b> 돈을 되돌리는 단위는 주문이 아니라 결제이고, 그것은
 * 단일 주문과 묶음이 서로 다르다. 부르는 쪽이 환불을 먼저 끝낸 뒤 이 메서드를 부른다.
 */
@Component
@RequiredArgsConstructor
public class ProductOrderArchiver {

    private final ProductOrderRepository orders;
    private final ProductOrderItemRepository orderItems;
    private final ProductShippingRepository shippings;
    private final ProductItemRepository items;
    private final CanceledProductOrderRepository canceledOrders;
    private final CanceledProductOrderItemRepository canceledOrderItems;
    private final Clock clock;

    /**
     * 주문을 이력으로 옮기고 원본을 지운다.
     *
     * <p><b>상태를 바꾸는 대신 행을 지운다.</b> 소재 거래와 다른 점이고 이유는
     * {@code product_order_item.product_item_id} 의 UNIQUE 다 — 취소된 개체를 다시 팔려면
     * 그 행이 사라져야 한다({@code CanceledProductOrder} 문서 참고).
     *
     * <p>지우는 순서가 FK 를 따른다: 배송지·항목이 주문을 참조하므로 그쪽을 먼저 지운다.
     */
    public void archiveAndDelete(ProductOrder order, String reason) {
        List<ProductOrderItem> orderItemRows = orderItems.findAllByProductOrderId(order.getId());

        CanceledProductOrder archived = canceledOrders.save(
                CanceledProductOrder.from(order, reason, LocalDateTime.now(clock)));
        orderItemRows.forEach(item ->
                canceledOrderItems.save(CanceledProductOrderItem.from(archived.getId(), item)));

        // 잡아 둔 개체를 재고로 되돌린다. 빠뜨리면 취소할 때마다 제품이 한 점씩 사라진다.
        // 결제까지 끝난 주문이면 개체가 SOLD 라, RESERVED 만 받는 release() 로는 부족하다.
        items.findAllById(orderItemRows.stream().map(ProductOrderItem::getProductItemId).toList())
                .forEach(ProductItem::returnToStock);

        shippings.deleteByProductOrderId(order.getId());
        orderItems.deleteAllByProductOrderId(order.getId());
        orders.delete(order);
        orders.flush();
    }
}
