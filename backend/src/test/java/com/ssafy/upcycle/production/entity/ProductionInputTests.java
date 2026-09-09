package com.ssafy.upcycle.production.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductionInputTests {

    @Test
    void inputSplitsIntoProductScrapAndWaste() {
        ProductionInput input = input(10_000L, 7_000L, 2_000L, 1_000L);

        assertThat(input.getInputQuantity()).isEqualTo(10_000L);
        assertThat(input.getProductUsedQuantity()).isEqualTo(7_000L);
        assertThat(input.getReusableScrapQuantity()).isEqualTo(2_000L);
        assertThat(input.getWasteQuantity()).isEqualTo(1_000L);
    }

    /**
     * ERD 가 "행마다 검증한다" 고 못 박은 규칙. 배치 합계로만 보면 한 소재를 과다 입력하고
     * 다른 소재를 과소 입력해도 통과하는데, 그러면 개별 소재의 이력이 거짓이 된다.
     */
    @Test
    void quantitiesThatDoNotAddUpAreRejected() {
        assertThatThrownBy(() -> input(10_000L, 7_000L, 2_000L, 500L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> input(10_000L, 7_000L, 2_000L, 1_500L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inputQuantityMustBePositive() {
        // 아무것도 넣지 않은 투입 행은 소재를 잠그고도 쓰지 않았다는 뜻이다.
        assertThatThrownBy(() -> input(0L, 0L, 0L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativePartsAreRejected() {
        // 합만 맞추면 통과하는 구멍을 막는다. -1 + 1 로도 식은 성립한다.
        assertThatThrownBy(() -> input(10_000L, 11_000L, -1_000L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everythingCanGoIntoTheProduct() {
        ProductionInput input = input(10_000L, 10_000L, 0L, 0L);

        assertThat(input.getProductUsedQuantity()).isEqualTo(10_000L);
        assertThat(input.hasReusableScrap()).isFalse();
    }

    @Test
    void scrapIsDetectedForReissue() {
        // 자투리가 남으면 확정 시 새 소재를 발행한다. 그 판정이 여기서 나온다.
        assertThat(input(10_000L, 8_000L, 2_000L, 0L).hasReusableScrap()).isTrue();
    }

    @Test
    void materialLockIsRequired() {
        // 잠그지 않은 소재를 투입하면 같은 소재를 두 배치가 동시에 쓰고 잔량이 음수가 된다.
        assertThatThrownBy(() -> ProductionInput.record(
                        1L, 2L, 10_000L, 10_000L, 0L, 0L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wasteReasonIsOptionalAndBlankBecomesNull() {
        assertThat(ProductionInput.record(1L, 2L, 100L, 100L, 0L, 0L, "  ", 3L).getWasteReason())
                .isNull();
        assertThat(ProductionInput.record(1L, 2L, 100L, 90L, 0L, 10L, "오염", 3L).getWasteReason())
                .isEqualTo("오염");
    }

    private ProductionInput input(long input, long productUsed, long scrap, long waste) {
        return ProductionInput.record(1L, 2L, input, productUsed, scrap, waste, null, 3L);
    }
}
