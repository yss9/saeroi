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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * MinIO/S3 구현. S15P21E104-162 · S15P21E104-248.
 *
 * <p><b>가짜 {@code S3Client} 로 돈다.</b> {@code S3Client} 가 인터페이스라 네트워크 없이
 * 요청 내용을 그대로 들여다볼 수 있다 — 여기서 확인해야 할 것은 "무엇을 보냈는가" 이고,
 * 실제 MinIO 왕복은 그 위의 문제다.
 *
 * <p>가장 중요한 단언은 <b>해시가 실제로 올라간 바이트를 가리키는가</b>다. 그 값이
 * 온체인에 기록되므로 어긋나면 되돌릴 수 없다. 그리고 <b>저장 키가 로컬 구현과 같은가</b> —
 * 다르면 저장소를 바꿀 때 기존 {@code evidence_file.file_url} 을 찾을 수 없다.
 */
class S3FileStorageServiceTests {

    private static final byte[] CONTENT = "photo-bytes".getBytes(StandardCharsets.UTF_8);
    private static final FileReference REFERENCE = new FileReference("MATERIAL", 1_024L);
    private static final String BUCKET = "evidence";

    private final FakeS3Client s3 = new FakeS3Client();

    private S3FileStorageService storage() {
        return new S3FileStorageService(s3, properties(), Clock.systemDefaultZone());
    }

    private static FileStorageProperties properties() {
        return FileStorageProperties.s3(BUCKET, "http://minio:9000", "key", "secret");
    }

    private StoredFile store(String filename, String contentType) {
        return storage().store(REFERENCE, filename, contentType, stream(), CONTENT.length);
    }

    private static InputStream stream() {
        return new ByteArrayInputStream(CONTENT);
    }

    // ── 저장 키는 로컬 구현과 같아야 한다 ─────────────────────

    @Test
    void derivesTheSameKeyShapeAsTheLocalImplementation() {
        StoredFile stored = store("photo.jpg", "image/jpeg");

        assertThat(stored.storageKey())
                .matches("MATERIAL/1024/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.jpg");
        assertThat(stored.storageKey())
                .as("원본 이름이 키에 들어가면 안 된다")
                .doesNotContain("photo");
    }

