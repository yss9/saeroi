package com.ssafy.upcycle.evidence.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.storage.FileContent;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.evidence.EvidenceErrorCode;
import com.ssafy.upcycle.evidence.entity.EvidenceFile;
import com.ssafy.upcycle.evidence.repository.EvidenceFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 증빙 파일 바이트를 되돌려준다. S15P21E104-162.
 *
 * <h2>왜 우리 서버를 통과시키는가</h2>
 *
 * <p>{@code evidence_file.file_url} 에는 <b>저장 키</b>가 들어 있다. 클라이언트는 그 값으로
 * 파일을 받을 수 없다 — 저장소는 외부에 열려 있지 않고, 열려 있어도 인가가 없다.
 *
 * <p>대안이었던 presigned URL 을 왜 버렸는지는 {@code FileStorageService#read} 주석에 있다.
 * 요약하면 <b>로컬 구현에 대응물이 없고</b>(저장소 전환 시 조회가 깨진다) <b>발급된 링크에는
 * 인가가 붙지 않는다.</b>
 *
 * <h2>스트림을 닫지 않는다</h2>
 *
 * <p>여기서 닫으면 컨트롤러가 응답으로 흘리기 전에 닫혀 <b>빈 본문</b>이 나간다.
 * 닫는 책임은 응답을 쓰는 쪽에 있다 ({@code EvidenceController}).
 */
@Service
@RequiredArgsConstructor
public class EvidenceContentService {

    private final EvidenceFileRepository evidenceFileRepository;
    private final EvidenceAccessPolicy accessPolicy;
    private final FileStorageService fileStorageService;

    /**
     * 인가를 통과한 요청에만 바이트를 열어 준다.
     *
     * <p>순서가 중요하다 — <b>인가를 먼저 보고 그 다음에 저장소를 만진다.</b> 반대로 두면
     * 권한 없는 요청자가 응답 시간 차이로 "그 파일이 저장소에 있는지" 를 알아낼 수 있다.
     *
     * <p>{@code readOnly} 트랜잭션이지만 <b>스트림을 트랜잭션 안에서 다 읽지 않는다.</b>
     * 스트림은 저장소에서 오는 것이고 DB 커넥션과 무관하므로, 메서드가 끝나 트랜잭션이
     * 닫혀도 유효하다.
     */
    @Transactional(readOnly = true)
    public FileContent read(Long requesterId, Long evidenceFileId) {
        EvidenceFile evidence = evidenceFileRepository
                .findByIdAndDeletedAtIsNull(evidenceFileId)
                .orElseThrow(() -> new BusinessException(EvidenceErrorCode.EVIDENCE_NOT_FOUND));
        accessPolicy.requireReadable(requesterId, evidence);
        return fileStorageService.read(evidence.getFileUrl());
    }
}
