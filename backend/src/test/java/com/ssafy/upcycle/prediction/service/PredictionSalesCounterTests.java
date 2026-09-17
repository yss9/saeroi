package com.ssafy.upcycle.prediction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.product.repository.CanceledProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderItemRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PredictionSalesCounterTests {
    @Mock ProductOrderItemRepository activeItems;
    @Mock CanceledProductOrderItemRepository canceledItems;
    @InjectMocks PredictionSalesCounter counter;

    @Test
    void countsPaidItemsMinusCancellationsMadeBeforeTheSalesDeadline() {
        Long modelId = 7L;
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 15, 0, 0);
        when(activeItems.countPaidItems(modelId, start, end)).thenReturn(62L);
        when(canceledItems.countOriginallyPaidItems(modelId, start, end)).thenReturn(8L);
        when(canceledItems.countCanceledPaidItemsAsOf(modelId, start, end)).thenReturn(5L);

        PredictionSalesCounter.Snapshot result = counter.calculate(modelId, start, end);

        assertThat(result.grossPaidQuantity()).isEqualTo(70);
        assertThat(result.canceledQuantity()).isEqualTo(5);
        assertThat(result.netSoldQuantity()).isEqualTo(65);
    }
}
