package com.ssafy.upcycle.production.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ProductionBatchTests {

    private static final String WALLET = "0x1234567890123456789012345678901234567890";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 9, 10, 0);

    @Test
    void openedBatchStartsAsDraftWithNothingUsed() {
        ProductionBatch batch = batch();

        assertThat(batch.getStatus()).isEqualTo(ProductionBatchStatus.DRAFT);
        assertThat(batch.isEditable()).isTrue();
        assertThat(batch.getTotalUsedQuantity()).isZero();
        assertThat(batch.getUpcycleRatio()).isEqualByComparingTo("0.00");
    }

    @Test
    void producedQuantityMustBePositive() {
        // 0개를 만드는 배치는 개체도 만들지 않는다. 그런 행은 이력만 어지럽힌다.
        assertThatThrownBy(() -> ProductionBatch.open("TMP-1", "1차", 1L, 0, 2L, WALLET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void walletMustBeAnAddressLength() {
        assertThatThrownBy(() -> ProductionBatch.open("TMP-1", "1차", 1L, 50, 2L, "0xabc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 상태 전이 ────────────────────────────────────────────

    @Test
    void batchWalksTheConfirmingLifecycle() {
        ProductionBatch batch = batch();

        batch.startSigning();
        assertThat(batch.getStatus()).isEqualTo(ProductionBatchStatus.SIGNING);
        // 서명 중에는 투입을 바꿀 수 없다.
        assertThat(batch.isEditable()).isFalse();

        batch.confirm(30_000L, 70_000L, NOW);
        assertThat(batch.getStatus()).isEqualTo(ProductionBatchStatus.CONFIRMED);
        assertThat(batch.isConfirmed()).isTrue();
        assertThat(batch.getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    void confirmingWithoutSigningIsRejected() {
        // DRAFT 에서 바로 확정하면 같은 배치를 두 번 확정하는 것을 막을 자리가 없다.
        assertThatThrownBy(() -> batch().confirm(0L, 100L, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void confirmingTwiceIsRejected() {
        ProductionBatch batch = batch();
        batch.startSigning();
        batch.confirm(0L, 100L, NOW);

        // 두 번 확정되면 개체가 두 배로 만들어지고 소재는 두 번 소비된다.
        assertThatThrownBy(() -> batch.confirm(0L, 100L, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedBatchCanBeReopened() {
        ProductionBatch batch = batch();
        batch.startSigning();
        batch.markFailed();

        assertThat(batch.getStatus()).isEqualTo(ProductionBatchStatus.FAILED);

        batch.reopen();
        assertThat(batch.getStatus()).isEqualTo(ProductionBatchStatus.DRAFT);
        assertThat(batch.isEditable()).isTrue();
    }

    @Test
    void confirmedBatchCannotBeCanceled() {
        // 소재는 이미 소비됐고 개체는 이미 만들어졌다. 되돌리는 것은 취소가 아니라 보정이다.
        ProductionBatch batch = batch();
        batch.startSigning();
        batch.confirm(0L, 100L, NOW);

        assertThatThrownBy(batch::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void draftBatchCanBeCanceled() {
        ProductionBatch batch = batch();
        batch.cancel();

        assertThat(batch.getStatus()).isEqualTo(ProductionBatchStatus.CANCELED);
    }

    // ── 업사이클 비율 ────────────────────────────────────────

    @Test
    void ratioIsTheUpcycledShareOfWhatWentIntoTheProduct() {
        ProductionBatch batch = batch();
        batch.startSigning();
        batch.confirm(30_000L, 70_000L, NOW);

        assertThat(batch.getTotalUsedQuantity()).isEqualTo(100_000L);
        assertThat(batch.getUpcycleRatio()).isEqualByComparingTo("70.00");
    }

    @Test
    void ratioRoundsToTwoDecimalsLikeTheColumn() {
        // upcycle_ratio 는 DECIMAL(5,2) 다. double 로 계산하면 33.33 이
        // 33.329999… 로 저장되는 일이 생긴다.
        ProductionBatch batch = batch();
        batch.startSigning();
        batch.confirm(2L, 1L, NOW);

        assertThat(batch.getUpcycleRatio()).isEqualByComparingTo(new BigDecimal("33.33"));
    }

    @Test
    void ratioIsZeroWhenNothingWasUsed() {
        // 0 으로 나누지 않는다. 컬럼이 NOT NULL 이라 "알 수 없음" 을 둘 수도 없다.
        ProductionBatch batch = batch();
        batch.startSigning();
        batch.confirm(0L, 0L, NOW);

        assertThat(batch.getUpcycleRatio()).isEqualByComparingTo("0.00");
        assertThat(batch.getTotalUsedQuantity()).isZero();
    }

    @Test
    void ratioIsAHundredWhenEverythingWasUpcycled() {
        ProductionBatch batch = batch();
        batch.startSigning();
        batch.confirm(0L, 50_000L, NOW);

        assertThat(batch.getUpcycleRatio()).isEqualByComparingTo("100.00");
    }

    @Test
    void negativeUsedQuantityIsRejected() {
        ProductionBatch batch = batch();
        batch.startSigning();

        assertThatThrownBy(() -> batch.confirm(-1L, 10L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 코드 ────────────────────────────────────────────────

    @Test
    void temporaryCodeIsReplacedOnce() {
        ProductionBatch batch = batch();
        batch.assignCode("PB-000001");

        assertThat(batch.getBatchCode()).isEqualTo("PB-000001");
        // 코드가 바뀌면 그 코드로 남긴 기록이 무엇을 가리키는지 알 수 없게 된다.
        assertThatThrownBy(() -> batch.assignCode("PB-000002"))
                .isInstanceOf(IllegalStateException.class);
    }

    private ProductionBatch batch() {
        return ProductionBatch.open(
                ProductionBatch.TEMPORARY_CODE_PREFIX + "1", "RE:BAG 1차 생산", 1L, 50, 2L, WALLET);
    }
}