    @Test
    void sendsToTheConfiguredBucketWithTheDerivedKey() {
        StoredFile stored = store("photo.jpg", "image/jpeg");

        assertThat(s3.puts).singleElement().satisfies(put -> {
            assertThat(put.request.bucket()).isEqualTo(BUCKET);
            assertThat(put.request.key()).isEqualTo(stored.storageKey());
            assertThat(put.request.contentType()).isEqualTo("image/jpeg");
            assertThat(put.request.contentLength()).isEqualTo(CONTENT.length);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"../../etc/passwd.jpg", "/absolute/path.jpg", "a/b/c.jpg"})
    void aHostileFilenameNeverReachesTheKey(String hostile) {
        StoredFile stored = store(hostile, "image/jpeg");

        assertThat(stored.storageKey()).doesNotContain("..").startsWith("MATERIAL/1024/");
    }

    @Test
    void givesTwoObjectsToTheSameFilenameUploadedTwice() {
        StoredFile first = store("IMG_0001.jpg", "image/jpeg");
        StoredFile second = store("IMG_0001.jpg", "image/jpeg");

        assertThat(first.storageKey()).isNotEqualTo(second.storageKey());
        assertThat(s3.objects).hasSize(2);
    }

    // ── 해시가 올라간 바이트를 가리켜야 한다 ─────────────────

    @Test
    void hashesTheBytesThatWereActuallyUploaded() throws Exception {
        StoredFile stored = store("photo.jpg", "image/jpeg");

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String expected = "0x" + HexFormat.of().formatHex(digest.digest(CONTENT));

        assertThat(stored.hash()).isEqualTo(expected);
        assertThat(stored.hash())
                .as("evidence_file.file_hash CHAR(66) · 온체인 비교 형식")
                .hasSize(FileHash.LENGTH);
        assertThat(s3.objects.get(stored.storageKey()))
                .as("해시와 실제 올라간 바이트가 같은 것을 가리켜야 한다")
                .isEqualTo(CONTENT);
    }

    @Test
    void reportsTheWrittenSizeNotTheDeclaredOne() {
        // 선언값을 그대로 돌려주면 거짓말할 수 있다. 실제로 흘러간 바이트를 센다.
        StoredFile stored =
                storage().store(REFERENCE, "photo.jpg", "image/jpeg", stream(), 999_999L);

        assertThat(stored.size()).isEqualTo(CONTENT.length);
        assertThat(s3.puts.get(0).request.contentLength()).isEqualTo(CONTENT.length);
    }

    @Test
    void uploadsTheContentUnchanged() {
        StoredFile stored = store("slip.pdf", "application/pdf");

        assertThat(s3.objects.get(stored.storageKey())).isEqualTo(CONTENT);
    }

    // ── 형식 규칙은 로컬 구현과 공유한다 ─────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"logo.svg", "script.js", "archive.zip", "noextension"})
    void rejectsTypesOutsideTheWhitelist(String filename) {
        // svg 가 특히 중요하다. 구현을 바꾸는 것은 설정 한 줄이라, 한쪽에서만 통과하면
        // 그 차이가 배포 시점까지 드러나지 않는다.
        assertThatThrownBy(() -> store(filename, "image/jpeg"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_TYPE_NOT_ALLOWED);

        assertThat(s3.puts).as("거절된 파일은 올라가지 않는다").isEmpty();
    }

    @Test
    void rejectsWhenExtensionAndContentTypeDisagree() {
        assertThatThrownBy(() -> store("photo.jpg", "application/pdf"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_TYPE_MISMATCH);
    }

    @Test
    void rejectsAnEmptyFile() {
        assertThatThrownBy(
                        () -> storage().store(REFERENCE, "photo.jpg", "image/jpeg", stream(), 0L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_EMPTY);
    }

    // ── 실패 시 잔여물 ───────────────────────────────────────

    @Test
    void leavesNoTemporaryFileWhenTheStreamFails() throws Exception {
        long before = temporaryPartFiles();
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("중간에 끊김");
            }
        };

        assertThatThrownBy(
                        () -> storage().store(REFERENCE, "photo.jpg", "image/jpeg", broken, 100L))
                .isInstanceOf(BusinessException.class);

        assertThat(s3.puts).isEmpty();
        assertThat(temporaryPartFiles()).isEqualTo(before);
    }

    @Test
    void leavesNoTemporaryFileWhenS3Fails() throws Exception {
        long before = temporaryPartFiles();
        s3.failPut = true;

        assertThatThrownBy(() -> store("photo.jpg", "image/jpeg"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_STORAGE_FAILED);

        assertThat(temporaryPartFiles()).isEqualTo(before);
    }

    /** 임시 파일은 시스템 temp 에 만들어진다. 우리 접두사만 센다. */
    private long temporaryPartFiles() throws IOException {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        try (Stream<Path> list = Files.list(tmp)) {
            return list.filter(path -> path.getFileName().toString().startsWith(".upload-"))
                    .count();
        }
    }

    // ── 읽기 (S15P21E104-162) ───────────────────────────────

    @Test
    void readsBackExactlyWhatWasUploaded() throws Exception {
        StoredFile stored = store("photo.jpg", "image/jpeg");

        FileContent content = storage().read(stored.storageKey());
        try (InputStream in = content.stream()) {
            assertThat(in.readAllBytes()).isEqualTo(CONTENT);
        }
        assertThat(content.size()).isEqualTo(CONTENT.length);
        assertThat(s3.gets).containsExactly(stored.storageKey());
    }

    @Test
    void derivesTheMediaTypeFromTheKeyLikeTheLocalImplementation() throws Exception {
        // 객체 메타데이터를 쓰면 로컬 구현과 답이 갈린다. 저장소 전환은 설정 한 줄이라
        // 그 차이가 배포 시점까지 드러나지 않는다.
        StoredFile stored = store("slip.pdf", "application/pdf");

        FileContent content = storage().read(stored.storageKey());
        try (InputStream in = content.stream()) {
            assertThat(in).isNotNull();
        }
        assertThat(content.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void readingAMissingObjectIsNotFoundRatherThanAServerError() {
        assertThatThrownBy(() -> storage().read("MATERIAL/1024/2026/09/does-not-exist.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_NOT_FOUND);
    }

    @Test
    void readFailsLoudlyWhenS3IsUnreachable() {
        // 연결 실패를 "없음"(404) 으로 답하면 화면에 "증빙 없음" 이라는 거짓이 나간다.
        StoredFile stored = store("photo.jpg", "image/jpeg");
        s3.failGet = true;

        assertThatThrownBy(() -> storage().read(stored.storageKey()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_STORAGE_FAILED);
    }

    // ── 삭제 · 존재 ──────────────────────────────────────────

    @Test
    void deletesAStoredObject() {
        StoredFile stored = store("photo.jpg", "image/jpeg");

        assertThat(storage().delete(stored.storageKey())).isTrue();
        assertThat(storage().exists(stored.storageKey())).isFalse();
    }

    @Test
    void deletingAMissingObjectReportsFalseInsteadOfThrowing() {
        // S3 DeleteObject 는 없는 키에도 성공한다. 그래서 확인 없이는 true 를 돌려주게 되고,
        // 호출부(롤백 경로)의 로그가 "지웠다" 고 거짓을 말한다.
        assertThat(storage().delete("MATERIAL/1024/2026/09/does-not-exist.jpg")).isFalse();
    }

    @Test
    void deleteDoesNotThrowWhenS3Fails() {
        // 호출부는 트랜잭션 롤백 경로에서 이걸 부른다. 여기서 던지면 원래 실패 원인을 덮는다.
        StoredFile stored = store("photo.jpg", "image/jpeg");
        s3.failDelete = true;

        assertThat(storage().delete(stored.storageKey())).isFalse();
    }

    @Test
    void existsThrowsWhenS3IsUnreachable() {
        // 연결 실패를 "없음" 으로 답하면 호출부가 파일이 사라졌다고 판단한다.
        // 증빙 조회에서 그건 "증빙 없음" 이라는 거짓이 화면에 나가는 것이다.
        s3.failHead = true;

        assertThatThrownBy(() -> storage().exists("MATERIAL/1024/2026/09/whatever.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_STORAGE_FAILED);
    }

    /**
     * 가짜 S3. 올라간 바이트를 그대로 들고 있어 해시 대조에 쓴다.
     *
     * <p>{@code S3Client} 의 나머지 수백 개 메서드는 인터페이스 기본 구현이 있어
     * 구현할 필요가 없다 — 이 테스트가 쓰는 셋만 덮는다.
     */
    private static class FakeS3Client implements software.amazon.awssdk.services.s3.S3Client {

        record Put(PutObjectRequest request, byte[] body) {}

        final List<Put> puts = new ArrayList<>();
        final List<String> gets = new ArrayList<>();
        final Map<String, byte[]> objects = new HashMap<>();
        boolean failPut;
        boolean failDelete;
        boolean failHead;
        boolean failGet;

        @Override
        public ResponseInputStream<GetObjectResponse> getObject(GetObjectRequest request) {
            if (failGet) {
                throw error("InternalError");
            }
            byte[] found = objects.get(request.key());
            if (found == null) {
                throw NoSuchKeyException.builder().message("no such key").build();
            }
            gets.add(request.key());
            return new ResponseInputStream<>(
                    GetObjectResponse.builder().contentLength((long) found.length).build(),
                    AbortableInputStream.create(new ByteArrayInputStream(found)));
        }

        @Override
        public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
            if (failPut) {
                throw error("InternalError");
            }
            byte[] bytes;
            try (InputStream in = body.contentStreamProvider().newStream()) {
                bytes = in.readAllBytes();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            puts.add(new Put(request, bytes));
            objects.put(request.key(), bytes);
            return PutObjectResponse.builder().build();
        }

        @Override
        public DeleteObjectResponse deleteObject(DeleteObjectRequest request) {
            if (failDelete) {
                throw error("AccessDenied");
            }
            objects.remove(request.key());
            return DeleteObjectResponse.builder().build();
        }

        @Override
        public HeadObjectResponse headObject(HeadObjectRequest request) {
            if (failHead) {
                throw error("InternalError");
            }
            byte[] found = objects.get(request.key());
            if (found == null) {
                throw NoSuchKeyException.builder().message("no such key").build();
            }
            return HeadObjectResponse.builder().contentLength((long) found.length).build();
        }

        private static S3Exception error(String code) {
            return (S3Exception) S3Exception.builder()
                    .awsErrorDetails(AwsErrorDetails.builder().errorCode(code).build())
                    .message(code)
                    .build();
        }

        @Override
        public String serviceName() {
            return "s3";
        }

        @Override
        public void close() {}
    }
}
