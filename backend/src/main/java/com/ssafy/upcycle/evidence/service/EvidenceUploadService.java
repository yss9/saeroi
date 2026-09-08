package com.ssafy.upcycle.evidence.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.storage.FileReference;
import com.ssafy.upcycle.common.storage.FileStorageErrorCode;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.common.storage.StoredFile;
import com.ssafy.upcycle.evidence.EvidenceErrorCode;
import com.ssafy.upcycle.evidence.dto.EvidenceFileResponse;
import com.ssafy.upcycle.evidence.dto.EvidenceUploadRequest;
import com.ssafy.upcycle.evidence.entity.EvidenceFile;
import com.ssafy.upcycle.evidence.entity.EvidenceReferenceType;
import com.ssafy.upcycle.evidence.repository.EvidenceFileRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 증빙 업로드. S15P21E104-249 · S15P21E104-252.
 *
 * <p>정책은 {@code docs/superpowers/specs/2026-09-07-file-upload-policy.md} 가 정한다.
 * 파일 하나에 적용되는 것(확장자 · 미디어 타입 · 빈 파일)은 {@link FileStorageService} 가
 * 이미 강제하므로, <b>여기서는 요청 단위 규칙만</b> 본다 — 개수와 크기다.
 *
 * <h2>디스크와 DB 를 함께 되돌릴 수 없다</h2>
 *
 * <p>파일 쓰기는 트랜잭션에 참여하지 않는다. DB 저장이 실패하면 롤백되지만 이미 기록된
 * 파일은 그대로 남는다. 그래서 <b>실패 경로에서 직접 지운다.</b>
 *
 * <p>반대 순서(DB 먼저)로 두지 않은 이유는 저장 키와 해시가 파일을 실제로 써 봐야
 * 나오는 값이기 때문이다. 해시를 나중에 채우는 방식이면 <b>해시 없는 증빙 행</b>이
 * 잠시 존재하고, 그 창에서 온체인 기록이 일어나면 무엇의 해시인지 알 수 없게 된다.
 *
 * <p>지우기가 실패하면 파일만 남는다. 디스크를 쓰는 것으로 끝이고 DB 에는 그 파일을
 * 가리키는 행이 없어 아무도 보지 못한다. <b>원래 실패 원인을 덮는 것보다 낫다</b> —
 * {@link FileStorageService#delete} 가 예외를 던지지 않는 이유가 그것이다.
 */
@Service
@RequiredArgsConstructor
public class EvidenceUploadService {

    /** 정책 문서의 요청 단위 상한. 소재 하나의 증빙은 사진 몇 장이다. */
    private static final int MAX_FILES_PER_REQUEST = 10;

    /** 파일 하나 10MB. 휴대폰 사진이 3~8MB 라 실제 입력을 받으면서 50MB 업로드는 막는다. */
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    private final FileStorageService fileStorageService;
    private final EvidenceFileRepository evidenceFileRepository;
    private final EvidenceReferenceValidator referenceValidator;
    private final UserRepository userRepository;

    /**
     * 증빙 여러 건을 올린다.
     *
     * <p>순서가 중요하다 — <b>검증을 모두 끝낸 뒤에 첫 바이트를 쓴다.</b> 파일을 쓰다가
     * 세 번째에서 개수 초과를 발견하면 이미 쓴 둘을 지워야 하고, 그 지우기가 실패하면
     * 고아 파일이 남는다. 쓰기 전에 걸러내면 그 경로 자체가 없어진다.
     *
     * @param uploaderId JWT {@code sub} 에서 온 사용자 id. 요청 바디로 받지 않는다 —
     *     받으면 남의 이름으로 증빙을 올릴 수 있고, 증빙은 온체인에 남는 기록이다
     */
    @Transactional
    public List<EvidenceFileResponse> upload(
            Long uploaderId, EvidenceUploadRequest request, List<MultipartFile> files) {
        requireActiveUploader(uploaderId);
        referenceValidator.requireExists(request.referenceType(), request.referenceId());
        requireUploadableFiles(files);

        FileReference reference =
                new FileReference(request.referenceType().name(), request.referenceId());

        List<StoredFile> stored = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                stored.add(store(reference, file));
            }
            List<EvidenceFile> saved = evidenceFileRepository.saveAll(stored.stream()
                    .map(file -> EvidenceFile.record(
                            request.referenceType(),
                            request.referenceId(),
                            file.storageKey(),
                            file.hash(),
                            request.evidenceType(),
                            uploaderId))
                    .toList());
            return saved.stream().map(EvidenceFileResponse::from).toList();
        } catch (RuntimeException failure) {
            // DB 저장이 실패했거나 두 번째 파일에서 형식 오류가 났다. 이미 쓴 것을 지운다.
            stored.forEach(file -> fileStorageService.delete(file.storageKey()));
            throw failure;
        }
    }

    /**
     * 한 대상의 <b>살아 있는</b> 증빙 목록. 업로드 순서를 리포지토리가 보장한다.
     *
     * <p>소프트 삭제된 행은 빠진다 (S15P21E104-162). 행이 남아 있는 것은 온체인 대조를
     * 위한 것이고, 화면에는 지워진 증빙이 보이지 않는 것이 사용자가 요청한 결과다.
     *
     * <p>업로드 요청 DTO 를 재사용하지 않는다 — 그쪽은 {@code evidenceType} 이 필수인데
     * 조회에는 그 값이 없다. 재사용하면 필수 필드에 {@code null} 을 넣어 통과시키게 되고,
     * 그 순간 DTO 의 제약이 거짓이 된다.
     */
    @Transactional(readOnly = true)
    public List<EvidenceFileResponse> findAll(
            EvidenceReferenceType referenceType, Long referenceId) {
        return evidenceFileRepository
                .findByReferenceTypeAndReferenceIdAndDeletedAtIsNullOrderByUploadedAtAscIdAsc(
                        referenceType, referenceId)
                .stream()
                .map(EvidenceFileResponse::from)
                .toList();
    }

    private StoredFile store(FileReference reference, MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            return fileStorageService.store(
                    reference,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    content,
                    file.getSize());
        } catch (IOException unreadable) {
            // 업로드 스트림이 중간에 끊겼다. 저장소 실패와 같은 코드를 준다 —
            // 사용자가 할 수 있는 일(다시 시도)이 같다.
            throw new BusinessException(FileStorageErrorCode.FILE_STORAGE_FAILED);
        }
    }

    /**
     * 요청 단위 규칙.
     *
     * <p>크기는 {@code MultipartFile#getSize} 로 본다. 이 값은 <b>선언값이 아니라 실제
     * 버퍼링된 크기</b>다 — Spring 이 이미 다 읽은 뒤이므로 믿을 수 있다.
     *
     * <p>Spring 의 {@code max-file-size} 가 먼저 걸리는 경우도 있는데, 그건
     * {@code MaxUploadSizeExceededException} 이라 사용자에게 읽을 수 있는 문구가 되도록
     * {@code GlobalExceptionHandler} 가 같은 코드로 번역한다. <b>두 층에서 막는 것이
     * 중복이 아니다</b> — 앞 층은 서버를 지키고, 이 층은 읽을 수 있는 오류를 만든다.
     */
    private void requireUploadableFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(EvidenceErrorCode.EVIDENCE_FILE_MISSING);
        }
        if (files.size() > MAX_FILES_PER_REQUEST) {
            throw new BusinessException(FileStorageErrorCode.FILE_COUNT_EXCEEDED);
        }
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new BusinessException(FileStorageErrorCode.FILE_EMPTY);
            }
            if (file.getSize() > MAX_FILE_SIZE_BYTES) {
                throw new BusinessException(FileStorageErrorCode.FILE_TOO_LARGE);
            }
        }
    }

    /**
     * 업로더 확인.
     *
     * <p>JWT 를 믿지 않고 DB 를 다시 읽는 이유는 {@code MaterialRegistrationService} 와
     * 같다 — 토큰은 발급 시점의 스냅샷이라 정지된 계정이 만료 전까지 계속 올릴 수 있다.
     * 증빙은 온체인에 해시가 남는 되돌릴 수 없는 기록이다.
     *
     * <p><b>역할은 보지 않는다.</b> 증빙을 올릴 수 있는 역할이 대상마다 다르고
     * (소재는 등록자, 가공은 가공사), 그 판정은 대상 도메인의 규칙이다. 여기서 한 가지로
     * 정하면 나중에 도메인마다 예외를 뚫게 된다. 지금은 대상 존재 확인이 그 자리를 맡는다.
     */
    private void requireActiveUploader(Long uploaderId) {
        User uploader = userRepository
                .findById(uploaderId)
                .orElseThrow(() ->
                        new BusinessException(EvidenceErrorCode.EVIDENCE_UPLOADER_NOT_FOUND));
        if (!uploader.isActive()) {
            throw new BusinessException(EvidenceErrorCode.EVIDENCE_UPLOADER_NOT_FOUND);
        }
    }
}
