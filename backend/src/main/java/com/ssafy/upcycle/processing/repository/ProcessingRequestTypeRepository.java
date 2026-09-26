package com.ssafy.upcycle.processing.repository;

import com.ssafy.upcycle.processing.entity.ProcessingRequestType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessingRequestTypeRepository extends JpaRepository<ProcessingRequestType, Long> {

    List<ProcessingRequestType> findAllByProcessingRequestIdOrderBySequenceNoAsc(
            Long processingRequestId);

    /**
     * 여러 가공 작업이 <b>실제로 수행한</b> 가공 종류를 한 번에 읽는다.
     *
     * <p>작업 하나씩
     * {@link #findAllByProcessingRequestIdOrderBySequenceNoAsc(Long)} 를 부르는 경로가 이미
     * 있지만({@code ProductJourneyQueryService}), 그쪽은 여정 하나에 노드가 몇 개뿐이다.
     * 소재 목록의 가공 이력은 소재마다 혈통을 거슬러 올라간 작업 전부가 대상이라
     * 같은 방식이면 그대로 N+1 이 된다.
     *
     * <p><b>{@code CONFIRMED} 만 센다.</b> {@code processing_request_type} 은 "무엇을
     * 해달라고 요청했는가" 이지 "무엇이 실제로 됐는가" 가 아니다. 브랜드 승인 전에 세면
     * 요청만 넣고 아직 아무 일도 일어나지 않은 가공이 완료된 이력으로 보인다 —
     * {@code ProcessingOutputRepository#existsConfirmedOutputMaterial} 과 같은 기준이다.
     *
     * <p>{@code sequenceNo} 로 정렬하지 않는다. 호출부가 작업 여러 건을 합집합으로 접기
     * 때문에 한 작업 안에서의 순서는 살아남지 못한다 — 남는 순서를 정하는 것은 호출부다.
     */
    @Query("""
            select new com.ssafy.upcycle.processing.repository.JobProcessType(j.id, t.processType)
            from ProcessingRequestType t
            join ProcessingJob j on j.processingRequestId = t.processingRequestId
            where j.id in :jobIds
              and j.status = com.ssafy.upcycle.processing.entity.ProcessingJobStatus.CONFIRMED
            """)
    List<JobProcessType> findConfirmedProcessTypes(@Param("jobIds") Collection<Long> jobIds);
}
