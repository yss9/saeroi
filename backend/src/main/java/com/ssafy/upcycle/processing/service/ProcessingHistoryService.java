package com.ssafy.upcycle.processing.service;

import com.ssafy.upcycle.material.service.ProcessingHistory;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.processing.repository.JobProcessType;
import com.ssafy.upcycle.processing.repository.ProcessingRequestTypeRepository;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ProcessingHistory} 의 구현. 작업 id 를 가공 종류로 바꾼다.
 *
 * <p>쿼리 한 번으로 전부 읽고 여기서 접는다 — 작업 수만큼 쿼리가 나가면 소재 목록 조회가
 * 소재 수 × 세대 수만큼의 쿼리가 된다.
 */
@Service
@RequiredArgsConstructor
public class ProcessingHistoryService implements ProcessingHistory {

    private final ProcessingRequestTypeRepository requestTypes;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Set<ProcessType>> findConfirmedProcessTypes(
            Collection<Long> processingJobIds) {
        if (processingJobIds == null || processingJobIds.isEmpty()) {
            // 빈 컬렉션으로 IN 절을 만들면 DB 마다 문법이 갈린다. 부를 이유도 없다.
            return Map.of();
        }
        Map<Long, Set<ProcessType>> byJob = new HashMap<>();
        for (JobProcessType row : requestTypes.findConfirmedProcessTypes(processingJobIds)) {
            byJob.computeIfAbsent(row.jobId(), key -> EnumSet.noneOf(ProcessType.class))
                    .add(row.processType());
        }
        return byJob;
    }
}
