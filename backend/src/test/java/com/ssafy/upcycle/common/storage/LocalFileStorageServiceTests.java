package com.ssafy.upcycle.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.common.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 정책 문서({@code 2026-09-07-file-upload-policy.md})의 Verification 절이 약속한 것을 고정한다.
 *
 * <p>가장 중요한 것은 <b>경로에 클라이언트 입력이 들어가지 않는다</b>는 것이다.
 * 파생 경로를 쓰는 이유가 디렉터리 탈출을 "막는" 게 아니라 "불가능하게" 만드는 것이므로,
 * 그 성질이 실제로 성립하는지 확인해야 의미가 있다.
 */
class LocalFileStorageServiceTests {

    private static final byte[] CONTENT = "photo-bytes".getBytes(StandardCharsets.UTF_8);
    private static final FileReference REFERENCE = new FileReference("MATERIAL", 1_024L);

    @TempDir
    Path root;

    private LocalFileStorageService storage() {
        return new LocalFileStorageService(
                FileStorageProperties.local(root.toString()), Clock.systemDefaultZone());
    }

    private StoredFile store(String filename, String contentType) {
        return storage().store(REFERENCE, filename, contentType, stream(), CONTENT.length);
    }

    private static InputStream stream() {
        return new ByteArrayInputStream(CONTENT);
    }

    // ── 경로 파생 ────────────────────────────────────────────

