package com.ssafy.upcycle.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.common.exception.BusinessException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link FileContentPolicy} 를 직접 고정한다.
 *
 * <h2>왜 저장 구현 테스트로는 부족한가</h2>
 *
 * <p>이 클래스는 {@code LocalFileStorageService} 에서 <b>꺼내 온 것</b>이고, 지금은 로컬과
 * S3 두 구현이 함께 호출한다. 구현별 테스트({@code LocalFileStorageServiceTests} ·
 * {@code S3FileStorageServiceTests})는 "그 구현이 정책을 부른다" 까지만 보여 주고,
 * 정책 자체의 경계값은 어느 쪽에서도 전부 나오지 않는다. 구현을 바꾸는 것은 설정 한 줄이라
 * (`app.storage.type`) 정책이 한쪽에만 매달려 있으면 그 차이가 배포 시점까지 가려진다.
 * 그래서 정책은 정책만 보는 테스트로 못박는다.
 *
 * <p>정책 문서: {@code docs/superpowers/specs/2026-09-07-file-upload-policy.md}
 *
 * <h2>여기서 지키려는 것 중 가장 중요한 것</h2>
 *
 * <p><b>확장자와 선언 미디어 타입이 서로를 지지해야 한다.</b> 확장자만 보면
 * {@code photo.jpg} 라는 이름의 무엇이든 통과하므로, 어긋나는 조합은 실제 공격 표면이다.
 * 그리고 그때 나가는 코드가 {@code FILE_TYPE_NOT_ALLOWED} 와 <b>달라야</b> 한다 —
 * {@code .heic} 를 {@code .jpg} 로 바꿔 올린 사용자에게는 "받지 않는 종류" 가 아니라
 * "이름이 내용과 다름" 을 알려야 한다.
 *
 * <h2>여기서 보지 않는 것 — 크기 상한</h2>
 *
 * <p>{@code requireAcceptable} 의 {@code size} 인자는 <b>빈 파일 판정에만</b> 쓰인다.
 * 파일당 10MB 상한은 요청 단위 규칙과 함께 {@code EvidenceUploadService} 가 보고
 * (`MAX_FILE_SIZE_BYTES`), 앞 층에서 Spring 의 {@code max-file-size} 가 먼저 걸리면
 * {@code GlobalExceptionHandler} 가 {@code FILE_TOO_LARGE} 로 번역한다.
 * 이 분담이 의도된 것임을 {@link #doesNotApplyAnUpperSizeLimitItself()} 가 명시한다 —
 * 그러지 않으면 나중에 누군가 "정책이 10MB 를 본다" 고 착각하고 호출부 검사를 지운다.
 */
class FileContentPolicyTests {

    private static final long ONE_BYTE = 1L;

    // ── 허용되는 조합 ────────────────────────────────────────

    /**
     * 정책 문서 화이트리스트 표 그대로. 반환값이 <b>점 없는 소문자 확장자</b>라는 계약도
     * 같이 고정한다 — 저장 키를 만들 때 쓰는 값이라 이걸 다시 파싱하면 판정과 사용이 갈라진다.
     */
    @ParameterizedTest
    @CsvSource({
        "photo.jpg,image/jpeg,jpg",
        "photo.jpeg,image/jpeg,jpeg",
        "photo.png,image/png,png",
        "photo.webp,image/webp,webp",
        "slip.pdf,application/pdf,pdf",
    })
    void acceptsTheWhitelistedCombinationsAndReturnsTheExtension(
            String filename, String contentType, String expectedExtension) {
        assertThat(FileContentPolicy.requireAcceptable(filename, contentType, ONE_BYTE))
                .isEqualTo(expectedExtension);
    }

    /** {@code jpg} 와 {@code jpeg} 는 같은 미디어 타입이지만 확장자는 원본을 따른다. */
    @Test
    void keepsJpgAndJpegDistinctInTheReturnedExtension() {
        assertThat(FileContentPolicy.requireAcceptable("a.jpg", "image/jpeg", ONE_BYTE))
                .isEqualTo("jpg");
        assertThat(FileContentPolicy.requireAcceptable("a.jpeg", "image/jpeg", ONE_BYTE))
                .isEqualTo("jpeg");
    }

    /** 휴대폰·카메라가 대문자 이름을 만든다({@code IMG_0001.JPG}). 정규화해 받는다. */
    @ParameterizedTest
    @CsvSource({
        "IMG_0001.JPG,image/jpeg,jpg",
        "SCAN.PDF,application/pdf,pdf",
        "photo.PnG,image/png,png",
    })
    void normalisesTheExtensionToLowerCase(
            String filename, String contentType, String expectedExtension) {
        assertThat(FileContentPolicy.requireAcceptable(filename, contentType, ONE_BYTE))
                .isEqualTo(expectedExtension);
    }

    /** 선언 타입도 대소문자·주변 공백을 정규화한다. HTTP 미디어 타입은 대소문자를 가리지 않는다. */
    @ParameterizedTest
    @ValueSource(strings = {"IMAGE/JPEG", "Image/Jpeg", " image/jpeg", "image/jpeg  "})
    void normalisesTheDeclaredContentType(String contentType) {
        assertThat(FileContentPolicy.requireAcceptable("photo.jpg", contentType, ONE_BYTE))
                .isEqualTo("jpg");
    }

    /**
     * 마지막 점만 본다. 저장 이름이 UUID + 검증된 확장자라 앞쪽 조각은 저장 키에 닿지 않으므로
     * {@code report.2026.pdf} 같은 정상 이름을 거부할 이유가 없다.
     */
    @ParameterizedTest
    @CsvSource({
        "report.2026.09.pdf,application/pdf,pdf",
        "photo.exe.jpg,image/jpeg,jpg",
        "계근표 2026-09.pdf,application/pdf,pdf",
    })
    void looksOnlyAtTheLastExtension(
            String filename, String contentType, String expectedExtension) {
        assertThat(FileContentPolicy.requireAcceptable(filename, contentType, ONE_BYTE))
                .isEqualTo(expectedExtension);
    }

    /**
     * 반대 방향의 이중 확장자는 막힌다 — {@code .jpg.exe} 는 실행 파일이고 마지막 조각이 판정 대상이다.
     * 위 {@link #looksOnlyAtTheLastExtension} 와 짝으로 읽어야 규칙이 분명해진다.
     */
    @Test
    void rejectsADoubleExtensionEndingOutsideTheWhitelist() {
        assertThatThrownBy(
                        () ->
                                FileContentPolicy.requireAcceptable(
                                        "photo.jpg.exe", "image/jpeg", ONE_BYTE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_TYPE_NOT_ALLOWED);
    }

    // ── 확장자 화이트리스트 ──────────────────────────────────

    /**
     * {@code svg} 가 목록에 있는 것이 특히 중요하다. 사용자에게는 이미지, 브라우저에는
     * 스크립트 컨테이너라 우리 오리진에서 서빙하면 저장형 XSS 가 된다.
     * 블랙리스트가 아니라 화이트리스트라는 성질도 여기서 확인된다 — 처음 보는 확장자는 전부 거부다.
     */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "logo.svg",
                "script.js",
                "page.html",
                "archive.zip",
                "photo.heic",
                "photo.gif",
                "photo.bmp",
                "photo.tiff",
                "doc.docx",
                "sheet.xlsx",
                "app.exe",
                "shell.sh",
                "photo.jpg2",
                "photo.jpgx",
            })
    void rejectsExtensionsOutsideTheWhitelist(String filename) {
        assertThatThrownBy(
                        () ->
                                FileContentPolicy.requireAcceptable(
                                        filename, "image/jpeg", ONE_BYTE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_TYPE_NOT_ALLOWED);
    }

    /**
     * 확장자를 뽑을 수 없는 이름. 점이 없거나, 점으로 끝나거나, 점 하나뿐인 이름이다.
     * 이 경우 {@code FILE_TYPE_MISMATCH} 가 아니라 {@code FILE_TYPE_NOT_ALLOWED} 가 나가야 한다 —
     * 형식을 알 수 없는 것과 이름이 내용과 다른 것은 다른 상황이다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"noextension", "IMG_0001", ".", "..", "photo.", "photo..", "photo.jpg."})
    void rejectsFilenamesWithNoUsableExtension(String filename) {
        assertThatThrownBy(
                        () ->
                                FileContentPolicy.requireAcceptable(
                                        filename, "image/jpeg", ONE_BYTE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_TYPE_NOT_ALLOWED);
    }

    /**
     * 이름 없이 확장자만 있는 경우({@code .jpg})는 <b>통과한다.</b> 위험하지 않아서 통과시키는 게
     * 아니라 <b>위험할 수 없어서</b>다 — 저장 이름은 UUID 이고 원본 이름은 메타데이터 컬럼에만 간다.
     * 이름을 거부 사유로 늘리면 정책이 파일명 문법 검사기가 되고, 그건 판정 지점이 아니다.
     */
    @Test
    void acceptsANameThatIsOnlyAnExtension() {
        assertThat(FileContentPolicy.requireAcceptable(".jpg", "image/jpeg", ONE_BYTE))
                .isEqualTo("jpg");
    }

    /**
     * 이름이 없으면 형식을 알 수 없다. {@code MultipartFile#getOriginalFilename} 은
     * {@code null} 을 돌려줄 수 있어 실제로 도달하는 입력이다.
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsAMissingFilename(String filename) {
        assertThatThrownBy(
                        () ->
                                FileContentPolicy.requireAcceptable(
                                        filename, "image/jpeg", ONE_BYTE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_TYPE_NOT_ALLOWED);
    }

    // ── 확장자 ↔ 선언 타입 불일치 (실제 공격 표면) ────────────

    /**
     * 여기가 이 클래스의 핵심이다. 화이트리스트 안의 값끼리도 서로 어긋나면 거부해야 한다.
     * {@code .heic} 를 {@code .jpg} 로 바꿔 올린 사용자와, 이미지인 척하는 pdf 가 같은 경로다.
     */
    @ParameterizedTest
    @MethodSource("mismatchedPairs")
    void rejectsWhenTheExtensionAndTheDeclaredTypeDisagree(String filename, String contentType) {
        assertThatThrownBy(
                        () -> FileContentPolicy.requireAcceptable(filename, contentType, ONE_BYTE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_TYPE_MISMATCH);
    }

    static Stream<Arguments> mismatchedPairs() {
        return Stream.of(
                // 화이트리스트 안의 이미지끼리 어긋난 경우
                Arguments.of("photo.jpg", "image/png"),
                Arguments.of("photo.jpeg", "image/webp"),
                Arguments.of("photo.png", "image/jpeg"),
                Arguments.of("photo.webp", "image/png"),
                // 이미지 이름 + 문서 타입, 문서 이름 + 이미지 타입
                Arguments.of("photo.jpg", "application/pdf"),
                Arguments.of("slip.pdf", "image/jpeg"),
                // 이름은 화이트리스트, 타입은 화이트리스트 밖
                Arguments.of("photo.jpg", "image/svg+xml"),
                Arguments.of("photo.jpg", "application/octet-stream"),
                Arguments.of("photo.jpg", "text/html"),
                Arguments.of("slip.pdf", "application/x-pdf"),
                // image/jpg 는 흔하지만 표준이 아니다. 받아 주면 화이트리스트가 두 개가 된다
                Arguments.of("photo.jpg", "image/jpg"),
                // 파라미터가 붙은 형태는 정규화 대상이 아니다 — 아래 별도 테스트로 명시한다
                Arguments.of("photo.jpg", "image/jpeg; charset=utf-8"));
    }

    /**
     * 선언 타입이 없어도 {@code FILE_TYPE_MISMATCH} 다. 통과시키면 확장자 단독 판정이 되어
     * 두 값이 서로를 지지한다는 전제가 무너진다 — {@code photo.jpg} 라는 이름의 무엇이든 들어온다.
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "   "})
    void rejectsAMissingContentType(String contentType) {
        assertThatThrownBy(
                        () ->
                                FileContentPolicy.requireAcceptable(
                                        "photo.jpg", contentType, ONE_BYTE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_TYPE_MISMATCH);
    }

    /**
     * 파라미터가 붙은 미디어 타입({@code image/jpeg; charset=utf-8})은 현재 거부된다.
     * 비교가 문자열 동등이라 그렇다. 브라우저가 이미지 파트에 파라미터를 붙이지 않으므로
     * 실무에서 걸리지 않지만, <b>이게 의도가 아니라 구현의 결과</b>라는 점을 남겨 둔다 —
     * 나중에 파라미터를 잘라 내기로 하면 이 테스트가 바뀌어야 하고, 그때 결정이 드러난다.
     */
    @Test
    void doesNotStripMediaTypeParameters() {
        assertThatThrownBy(
                        () ->
                                FileContentPolicy.requireAcceptable(
                                        "slip.pdf", "application/pdf; version=1.7", ONE_BYTE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_TYPE_MISMATCH);
    }

    // ── 크기 ────────────────────────────────────────────────

    /** 빈 파일은 올릴 수 없다. 음수는 도달하지 않아야 하지만 같은 경로로 떨어져야 한다. */
    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void rejectsAnEmptyOrNegativeSize(long size) {
        assertThatThrownBy(
                        () ->
                                FileContentPolicy.requireAcceptable(
                                        "photo.jpg", "image/jpeg", size))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_EMPTY);
    }

    /**
     * 크기 판정이 형식 판정보다 <b>먼저</b>다. 순서가 뒤집히면 0바이트 {@code .svg} 업로드에
     * 형식 오류가 먼저 나가고, 사용자는 파일을 고쳐 다시 올린 뒤에야 빈 파일임을 알게 된다.
     */
    @Test
    void checksTheSizeBeforeTheFileType() {
        assertThatThrownBy(() -> FileContentPolicy.requireAcceptable("logo.svg", null, 0L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_EMPTY);
    }

    /**
     * <b>정책은 상한을 보지 않는다.</b> 10MB 는 요청 단위 규칙과 함께
     * {@code EvidenceUploadService.MAX_FILE_SIZE_BYTES} 가 보고, 그보다 앞에서
     * Spring 의 {@code max-file-size: 10MB} 가 걸린다.
     *
     * <p>여기서 통과하는 것이 구멍처럼 보일 수 있어 명시적으로 못박는다. 이 테스트가 없으면
     * 누군가 "정책이 크기를 받으니 상한도 본다" 고 읽고 호출부 검사를 지울 수 있다.
     */
    @Test
    void doesNotApplyAnUpperSizeLimitItself() {
        long overTenMegabytes = 10L * 1024 * 1024 + 1;

        assertThatCode(
                        () ->
                                FileContentPolicy.requireAcceptable(
                                        "photo.jpg", "image/jpeg", overTenMegabytes))
                .doesNotThrowAnyException();
        assertThat(FileContentPolicy.requireAcceptable("photo.jpg", "image/jpeg", Long.MAX_VALUE))
                .isEqualTo("jpg");
    }
}
