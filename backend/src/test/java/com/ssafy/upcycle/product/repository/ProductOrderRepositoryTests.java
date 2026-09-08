package com.ssafy.upcycle.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import com.ssafy.upcycle.product.entity.CanceledProductOrder;
import com.ssafy.upcycle.product.entity.CanceledProductOrderItem;
import com.ssafy.upcycle.product.entity.ProductOrder;
import com.ssafy.upcycle.product.entity.ProductOrderItem;
import com.ssafy.upcycle.product.entity.ProductOrderStatus;
import com.ssafy.upcycle.product.entity.ProductShipping;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * FK 무결성은 여기서 검증되지 않는다. 참조를 연관관계가 아닌 값으로 매핑했으므로
 * Hibernate 가 테스트 스키마에 FK 제약을 만들지 않는다. 실제 MySQL 에는 FK 가 있다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class ProductOrderRepositoryTests {

    private static final long BUYER_ID = 10L;
    private static final long BRAND_ID = 11L;
    private static final long MODEL_ID = 20L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 8, 12, 0);

    @Autowired
    private ProductOrderRepository orderRepository;

    @Autowired
    private ProductOrderItemRepository orderItemRepository;

    @Autowired
    private ProductShippingRepository shippingRepository;

    @Autowired
    private CanceledProductOrderRepository canceledOrderRepository;

    @Autowired
    private CanceledProductOrderItemRepository canceledOrderItemRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void orderIsPersistedWithCreatedAt() {
        ProductOrder saved = orderRepository.save(order("ORD-000001"));
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(ProductOrderStatus.CREATED);
    }

    @Test
    void ordersAreListedFromBothSides() {
        orderRepository.save(order("ORD-000001"));
        orderRepository.save(order("ORD-000002"));
        entityManager.flush();
        entityManager.clear();

        assertThat(orderRepository.findAllByBuyerIdAndStatus(
                BUYER_ID, ProductOrderStatus.CREATED)).hasSize(2);
        assertThat(orderRepository.findAllByBrandIdAndStatus(
                BRAND_ID, ProductOrderStatus.CREATED)).hasSize(2);
    }

    @Test
    void orderIsFoundByCode() {
        orderRepository.save(order("ORD-000001"));
        entityManager.flush();
        entityManager.clear();

        assertThat(orderRepository.findByOrderCode("ORD-000001")).isPresent();
        assertThat(orderRepository.findByOrderCode("ORD-999999")).isEmpty();
    }

    /** 정리되지 않으면 잡아 둔 개체가 영구히 RESERVED 로 묶여 재고가 조용히 줄어든다. */
    @Test
    void expiredUnpaidOrdersAreFound() {
        orderRepository.save(order("ORD-000001"));
        ProductOrder paid = orderRepository.save(order("ORD-000002"));
        paid.markPaid("T1234", 89_000L, NOW);
        entityManager.flush();
        entityManager.clear();

        assertThat(orderRepository.findExpiredUnpaid(NOW.plusMinutes(29))).isEmpty();
        // 결제까지 끝난 주문은 마감이 지나도 걸리지 않는다
        assertThat(orderRepository.findExpiredUnpaid(NOW.plusMinutes(31)))
                .hasSize(1)
                .extracting(ProductOrder::getOrderCode)
                .containsExactly("ORD-000001");
    }

    /** 같은 개체가 두 주문에 들어가는 것을 미리 알려주는 조회. 실제로 막는 것은 UNIQUE 다. */
    @Test
    void soldItemIsFoundByItsId() {
        Long orderId = orderRepository.save(order("ORD-000001")).getId();
        orderItemRepository.save(ProductOrderItem.of(orderId, 100L, MODEL_ID, 89_000L));
        entityManager.flush();
        entityManager.clear();

        assertThat(orderItemRepository.findByProductItemId(100L)).isPresent();
        assertThat(orderItemRepository.findByProductItemId(999L)).isEmpty();
    }

    @Test
    void shippingIsFoundByOrder() {
        Long orderId = orderRepository.save(order("ORD-000001")).getId();
        shippingRepository.save(ProductShipping.of(
                        orderId, "김소비", "010-0000-0000", "04766", "서울시 성동구", "101동"))
                .registerShipment("CJ대한통운", "123456789012", NOW);
        entityManager.flush();
        entityManager.clear();

        assertThat(shippingRepository.findByProductOrderId(orderId))
                .get()
                .extracting(ProductShipping::getTrackingNumber)
                .isEqualTo("123456789012");
    }

    /**
     * 취소는 원본을 지우고 이력으로 옮긴다. 그래야 product_item_id 의 UNIQUE 를 유지한 채
     * 같은 개체를 다시 팔 수 있다 — 소재 거래가 상태만 바꾸는 것과 다른 점이다.
     */
    @Test
    void cancellationMovesTheOrderIntoHistory() {
        ProductOrder order = orderRepository.save(order("ORD-000001"));
        ProductOrderItem item =
                orderItemRepository.save(ProductOrderItem.of(order.getId(), 100L, MODEL_ID, 89_000L));
        entityManager.flush();

        CanceledProductOrder canceled = canceledOrderRepository.save(
                CanceledProductOrder.from(order, "재고 확인 중 파손 발견", NOW));
        canceledOrderItemRepository.save(
                CanceledProductOrderItem.from(canceled.getId(), item));
        orderItemRepository.deleteAllByProductOrderId(order.getId());
        orderRepository.delete(order);
        entityManager.flush();
        entityManager.clear();

        assertThat(orderRepository.findByOrderCode("ORD-000001")).isEmpty();
        assertThat(canceledOrderRepository.findByOriginalOrderId(order.getId()))
                .get()
                .extracting(CanceledProductOrder::getCancelReason)
                .isEqualTo("재고 확인 중 파손 발견");
        assertThat(canceledOrderItemRepository.findAllByCanceledProductOrderId(canceled.getId()))
                .hasSize(1);
    }

    /** 원본이 지워졌으므로 주문 코드가 다시 발급될 수 있고, 그 주문이 또 취소될 수 있다. */
    @Test
    void sameItemCanBeCanceledMoreThanOnce() {
        ProductOrder first = orderRepository.save(order("ORD-000001"));
        ProductOrderItem firstItem =
                orderItemRepository.save(ProductOrderItem.of(first.getId(), 100L, MODEL_ID, 89_000L));
        entityManager.flush();

        Long firstCanceled = canceledOrderRepository
                .save(CanceledProductOrder.from(first, null, NOW)).getId();
        canceledOrderItemRepository.save(
                CanceledProductOrderItem.from(firstCanceled, firstItem));
        orderItemRepository.deleteAllByProductOrderId(first.getId());
        orderRepository.delete(first);
        entityManager.flush();

        // 같은 개체가 다시 팔리고 또 취소된다
        ProductOrder second = orderRepository.save(order("ORD-000002"));
        ProductOrderItem secondItem =
                orderItemRepository.save(ProductOrderItem.of(second.getId(), 100L, MODEL_ID, 89_000L));
        entityManager.flush();

        Long secondCanceled = canceledOrderRepository
                .save(CanceledProductOrder.from(second, null, NOW)).getId();
        canceledOrderItemRepository.save(
                CanceledProductOrderItem.from(secondCanceled, secondItem));
        entityManager.flush();
        entityManager.clear();

        List<CanceledProductOrderItem> history =
                canceledOrderItemRepository.findAllByProductItemId(100L);
        assertThat(history).hasSize(2);
    }

    @Test
    void canceledOrdersAreListedFromBothSides() {
        ProductOrder order = orderRepository.save(order("ORD-000001"));
        entityManager.flush();
        canceledOrderRepository.save(CanceledProductOrder.from(order, null, NOW));
        entityManager.flush();
        entityManager.clear();

        assertThat(canceledOrderRepository.findAllByBuyerId(BUYER_ID)).hasSize(1);
        assertThat(canceledOrderRepository.findAllByBrandId(BRAND_ID)).hasSize(1);
    }

    private ProductOrder order(String orderCode) {
        return ProductOrder.place(
                orderCode, BUYER_ID, BRAND_ID, 89_000L,
                PaymentMethod.MOCK, NOW.plusMinutes(30));
    }
}
