package com.ssafy.upcycle.evidence.service;

import com.ssafy.upcycle.evidence.dto.EvidenceListResponse;
import com.ssafy.upcycle.evidence.entity.EvidenceFile;
import com.ssafy.upcycle.evidence.entity.EvidenceReferenceType;
import com.ssafy.upcycle.evidence.repository.EvidenceFileRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 대상의 증빙 목록. S15P21E104-286 §6-1.
 *
 * <h2>왜 업로드 서비스에서 떼어냈는가</h2>
 *
 * <p>이 조회는 원래 {@code EvidenceUploadService#findAll(referenceType, referenceId)} 였고
 * <b>요청자를 아예 받지 않았다.</b> 인가를 붙이려면 요청자가 필요한데, 인자 없는 조회
 * 메서드를 그 클래스에 남겨 두면 <b>인가를 우회하는 경로가 그대로 남는다</b> — 다음 사람이
 * 그것을 부르면 컴파일도 테스트도 통과하고, 목록이 다시 열린다.
 *
 * <p>그래서 조회를 여기로 옮기고 업로드 쪽에서는 지웠다. 목록을 얻는 길이 하나뿐이고,
 * 그 길에 요청자가 <b>필수 인자</b>로 들어 있다.
 *
 * <h2>응답이 두 모양인 이유</h2>
 *
 * <p>{@link EvidenceListResponse} 주석에 버린 안까지 적어 두었다. 요약하면 목록을 통째로
 * 막으면 추적성 화면이 대부분 방문자에게 빈칸이 되고, 그대로 열어 두면 파일명으로 거래
 * 상대가 새어 나간다. 그래서 <b>보는 사람에 따라 응답 모양을 바꾸고 그 사실을 응답에
 * 적는다.</b>
 *
 * <h2>인증은 그대로 필요하다</h2>
 *
 * <p>{@code SecurityConfig} 를 고치지 않았다. 비회원에게 요약을 열지는
 * Matrix §6-3 · §6-4 (기획 결정)에 걸려 있고 <b>아직 미결</b>이라, 이번 변경은
 * <b>인증된 사용자 사이의 구분만</b> 한다. 비회원은 지금도 401 이다.
 */
@Service
@RequiredArgsConstructor
public class EvidenceListService {

    private final EvidenceFileRepository evidenceFileRepository;
    private final EvidenceAccessPolicy accessPolicy;

    /**
     * <b>살아 있는</b> 증빙 목록. 관계가 있으면 전체, 없으면 요약만.
     *
     * <p>대상이 없으면 <b>404 가 아니라 빈 요약</b>이다. 목록을 묻는 요청이라 그것이 정확한
     * 답이고, 404 로 답하면 <b>소재 id 의 존재 여부를 훑을 수 있는 신호</b>가 된다.
     * 대상 존재는 업로드할 때 확인한다.
     *
     * <p>소프트 삭제된 행은 빠진다 (S15P21E104-162). 그래서 요약 개수도 살아 있는 것만
     * 센다 — 지운 증빙이 개수에 남으면 "있다는데 안 보인다" 는 문의가 된다.
     *
     * <p>정렬은 리포지토리가 고정한다 (업로드 순서). 여기서 다시 정렬하지 않는다.
     *
     * @param requesterId JWT {@code sub}. 쿼리 파라미터로 받지 않는다 — 받으면 남의 id 를
     *     써서 전체 응답을 얻을 수 있고, 그건 인가가 없는 것과 같다
     */
    @Transactional(readOnly = true)
    public EvidenceListResponse findAll(
            Long requesterId, EvidenceReferenceType referenceType, Long referenceId) {
        List<EvidenceFile> evidence = evidenceFileRepository
                .findByReferenceTypeAndReferenceIdAndDeletedAtIsNullOrderByUploadedAtAscIdAsc(
                        referenceType, referenceId);
        return accessPolicy.canReadFully(requesterId, referenceType, referenceId, evidence)
                ? EvidenceListResponse.full(evidence)
                : EvidenceListResponse.summary(evidence);
    }
}
