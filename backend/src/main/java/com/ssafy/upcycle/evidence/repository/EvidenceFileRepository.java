package com.ssafy.upcycle.evidence.repository;

import com.ssafy.upcycle.evidence.entity.EvidenceFile;
import com.ssafy.upcycle.evidence.entity.EvidenceReferenceType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceFileRepository extends JpaRepository<EvidenceFile, Long> {

    /**
     * 한 대상의 증빙 목록. <b>업로드 순서(오래된 것 먼저)</b>로 고정한다.
     *
     * <p>정렬을 지정하지 않으면 DB 가 편한 순서로 돌려주고, 같은 소재를 두 번 열었을 때
     * 사진 순서가 달라 보인다. 증빙은 "이전 사진 → 이후 사진" 처럼 시간 순서가 뜻을 갖는
     * 자료라 올린 순서가 맞다. 같은 시각에 여러 건이 들어오면 id 로 갈라 결정적으로 만든다.
     *
     * <p>{@code referenceType} 이 조건에 반드시 있어야 한다 — {@code reference_id} 는
     * 타입마다 따로 매겨지므로 id 만 보면 7번 가공작업의 증빙을 7번 소재의 것으로 읽는다.
     */
    List<EvidenceFile> findByReferenceTypeAndReferenceIdOrderByUploadedAtAscIdAsc(
            EvidenceReferenceType referenceType, Long referenceId);
}
