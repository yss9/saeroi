package com.ssafy.upcycle.common.storage;

import com.ssafy.upcycle.common.exception.BusinessException;
import java.util.Locale;
import java.util.Set;

/**
 * 파일 하나에 적용되는 형식 규칙. {@code LocalFileStorageService} 에서 꺼내 온 것이다.
 *
 * <h2>왜 공유해야 하는가</h2>
 *
 * <p>저장 구현이 둘이 되었다(로컬 · S3). 화이트리스트를 각 구현이 따로 들고 있으면
 * <b>한쪽에서만 svg 가 통과하는 상태</b>가 만들어질 수 있고, 그건 우리 오리진에서
 * 저장형 XSS 를 서빙하는 것과 같다. 구현을 바꾸는 것은 설정 한 줄이라 그 차이가
 * 배포 시점까지 드러나지 않는다.
 *
 * <p>정책 문서: {@code docs/superpowers/specs/2026-09-07-file-upload-policy.md}
 *
 * <h2>여기서 보지 않는 것</h2>
 *
 * <p>개수 상한과 요청 전체 크기는 <b>요청 단위</b>라 업로드 엔드포인트가 본다
 * ({@code EvidenceUploadService}). 이 클래스는 파일 하나만 안다.
 */
public final class FileContentPolicy {

    /** 정책 문서의 화이트리스트. 확장자 → 허용 미디어 타입. */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("pdf");

    private static final Set<String> IMAGE_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private FileContentPolicy() {}

    /**
     * 파일 하나를 받아도 되는지 확인하고 <b>확장자를 돌려준다.</b>
     *
     * <p>확장자를 반환값으로 두는 이유는 저장 키를 만들 때 필요하고, 그 값을 다시
     * 파싱하면 판정과 사용이 갈라질 수 있기 때문이다.
     *
     * @return 소문자 확장자 (점 없음)
     */
    public static String requireAcceptable(String originalFilename, String contentType, long size) {
        if (size <= 0) {
            throw new BusinessException(FileStorageErrorCode.FILE_EMPTY);
        }
        String extension = requireAllowedExtension(originalFilename);
        requireContentTypeMatches(extension, contentType);
        return extension;
    }

    /**
     * 저장 키의 확장자로 <b>응답에 쓸 미디어 타입</b>을 정한다.
     *
     * <p>저장소가 들고 있는 메타데이터(S3 객체의 {@code Content-Type})를 쓰지 않는다.
     * 이유가 둘이다.
     *
     * <ol>
     *   <li>로컬 구현에는 그런 메타데이터가 <b>없다.</b> 저장소를 바꾸면 같은 파일이 다른
     *       타입으로 나가고, 브라우저 동작이 달라진다
     *   <li>메타데이터는 업로드 시점에 클라이언트가 선언한 값이 흘러 들어간 것이다.
     *       확장자는 {@link #requireAcceptable} 를 통과한 값이라 <b>화이트리스트 안에 있는
     *       것이 보장된다</b> — 파생하는 편이 좁다
     * </ol>
     *
     * @param storageKey {@code store} 가 만든 키. 확장자만 본다
     */
    public static String mediaTypeOf(String storageKey) {
        String extension = requireAllowedExtension(storageKey);
        return DOCUMENT_EXTENSIONS.contains(extension) ? PDF_CONTENT_TYPE : imageType(extension);
    }

    private static String imageType(String extension) {
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            // requireAllowedExtension 을 통과한 값만 오므로 닿지 않는다. 화이트리스트가
            // 늘었는데 여기를 안 고치면 이 예외가 그 사실을 알려 준다.
            default -> throw new BusinessException(FileStorageErrorCode.FILE_TYPE_NOT_ALLOWED);
        };
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
}