    @Test
    void derivesThePathFromTheReferenceAndNotTheFilename() {
        StoredFile stored = store("photo.jpg", "image/jpeg");

        assertThat(stored.storageKey())
                .startsWith("MATERIAL/1024/")
                .matches("MATERIAL/1024/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.jpg");
        assertThat(stored.storageKey())
                .as("원본 이름이 경로에 들어가면 안 된다")
                .doesNotContain("photo");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "../../etc/passwd.jpg",
        "/absolute/path.jpg",
        "..\\..\\windows.jpg",
        "a/b/c.jpg",
    })
    void cannotEscapeTheRootWithAHostileFilename(String hostile) {
        // 이름을 확장자 판정에만 쓰므로 탈출 시도가 경로에 반영되지 않는다.
        // 검사로 막는 것이 아니라 구조적으로 불가능하다.
        StoredFile stored = store(hostile, "image/jpeg");

        Path written = root.resolve(stored.storageKey()).normalize();
        assertThat(written).startsWith(root);
        assertThat(stored.storageKey()).doesNotContain("..");
    }

    @Test
    void givesTwoObjectsToTheSameFilenameUploadedTwice() {
        // 원본 이름은 충돌한다 (두 휴대폰의 IMG_0001.jpg). 덮어쓰면 증빙이 조용히 사라지고,
        // 그 해시는 이미 온체인에 올라가 있을 수 있다.
        StoredFile first = store("IMG_0001.jpg", "image/jpeg");
        StoredFile second = store("IMG_0001.jpg", "image/jpeg");

        assertThat(first.storageKey()).isNotEqualTo(second.storageKey());
        assertThat(storage().exists(first.storageKey())).isTrue();
        assertThat(storage().exists(second.storageKey())).isTrue();
    }

    @Test
    void keepsTheOriginalFilenameAsMetadata() {
        StoredFile stored = store("계근표 2026-09.pdf", "application/pdf");

        assertThat(stored.originalFilename()).isEqualTo("계근표 2026-09.pdf");
    }

    // ── 해시 ────────────────────────────────────────────────

    @Test
    void hashesTheBytesThatWereActuallyWritten() throws Exception {
        StoredFile stored = store("photo.jpg", "image/jpeg");

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String expected = "0x" + HexFormat.of().formatHex(digest.digest(CONTENT));

        assertThat(stored.hash()).isEqualTo(expected);
        assertThat(stored.hash())
                .as("evidence_file.file_hash CHAR(66) · 온체인 비교 형식")
                .hasSize(66);
    }

    @Test
    void reportsTheWrittenSizeNotTheDeclaredOne() {
        // 선언값을 그대로 돌려주면 거짓말할 수 있다. 실제로 흘러간 바이트를 센다.
        StoredFile stored =
                storage().store(REFERENCE, "photo.jpg", "image/jpeg", stream(), 999_999L);

        assertThat(stored.size()).isEqualTo(CONTENT.length);
    }

    @Test
    void writesTheContentUnchanged() throws Exception {
        StoredFile stored = store("photo.jpg", "image/jpeg");

        assertThat(Files.readAllBytes(root.resolve(stored.storageKey()))).isEqualTo(CONTENT);
    }

    // ── 형식 검증 ────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "photo.jpg,image/jpeg",
        "photo.jpeg,image/jpeg",
        "photo.png,image/png",
        "photo.webp,image/webp",
        "slip.pdf,application/pdf",
    })
    void acceptsTheWhitelistedTypes(String filename, String contentType) {
        assertThat(store(filename, contentType).storageKey()).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {"logo.svg", "script.js", "archive.zip", "photo.heic", "noextension"})
    void rejectsTypesOutsideTheWhitelist(String filename) {
        // svg 가 특히 중요하다 — 사용자에게는 이미지, 브라우저에는 스크립트 컨테이너다.
        assertThatThrownBy(() -> store(filename, "image/jpeg"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_TYPE_NOT_ALLOWED);
    }

    @ParameterizedTest
    @CsvSource({
        "photo.jpg,image/png",
        "photo.png,image/jpeg",
        "slip.pdf,image/jpeg",
        "photo.jpg,application/pdf",
    })
    void rejectsWhenExtensionAndContentTypeDisagree(String filename, String contentType) {
        // 확장자만 보면 photo.jpg 라는 이름의 무엇이든 통과한다.
        // 코드가 NOT_ALLOWED 와 달라야 한다 — 사용자에게 다른 안내가 필요하다.
        assertThatThrownBy(() -> store(filename, contentType))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_TYPE_MISMATCH);
    }

    @Test
    void rejectsAnEmptyFile() {
        assertThatThrownBy(() ->
                        storage().store(REFERENCE, "photo.jpg", "image/jpeg", stream(), 0L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_EMPTY);
    }

    // ── 실패 시 잔여물 ───────────────────────────────────────

    @Test
    void leavesNothingBehindWhenTheStreamFails() throws Exception {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("중간에 끊김");
            }
        };

        assertThatThrownBy(() ->
                        storage().store(REFERENCE, "photo.jpg", "image/jpeg", broken, 100L))
                .isInstanceOf(BusinessException.class);

        // 깨진 파일이 정상 경로에 남거나 .part 임시 파일이 남으면 안 된다.
        try (Stream<Path> walk = Files.walk(root)) {
            assertThat(walk.filter(Files::isRegularFile))
                    .as("실패한 업로드는 흔적을 남기지 않는다")
                    .isEmpty();
        }
    }

    @Test
    void rejectsAFileReferenceThatCouldBecomeAPathSegment() {
        // 경로 조각이 되는 값이라 여기서 막는다. 막으면 경로 조립부는 검사를 다시 하지 않는다.
        assertThatThrownBy(() -> new FileReference("../MATERIAL", 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FileReference("material", 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FileReference("MATERIAL", 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 읽기 (S15P21E104-162) ───────────────────────────────

    @Test
    void readsBackExactlyWhatWasWritten() throws Exception {
        StoredFile stored = store("photo.jpg", "image/jpeg");

        FileContent content = storage().read(stored.storageKey());
        try (InputStream in = content.stream()) {
            assertThat(in.readAllBytes()).isEqualTo(CONTENT);
        }
        assertThat(content.size()).isEqualTo(CONTENT.length);
    }

    @ParameterizedTest
    @CsvSource({
        "photo.jpg,image/jpeg",
        "photo.png,image/png",
        "photo.webp,image/webp",
        "slip.pdf,application/pdf",
    })
    void derivesTheMediaTypeFromTheKey(String filename, String contentType) throws Exception {
        // 로컬에는 저장해 둔 메타데이터가 없고 probeContentType 은 OS 설정에 따라 답이
        // 달라진다. 확장자에서 파생하면 서버·저장소가 달라도 같은 답이 나온다.
        StoredFile stored = store(filename, contentType);

        FileContent content = storage().read(stored.storageKey());
        try (InputStream in = content.stream()) {
            assertThat(in).isNotNull();
        }
        assertThat(content.contentType()).isEqualTo(contentType);
    }

    @Test
    void readingAMissingFileIsNotFoundRatherThanAServerError() {
        // 500 으로 내보내면 프론트가 재시도 루프를 돌고 우리는 장애 알림을 받는다 —
        // 원인은 데이터 쪽인데.
        assertThatThrownBy(() -> storage().read("MATERIAL/1024/2026/09/does-not-exist.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_NOT_FOUND);
    }

    @Test
    void refusesToReadAKeyThatEscapesTheRoot() {
        // 읽기는 키를 DB 에서 받는다. 그 값이 오염되면 이 메서드가 서버의 임의 파일을
        // 응답으로 내보내는 문이 된다 — 파생 경로를 쓰는 노력이 여기서 무의미해진다.
        assertThatThrownBy(() -> storage().read("../../etc/passwd"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_STORAGE_FAILED);
    }

    // ── 삭제·존재 ────────────────────────────────────────────

    @Test
    void deletesAStoredFile() {
        StoredFile stored = store("photo.jpg", "image/jpeg");

        assertThat(storage().delete(stored.storageKey())).isTrue();
        assertThat(storage().exists(stored.storageKey())).isFalse();
    }

    @Test
    void deletingAMissingFileIsNotAnError() {
        // 호출부는 트랜잭션 롤백 경로에서 이걸 부른다. 여기서 던지면 원래 실패 원인을 덮는다.
        assertThat(storage().delete("MATERIAL/1024/2026/09/does-not-exist.jpg")).isFalse();
    }

    @Test
    void refusesToResolveAKeyThatEscapesTheRoot() {
        // DB 에 저장된 키가 오염됐을 때. 되찾는 쪽에 구멍이 있으면 파생 경로의 노력이 무의미하다.
        assertThatThrownBy(() -> storage().exists("../../etc/passwd"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_STORAGE_FAILED);
    }
}
