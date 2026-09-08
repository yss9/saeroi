package com.ssafy.upcycle.common.storage;

import com.ssafy.upcycle.common.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 로컬 파일시스템 구현. MinIO/S3 구현체가 오기 전까지의 정본이다.
 *
 * <p>정책은 {@code docs/superpowers/specs/2026-09-07-file-upload-policy.md} 가 정한다.
 * 이 클래스는 그 정책 중 <b>파일 한 개에 적용되는 것</b>만 강제한다 — 확장자·미디어 타입·
 * 빈 파일. 크기 상한과 개수 상한은 요청 단위라 업로드 엔드포인트가 본다.
 *
 * <h2>경로를 파생시키는 것이 이 클래스의 핵심</h2>
 *
 * <pre>
 *   {reference_type}/{reference_id}/{yyyy}/{MM}/{uuid}.{ext}
 * </pre>
 *
 * <p>클라이언트가 준 이름은 <b>확장자 판정에만</b> 쓰고 경로에는 들어가지 않는다.
 * {@code ../../etc/passwd} 같은 이름이 와도 경로가 되지 않으므로 디렉터리 탈출을
 * 막을 필요 자체가 없다. 방어하는 것보다 불가능하게 만드는 편이 안전하다.
 *
 * <h2>해시는 쓰면서 계산한다</h2>
 *
 * <p>{@link DigestOutputStream} 으로 감싸 한 번의 스트림 통과로 기록과 해시를 함께 만든다.
 * 저장 후 다시 읽어 계산하면 <b>기록된 것과 다른 해시</b>가 나올 여지가 생기는데,
 * 그 해시는 온체인에 올라가므로 되돌릴 수 없다.
 *
 * <h2>임시 파일을 거친다</h2>
 *
 * <p>바로 최종 경로에 쓰면 중간에 실패했을 때 <b>깨진 파일이 정상 경로에 남는다.</b>
 * 임시 파일에 다 쓰고 원자적으로 옮긴다. 옮기기 전에 실패하면 임시 파일만 지운다.
 */
public class LocalFileStorageService implements FileStorageService {



    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");

    private final Path root;
    private final Clock clock;

    public LocalFileStorageService(FileStorageProperties properties, Clock clock) {
        this.root = Path.of(properties.root()).toAbsolutePath().normalize();
        this.clock = clock;
    }

    @Override
    public StoredFile store(
            FileReference reference,
            String originalFilename,
            String contentType,
            InputStream content,
            long size) {
        // 형식 규칙은 FileContentPolicy 가 갖는다 — S3 구현과 같은 규칙을 써야 한다.
        // 각자 화이트리스트를 들고 있으면 한쪽에서만 svg 가 통과하는 상태가 만들어진다.
        String extension = FileContentPolicy.requireAcceptable(originalFilename, contentType, size);

        String key = deriveKey(reference, extension);
        Path target = root.resolve(key);
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            // 임시 파일은 최종 경로와 같은 디렉터리에 만든다. 다른 파일시스템에 있으면
            // move 가 원자적이지 않고 복사로 떨어진다.
            temporary = Files.createTempFile(target.getParent(), ".upload-", ".part");

            MessageDigest digest = FileHash.sha256();
            long written;
            try (OutputStream out = Files.newOutputStream(temporary);
                    DigestOutputStream digesting = new DigestOutputStream(out, digest)) {
                written = content.transferTo(digesting);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            temporary = null;

            return new StoredFile(
                    key,
                    originalFilename,
                    contentType,
                    written,
                    FileHash.format(digest));
        } catch (IOException e) {
            throw new BusinessException(FileStorageErrorCode.FILE_STORAGE_FAILED);
        } finally {
            deleteQuietly(temporary);
        }
    }

    /**
     * 저장된 파일을 열어 준다.
     *
     * <p>{@code resolveExisting} 을 거치므로 <b>오염된 키가 루트를 벗어날 수 없다.</b>
     * 이 경로가 특히 중요하다 — 읽기는 키를 DB 에서 받고, 그 값이 어떤 이유로든
     * {@code ../../} 를 담게 되면 여기가 서버의 임의 파일을 응답으로 내보내는 문이 된다.
     *
     * <p>미디어 타입은 확장자에서 파생한다. 로컬에는 저장해 둔 메타데이터가 없고,
     * {@code Files.probeContentType} 은 OS 설정에 따라 답이 달라져 같은 파일이 서버마다
     * 다른 타입으로 나간다.
     */
    @Override
    public FileContent read(String storageKey) {
        Path file = resolveExisting(storageKey);
        if (!Files.isRegularFile(file)) {
            throw new BusinessException(FileStorageErrorCode.FILE_NOT_FOUND);
        }
        try {
            // 스트림은 호출부가 닫는다. 여기서 try-with-resources 로 감싸면 컨트롤러가
            // 응답으로 흘리기 전에 닫혀 빈 본문이 나간다.
            return new FileContent(
                    Files.newInputStream(file),
                    Files.size(file),
                    FileContentPolicy.mediaTypeOf(storageKey));
        } catch (IOException e) {
            throw new BusinessException(FileStorageErrorCode.FILE_STORAGE_FAILED);
        }
    }

    @Override
    public boolean delete(String storageKey) {
        try {
            return Files.deleteIfExists(resolveExisting(storageKey));
        } catch (IOException e) {
            // 삭제 실패로 예외를 던지지 않는다. 호출부는 롤백 경로에서 이걸 부르는데,
            // 여기서 던지면 원래 실패 원인을 덮어쓴다.
            return false;
        }
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.isRegularFile(resolveExisting(storageKey));
    }

    /**
     * 저장 경로를 만든다. <b>클라이언트 입력이 한 조각도 들어가지 않는다.</b>
     *
     * <p>날짜는 업무 시각이 아니라 업로드 시각이다 — 파일을 찾는 기준이 "언제 올렸나" 이고,
     * 업무 시각으로 나누면 과거 데이터를 보정할 때 파일이 옮겨져야 한다.
     */
    private String deriveKey(FileReference reference, String extension) {
        LocalDate today = LocalDate.now(clock);
        return String.join(
                "/",
                reference.type(),
                String.valueOf(reference.id()),
                today.format(YEAR),
                today.format(MONTH),
                UUID.randomUUID() + "." + extension);
    }

    /**
     * 키를 루트 아래 경로로 바꾼다.
     *
     * <p>{@code store} 가 만든 키만 들어오는 것이 정상이지만, 삭제·조회는 DB 에 저장된 값을
     * 그대로 받는다. 그 값이 오염됐을 때 루트를 벗어나지 않도록 확인한다 —
     * 파생 경로를 쓰는 이유가 탈출을 불가능하게 만드는 것인데, 되찾는 쪽에 구멍이 있으면
     * 그 노력이 무의미하다.
     */
    private Path resolveExisting(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException(FileStorageErrorCode.FILE_STORAGE_FAILED);
        }
        return resolved;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 임시 파일이 남는 것은 디스크만 쓴다. 원래 실패를 덮지 않는다.
        }
    }
}
