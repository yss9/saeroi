package com.ssafy.upcycle.evidence.service;

import com.ssafy.upcycle.evidence.dto.EvidenceThumbnailResponse;
import com.ssafy.upcycle.evidence.entity.EvidenceFile;
import com.ssafy.upcycle.evidence.entity.EvidenceReferenceType;
import com.ssafy.upcycle.evidence.entity.EvidenceType;
import com.ssafy.upcycle.evidence.repository.EvidenceFileRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여러 대상의 대표 사진을 한 번에 찾는다. 마켓플레이스 카드·소재 상세 판매 정보 탭이
 * 쓴다 (S15P21E104-438/439 연장).
 *
 * <h2>왜 목록 조회({@code EvidenceListService})와 다른 문인가</h2>
 *
 * <p>목록 조회는 <b>관계가 있어야 '전체'를 준다</b>, 없으면 요약만 준다 — 계근표 파일명 같은
 * 민감한 값이 섞여 있어서다. 여기는 <b>사진류만</b> 다루고 그 사진은 관계와 무관하게
 * 공개한다({@code EvidenceAccessPolicy#requireReadable} 의 같은 예외). 그래서 요청자 id 를
 * 받지 않는다 — 판정할 관계가 없다.
 */
@Service
@RequiredArgsConstructor
public class EvidenceThumbnailService {

    private static final List<EvidenceType> PUBLIC_PHOTO_TYPES =
            List.of(
                    EvidenceType.BEFORE_PHOTO,
                    EvidenceType.AFTER_PHOTO,
                    EvidenceType.WEIGHT_PHOTO,
                    EvidenceType.PROCESS_PHOTO);

    private final EvidenceFileRepository evidenceFileRepository;

    /**
     * 대상마다 <b>가장 먼저 올라온</b> 사진 한 장. 사진이 없는 대상은 결과에 없다.
     *
     * <p>대상 하나에 사진이 여러 장이어도 맵이라 하나만 남는다 — 리포지토리가 정렬을
     * {@code referenceId, uploadedAt, id} 오름차순으로 고정해 주므로, 같은 {@code referenceId}
     * 를 두 번째로 만나면 이미 첫 장이 들어 있어 덮어쓰지 않는다({@code putIfAbsent}).
     */
    @Transactional(readOnly = true)
    public Map<Long, EvidenceThumbnailResponse> findThumbnails(
            EvidenceReferenceType referenceType, List<Long> referenceIds) {
        List<EvidenceFile> photos = evidenceFileRepository
                .findByReferenceTypeAndReferenceIdInAndEvidenceTypeInAndDeletedAtIsNullOrderByReferenceIdAscUploadedAtAscIdAsc(
                        referenceType, referenceIds, PUBLIC_PHOTO_TYPES);

        Map<Long, EvidenceThumbnailResponse> thumbnails = new LinkedHashMap<>();
        for (EvidenceFile photo : photos) {
            thumbnails.putIfAbsent(photo.getReferenceId(), EvidenceThumbnailResponse.from(photo));
        }
        return thumbnails;
    }
}
