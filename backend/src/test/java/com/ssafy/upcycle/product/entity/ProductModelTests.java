package com.ssafy.upcycle.product.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductModelTests {

    @Test
    void registeredModelStartsAsDraft() {
        ProductModel model = model();

        assertThat(model.getStatus()).isEqualTo(ProductModelStatus.DRAFT);
        assertThat(model.isOnSale()).isFalse();
    }

    @Test
    void blankFieldsAreRejected() {
        assertThatThrownBy(() -> ProductModel.register(
                        "  ", ProductCategory.BAG, 1L, 89_000L, "설명", "https://img"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProductModel.register(
                        "RE:BAG", ProductCategory.BAG, 1L, 89_000L, null, "https://img"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativePriceIsRejected() {
        assertThatThrownBy(() -> ProductModel.register(
                        "RE:BAG", ProductCategory.BAG, 1L, -1L, "설명", "https://img"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 0원은 허용한다. schema 의 CHECK 도 price >= 0 이다 — 증정품 같은 경우가 있다. */
    @Test
    void freeProductIsAllowed() {
        assertThat(ProductModel.register(
                        "RE:BAG", ProductCategory.BAG, 1L, 0L, "설명", "https://img")
                .getPrice()).isZero();
    }

    @Test
    void modelWalksTheSellingLifecycle() {
        ProductModel model = model();

        model.putOnSale();
        assertThat(model.isOnSale()).isTrue();

        model.markSoldOut();
        assertThat(model.getStatus()).isEqualTo(ProductModelStatus.SOLD_OUT);

        model.restock();
        assertThat(model.getStatus()).isEqualTo(ProductModelStatus.ON_SALE);
    }

    /** 팔린 적 없는 모델을 품절로 만들면 화면이 "품절" 이라고 거짓말한다. */
    @Test
    void draftModelIsNotMarkedSoldOut() {
        ProductModel model = model();

        model.markSoldOut();

        assertThat(model.getStatus()).isEqualTo(ProductModelStatus.DRAFT);
    }

    /** 판매 중인 모델에 재입고를 불러도 상태가 흔들리면 안 된다. */
    @Test
    void restockDoesNothingWhenAlreadyOnSale() {
        ProductModel model = model();
        model.putOnSale();

        model.restock();

        assertThat(model.getStatus()).isEqualTo(ProductModelStatus.ON_SALE);
    }

    @Test
    void discontinuedModelCannotComeBack() {
        ProductModel model = model();
        model.putOnSale();
        model.discontinue();

        assertThat(model.getStatus()).isEqualTo(ProductModelStatus.DISCONTINUED);
        assertThatThrownBy(model::putOnSale).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(model::discontinue).isInstanceOf(IllegalStateException.class);
    }

    /** 단종된 모델에는 재입고도 통하지 않는다. 남은 개체가 있어도 더 팔지 않는다. */
    @Test
    void discontinuedModelIgnoresRestock() {
        ProductModel model = model();
        model.putOnSale();
        model.discontinue();

        model.restock();

        assertThat(model.getStatus()).isEqualTo(ProductModelStatus.DISCONTINUED);
    }

    private ProductModel model() {
        return ProductModel.register(
                "RE:BAG", ProductCategory.BAG, 1L, 89_000L, "폐데님으로 만든 가방", "https://img");
    }
}
