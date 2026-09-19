package com.ssafy.upcycle.processing.repository;

import com.ssafy.upcycle.processing.entity.*;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.ssafy.upcycle.processing.dto.ProcessingCustodyMaterialResponse;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ProcessingJob j where j.id=:id")
    Optional<ProcessingJob> findForUpdate(@Param("id") Long id);
    List<ProcessingJob> findTop100ByStatusAndPaymentExpiresAtLessThanEqualOrderById(
            ProcessingJobStatus status, LocalDateTime now);

    /**
     * 요청 하나에 작업 하나다. 견적이 사라지면서 요청과 작업이 함께 만들어지므로 둘은 1:1 이고,
     * 요청을 끝낼 때 딸린 작업을 찾는 경로가 필요하다.
     */
    Optional<ProcessingJob> findByProcessingRequestId(Long processingRequestId);

    /** 가공사가 수락을 답하지 않은 채 기한이 지난 작업. 만료 배치가 쓴다. */
    List<ProcessingJob> findTop100ByStatusAndAcceptExpiresAtLessThanEqualOrderById(
            ProcessingJobStatus status, LocalDateTime now);
    @Query("select j from ProcessingJob j where j.brandId=:actorId or j.processorId=:actorId order by j.createdAt desc, j.id desc")
    List<ProcessingJob> findAllForActor(@Param("actorId") Long actorId);

    @Query("""
            select new com.ssafy.upcycle.processing.dto.ProcessingCustodyMaterialResponse(
                j.id, j.jobCode, m.id, m.materialCode, m.name, m.materialType,
                i.quantity, j.status, j.shippedAt, j.receivedAt)
            from ProcessingJob j
            join ProcessingInput i on i.processingJobId = j.id
            join Material m on m.id = i.materialId
            where j.processorId = :processorId and j.status in :statuses
            order by j.createdAt desc, j.id desc
            """)
    List<ProcessingCustodyMaterialResponse> findCustodyMaterials(
            @Param("processorId") Long processorId,
            @Param("statuses") Collection<ProcessingJobStatus> statuses);
}
