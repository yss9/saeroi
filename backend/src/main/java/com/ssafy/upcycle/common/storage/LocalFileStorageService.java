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
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

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
@Service
public class LocalFileStorageService implements FileStorageService {

    /** 정책 문서의 화이트리스트. 확장자 → 허용 미디어 타입. */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("pdf");

    private static final Set<String> IMAGE_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");
    private static final String PDF_CONTENT_TYPE = "application/pdf";

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
        if (size <= 0) {
            throw new BusinessException(FileStorageErrorCode.FILE_EMPTY);
        }
        String extension = requireAllowedExtension(originalFilename);
        requireContentTypeMatches(extension, contentType);

        String key = deriveKey(reference, extension);
        Path target = root.resolve(key);
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            // 임시 파일은 최종 경로와 같은 디렉터리에 만든다. 다른 파일시스템에 있으면
            // move 가 원자적이지 않고 복사로 떨어진다.
            temporary = Files.createTempFile(target.getParent(), ".upload-", ".part");

            MessageDigest digest = sha256();
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
                    "0x" + HexFormat.of().formatHex(digest.digest()));
        } catch (IOException e) {
            throw new BusinessException(FileStorageErrorCode.FILE_STORAGE_FAILED);
        } finally {
            deleteQuietly(temporary);
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

    private static String requireAllowedExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException(FileStorageErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            throw new BusinessException(FileStorageErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        String extension = originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!IMAGE_EXTENSIONS.contains(extension) && !DOCUMENT_EXTENSIONS.contains(extension)) {
            throw new BusinessException(FileStorageErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        return extension;
    }

    /**
     * 확장자와 선언된 미디어 타입이 일치해야 한다.
     *
     * <p>확장자만 보면 {@code photo.jpg} 라는 이름의 무엇이든 통과한다. 두 값이 서로를
     * 지지해야 하고, 어긋나면 {@code FILE_TYPE_NOT_ALLOWED} 와 다른 코드를 준다 —
     * "받지 않는 종류" 와 "이름이 내용과 다름" 은 사용자에게 다른 안내가 필요하다.
     */
    private static void requireContentTypeMatches(String extension, String contentType) {
        String declared = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        boolean matches = DOCUMENT_EXTENSIONS.contains(extension)
                ? PDF_CONTENT_TYPE.equals(declared)
                : IMAGE_CONTENT_TYPES.contains(declared) && imageMatches(extension, declared);
        if (!matches) {
            throw new BusinessException(FileStorageErrorCode.FILE_TYPE_MISMATCH);
        }
    }

    /** {@code jpg}·{@code jpeg} 는 같은 타입이다. 나머지는 이름과 타입이 1:1 이다. */
    private static boolean imageMatches(String extension, String contentType) {
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg".equals(contentType);
            case "png" -> "image/png".equals(contentType);
            case "webp" -> "image/webp".equals(contentType);
            default -> false;
        };
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

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 모든 JVM 이 제공한다. 없으면 환경이 깨진 것이라 복구 대상이 아니다.
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
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
