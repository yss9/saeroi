package com.ssafy.upcycle.evidence.repository;

import com.ssafy.upcycle.evidence.entity.EvidenceFile;
import com.ssafy.upcycle.evidence.entity.EvidenceReferenceType;
import java.util.List;
import java.util.Optional;
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
     *
     * <p><b>소프트 삭제된 행은 빠진다.</b> 목록에서 그것이 걸러지는 지점은 여기 하나다.
     *
     * <p>Hibernate 의 엔티티 레벨 필터({@code @SQLRestriction})를 쓰지 않았다. 그러면
     * <b>삭제된 행을 일부러 읽어야 하는 경로</b>(정책의 "이미 지워졌나" 판정, 감사 조회)가
     * 필터를 우회할 방법이 없어 네이티브 쿼리로 도망가게 된다. 조건은 명시적으로 둔다.
     *
     * <p>인덱스 {@code (reference_type, reference_id, deleted_at)} 를 그대로 탄다.
     */
    List<EvidenceFile> findByReferenceTypeAndReferenceIdAndDeletedAtIsNullOrderByUploadedAtAscIdAsc(
            EvidenceReferenceType referenceType, Long referenceId);

    /**
     * 살아 있는 증빙 한 건. 조회·삭제가 모두 이것으로 시작한다.
     *
     * <p>{@code findById} 를 쓰고 나중에 삭제 여부를 보는 방식이 아니다 — 그 검사를 한 곳에서
     * 빼먹으면 <b>지워진 증빙의 바이트를 내보내거나 두 번 지우게 된다.</b> 조건을 쿼리에
     * 넣어 두면 빼먹을 자리가 없다.
     */
    Optional<EvidenceFile> findByIdAndDeletedAtIsNull(Long id);

    long countByReferenceTypeAndReferenceIdAndDeletedAtIsNull(
            EvidenceReferenceType referenceType, Long referenceId);
}
