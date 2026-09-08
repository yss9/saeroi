package com.ssafy.upcycle.product.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductItemTests {

    private static final String QR_TOKEN = "0123456789abcdef0123456789abcdef";

    @Test
    void producedItemIsImmediatelyInStock() {
        ProductItem item = item();

        assertThat(item.getStatus()).isEqualTo(ProductItemStatus.IN_STOCK);
        assertThat(item.isInStock()).isTrue();
    }

    @Test
    void serialNumberStartsFromOne() {
        assertThatThrownBy(() -> ProductItem.produce(1L, 2L, 0, "RE:BAG #000", QR_TOKEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProductItem.produce(1L, 2L, -1, "RE:BAG #001", QR_TOKEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 컬럼이 CHAR(32) 라 길이가 어긋나면 잘리거나 공백이 붙는다. QR 주소로 쓰이는 값이라
     * 한 글자만 달라져도 조회가 실패한다.
     */
    @Test
    void malformedQrTokenIsRejected() {
        assertThatThrownBy(() -> ProductItem.produce(1L, 2L, 1, "RE:BAG #001", "짧음"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProductItem.produce(1L, 2L, 1, "RE:BAG #001", null))
                .isInstanceOf(IllegalArgumentException.class);
        // 대문자 hex 는 받지 않는다. 같은 값이 두 표기로 저장되면 UNIQUE 가 무의미해진다
        assertThatThrownBy(() -> ProductItem.produce(
                        1L, 2L, 1, "RE:BAG #001", QR_TOKEN.toUpperCase()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProductItem.produce(
                        1L, 2L, 1, "RE:BAG #001", QR_TOKEN + "0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itemWalksTheSellingLifecycle() {
        ProductItem item = item();

        item.reserve();
        assertThat(item.getStatus()).isEqualTo(ProductItemStatus.RESERVED);

        item.markSold();
        assertThat(item.getStatus()).isEqualTo(ProductItemStatus.SOLD);
    }

    @Test
    void canceledOrderReturnsTheItemToStock() {
        ProductItem item = item();
        item.reserve();

        item.release();

        assertThat(item.isInStock()).isTrue();
    }

    /** 같은 개체를 두 주문이 잡으면 한 사람은 없는 물건을 산 것이 된다. */
    @Test
    void reservedItemCannotBeReservedAgain() {
        ProductItem item = item();
        item.reserve();

        assertThatThrownBy(item::reserve).isInstanceOf(IllegalStateException.class);
    }

    /** 잡아 두지 않은 개체가 팔리면 재고를 거치지 않고 사라진다. */
    @Test
    void itemInStockCannotBeSoldDirectly() {
        ProductItem item = item();

        assertThatThrownBy(item::markSold).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void soldItemIsFinal() {
        ProductItem item = item();
        item.reserve();
        item.markSold();

        assertThatThrownBy(item::release).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(item::reserve).isInstanceOf(IllegalStateException.class);
    }

    private ProductItem item() {
        return ProductItem.produce(1L, 2L, 1, "RE:BAG #001", QR_TOKEN);
    }
}
