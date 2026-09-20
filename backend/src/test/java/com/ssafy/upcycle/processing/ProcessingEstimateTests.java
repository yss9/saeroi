package com.ssafy.upcycle.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.processing.entity.ProcessingEstimate;
import org.junit.jupiter.api.Test;

/**
 * 가공비와 소요일의 계산식.
 *
 * <p>견적이 사라지면서 <b>이 식이 곧 청구서</b>가 됐다. 검색 목록이 보여 주는 금액과 요청이
 * 확정하는 금액이 같은 식을 쓰기 때문에, 여기가 흔들리면 화면이 보여 준 값과 결제창 값이
 * 달라진다 — 사용자가 결제 직전에야 알게 되는 종류의 오류다.
 */
class ProcessingEstimateTests {

    @Test
    void theFeeIsThePricePerKiloTimesTheKilos() {
        // 1000원/kg × 2kg
        assertThat(ProcessingEstimate.fee(1_000, 2_000)).isEqualTo(2_000);
    }

    @Test
    void aPartialKiloIsRoundedUpRatherThanGivenAway() {
        // 내림하면 1kg 미만이 공짜가 된다. 1000원/kg 에 1g 을 맡겨도 1원은 받는다.
        assertThat(ProcessingEstimate.fee(1_000, 1)).isEqualTo(1);
        // 999원/kg × 1.001kg = 999.999원 → 1000원
        assertThat(ProcessingEstimate.fee(999, 1_001)).isEqualTo(1_000);
        assertThat(ProcessingEstimate.fee(1_500, 100)).isEqualTo(150);
    }

    @Test
    void freeProcessingIsAllowedButNegativePricingIsNot() {
        // 무상 가공이나 시범 운영이 있을 수 있다. 음수는 환불이지 가공이 아니다.
        assertThat(ProcessingEstimate.fee(0, 5_000)).isZero();
        assertThatThrownBy(() -> ProcessingEstimate.fee(-1, 5_000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDayAndAHalfIsTwoDaysBecauseHalfDaysAreNotSchedules() {
        // 하루 1000g 처리하는 업체에 1500g 을 맡기면 이틀이다.
        assertThat(ProcessingEstimate.leadDays(1_000, 1_500)).isEqualTo(2);
        assertThat(ProcessingEstimate.leadDays(1_000, 2_000)).isEqualTo(2);
        assertThat(ProcessingEstimate.leadDays(1_000, 2_001)).isEqualTo(3);
    }

    @Test
    void evenATinyBatchTakesAWholeDay() {
        // 하루 처리량보다 적어도 0일에 끝나는 가공은 없다.
        assertThat(ProcessingEstimate.leadDays(1_000, 1)).isEqualTo(1);
    }

    @Test
    void aProcessorWithNoDailyCapacityHasNoSchedule() {
        // 0 은 소요일의 분모다. 여기서 막지 않으면 나누기에서 터진다.
        assertThatThrownBy(() -> ProcessingEstimate.leadDays(0, 1_000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anImpossibleScheduleIsClampedInsteadOfGoingNegative() {
        // 하루 1g 씩 Long.MAX_VALUE 를 처리하는 일정은 성립하지 않지만, int 를 넘겨
        // "마이너스 3일" 이 화면에 나가는 것보다는 상한에 붙는 편이 낫다.
        assertThat(ProcessingEstimate.leadDays(1, Long.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void nothingIsEstimatedForNothing() {
        assertThatThrownBy(() -> ProcessingEstimate.fee(1_000, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProcessingEstimate.leadDays(1_000, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
