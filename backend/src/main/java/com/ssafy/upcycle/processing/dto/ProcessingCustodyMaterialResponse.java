package com.ssafy.upcycle.processing.dto;

import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.processing.entity.ProcessingJobStatus;
import java.time.LocalDateTime;

/**
 * Processor가 가공을 위해 배송받는 소재다.
 *
 * <p>이 응답은 소유 잔액이 아니다. 가공 중에도 토큰과 {@code material_balance}의 소유자는
 * Brand로 유지하고, Processor가 물리적으로 보관하는 사실만 Job에서 파생해 보여준다.
 */
public record ProcessingCustodyMaterialResponse(
        Long jobId,
        String jobCode,
        Long materialId,
        String materialCode,
        String materialName,
        MaterialType materialType,
        long quantity,
        ProcessingJobStatus status,
        LocalDateTime shippedAt,
        LocalDateTime receivedAt) {
}
