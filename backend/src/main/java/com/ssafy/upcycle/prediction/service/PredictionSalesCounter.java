package com.ssafy.upcycle.prediction.service;

import com.ssafy.upcycle.product.repository.CanceledProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderItemRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PredictionSalesCounter {
    private final ProductOrderItemRepository activeItems;
    private final CanceledProductOrderItemRepository canceledItems;

    @Transactional(readOnly = true)
    public Snapshot calculate(Long productModelId, LocalDateTime startsAt, LocalDateTime endsAt) {
        long activePaid = activeItems.countPaidItems(productModelId, startsAt, endsAt);
        long canceledPaid = canceledItems.countOriginallyPaidItems(productModelId, startsAt, endsAt);
        long canceledByDeadline = canceledItems.countCanceledPaidItemsAsOf(productModelId, startsAt, endsAt);
        long gross = Math.addExact(activePaid, canceledPaid);
        long net = Math.max(0, gross - canceledByDeadline);
        return new Snapshot(Math.toIntExact(gross), Math.toIntExact(canceledByDeadline), Math.toIntExact(net));
    }

    public record Snapshot(int grossPaidQuantity, int canceledQuantity, int netSoldQuantity) { }
}

