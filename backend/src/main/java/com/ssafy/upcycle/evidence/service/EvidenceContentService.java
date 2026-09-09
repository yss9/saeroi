package com.ssafy.upcycle.evidence.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.storage.FileContent;
import com.ssafy.upcycle.common.storage.FileHash;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.evidence.EvidenceErrorCode;
import com.ssafy.upcycle.evidence.entity.EvidenceFile;
import com.ssafy.upcycle.evidence.repository.EvidenceFileRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
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
     *
     * <h2>돌려주기 전에 해시를 다시 계산한다 — 여기서 예외를 벗어나지 않는다</h2>
     *
     * <p>저장소 바이트가 업로드 이후 바뀌었으면(디스크 손상, 수동 변조) 재계산한 해시가
     * {@code evidence_file.file_hash} 와 달라진다. 온체인 {@code verifyEvidence} 가 대조할
     * 값이 바로 이 컬럼이라({@code EvidenceHashIntegrityTests} 클래스 주석 참고), 어긋난
     * 바이트를 그대로 흘려보내면 "그 파일이 그 증빙이다" 라는 보증이 조용히 깨진다.
     * 그래서 여기서 막는다 — 컨트롤러까지 내려보낸 뒤에 검사하면 이미 응답이 나가기
     * 시작했을 수 있다.
     *
     * <h2>왜 스트리밍 대조가 아니라 통째로 읽는가</h2>
     *
     * <p>파일 하나는 업로드 시점에 10MB 로 상한이 걸린다({@code FileStorageErrorCode
     * #FILE_TOO_LARGE}). 그 상한 안에서는 메모리에 올려 한 번에 대조하는 편이 스트리밍
     * 대조보다 <b>더 안전하다</b> — 스트리밍이면 앞부분 바이트가 이미 서블릿 응답으로
     * 나간 뒤에야 뒷부분에서 불일치를 발견할 수 있고, 그때는 "돌려주지 않는다" 는 약속을
     * 지킬 수 없다. 10MB 는 매 읽기마다 버퍼링해도 감당할 수 있는 크기다.
     */
    @Transactional(readOnly = true)
    public FileContent read(Long requesterId, Long evidenceFileId) {
        EvidenceFile evidence = evidenceFileRepository
                .findByIdAndDeletedAtIsNull(evidenceFileId)
                .orElseThrow(() -> new BusinessException(EvidenceErrorCode.EVIDENCE_NOT_FOUND));
        accessPolicy.requireReadable(requesterId, evidence);
        FileContent stored = fileStorageService.read(evidence.getFileUrl());
        return verifyAndBuffer(stored, evidence.getFileHash());
    }

    /**
     * 저장소가 돌려준 스트림을 끝까지 읽어 해시를 재계산하고, 대조한 뒤 다시 스트림으로
     * 감싸 돌려준다. 원본 스트림은 여기서 끝까지 읽으므로 여기서 닫는다 — 남는 것은
     * 메모리에 올라간 바이트뿐이라 호출부가 새 스트림을 늦게 닫아도 파일 핸들이 남지
     * 않는다.
     */
    private FileContent verifyAndBuffer(FileContent stored, String expectedHash) {
        byte[] bytes;
        try (InputStream original = stored.stream()) {
            bytes = original.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        MessageDigest digest = FileHash.sha256();
        digest.update(bytes);
        String actualHash = FileHash.format(digest);
        if (!actualHash.equals(expectedHash)) {
            throw new BusinessException(EvidenceErrorCode.EVIDENCE_INTEGRITY_VIOLATION);
        }

        return new FileContent(new ByteArrayInputStream(bytes), bytes.length, stored.contentType());
    }
}
