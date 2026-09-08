package com.ssafy.upcycle.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.AbortedException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
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
 *
 * <h2>실패는 두 종류를 모두 넣어야 한다</h2>
 *
 * <p>SDK 가 내는 실패는 서로 상속 관계가 없는 두 갈래다.
 *
 * <ul>
 *   <li>{@code S3Exception} — <b>서비스가 오류를 응답한 경우</b> (AccessDenied, InternalError…)
 *   <li>{@code SdkClientException} — <b>서비스에 닿지도 못한 경우</b>
 *       (컨테이너 정지, DNS 실패, 커넥션 타임아웃)
 * </ul>
 *
 * <p>공통 조상은 {@code SdkException} 뿐이라 한쪽만 잡는 구현은 다른 쪽을 그대로 흘린다.
 * 예전 이 테스트는 "도달 불가" 를 {@code S3Exception(InternalError)} 로 흉내내서
 * <b>가짜가 현실과 다른 종류의 실패를 재현했고</b>, 그래서 도달 불가에서 원시 SDK 예외가
 * 새어 나가는 결함을 통과시켰다. 지금은 실패를 넣는 테스트마다 두 종류를 함께 돌린다
 * ({@link #sdkFailures()}).
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("sdkFailures")
    void storeReportsADomainErrorAndLeavesNoTemporaryFile(String kind, SdkException failure)
            throws Exception {
        // 두 실패 종류를 모두 넣는다. SdkClientException(도달 불가)은 S3Exception 이 아니라
        // catch (S3Exception) 에 걸리지 않았고, 원시 SDK 예외가 그대로 밖으로 나갔다.
        long before = temporaryPartFiles();
        s3.putFailure = failure;

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("sdkFailures")
    void deleteDoesNotThrowWhenTheDeleteCallFails(String kind, SdkException failure) {
        // 호출부는 트랜잭션 롤백 경로에서 이걸 부른다. 여기서 던지면 원래 실패 원인을 덮는다.
        StoredFile stored = store("photo.jpg", "image/jpeg");
        s3.deleteFailure = failure;

        assertThat(storage().delete(stored.storageKey())).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sdkFailures")
    void deleteDoesNotThrowWhenTheExistenceCheckFails(String kind, SdkException failure) {
        // delete() 는 먼저 exists() 를 부르고, exists() 는 계약대로 저장소 오류를
        // BusinessException 으로 바꿔 던진다. 그것을 delete() 가 흘리면 결국 롤백 경로에서
        // 던지는 것과 같다 — MinIO 가 죽어 있으면 업로드 롤백 중 원래 예외가 덮인다.
        StoredFile stored = store("photo.jpg", "image/jpeg");
        s3.headFailure = failure;

        assertThat(storage().delete(stored.storageKey())).isFalse();
    }

    @Test
    void existsThrowsWhenS3RespondsWithAnError() {
        // 이 테스트가 재현하는 것은 "서비스가 오류를 응답한 경우" 다 (도달 불가가 아니다).
        // 권한 오류를 "없음" 으로 답하면 호출부가 파일이 사라졌다고 판단하고,
        // 증빙 조회에서 그건 "증빙 없음" 이라는 거짓이 화면에 나가는 것이다.
        s3.headFailure = FakeS3Client.serviceError("AccessDenied");

        assertThatThrownBy(() -> storage().exists("MATERIAL/1024/2026/09/whatever.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_STORAGE_FAILED);
    }

    @Test
    void existsThrowsWhenS3IsUnreachable() {
        // 진짜 도달 불가다 — MinIO 컨테이너 정지·DNS 실패·커넥션 타임아웃은
        // S3Exception 이 아니라 SdkClientException 으로 온다. catch (S3Exception) 만
        // 하던 동안 이건 원시 SDK 예외로 새어 나가 ApiResponse 봉투 없는 500 이 됐다.
        s3.headFailure = FakeS3Client.unreachable();

        assertThatThrownBy(() -> storage().exists("MATERIAL/1024/2026/09/whatever.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_STORAGE_FAILED);
    }

    // ── 취소는 저장 실패가 아니다 ────────────────────────────

    @Test
    void storePassesCancellationThroughInsteadOfCallingItAStorageFailure() throws Exception {
        // AbortedException(SdkClientException 의 하위 타입) 은 인터럽트·요청 취소다.
        // FILE_STORAGE_FAILED 로 바꾸면 취소가 500 도메인 오류로 보고되고,
        // 호출부는 재시도할 근거가 없는데 재시도하게 된다. 임시 파일 정리는 그대로 해야 한다.
        long before = temporaryPartFiles();
        s3.putFailure = AbortedException.create("취소됨");

        assertThatThrownBy(() -> store("photo.jpg", "image/jpeg"))
                .isInstanceOf(AbortedException.class);

        assertThat(temporaryPartFiles()).isEqualTo(before);
    }

    @Test
    void existsPassesCancellationThrough() {
        s3.headFailure = AbortedException.create("취소됨");

        assertThatThrownBy(() -> storage().exists("MATERIAL/1024/2026/09/whatever.jpg"))
                .isInstanceOf(AbortedException.class);
    }

    @Test
    void deleteSwallowsEvenCancellation() {
        // delete() 만 예외다 — 이미 실패한 트랜잭션을 정리하는 자리라 무엇을 던져도
        // 원래 원인을 덮는다. 계약은 "어떤 경우에도 던지지 않는다" 다.
        StoredFile stored = store("photo.jpg", "image/jpeg");
        s3.deleteFailure = AbortedException.create("취소됨");

        assertThat(storage().delete(stored.storageKey())).isFalse();
    }

    /**
     * SDK 실패의 두 갈래. 서로 상속 관계가 없고 공통 조상이 {@code SdkException} 뿐이라
     * 실패를 넣는 테스트는 반드시 둘 다 돌려야 한다.
     */
    static Stream<Arguments> sdkFailures() {
        return Stream.of(
                arguments("서비스가 오류를 응답 (S3Exception)", FakeS3Client.serviceError("InternalError")),
                arguments("서비스에 닿지 못함 (SdkClientException)", FakeS3Client.unreachable()));
    }

    /**
     * 가짜 S3. 올라간 바이트를 그대로 들고 있어 해시 대조에 쓴다.
     *
     * <p>{@code S3Client} 의 나머지 수백 개 메서드는 인터페이스 기본 구현이 있어
     * 구현할 필요가 없다 — 이 테스트가 쓰는 셋만 덮는다.
     *
     * <p>실패는 <b>boolean 이 아니라 예외 인스턴스로</b> 넣는다. boolean 이던 동안 이 가짜는
     * 모든 실패를 {@code S3Exception} 으로만 흉내낼 수 있었고, 그래서 도달 불가
     * ({@code SdkClientException})를 재현할 방법이 아예 없었다.
     */
    private static class FakeS3Client implements software.amazon.awssdk.services.s3.S3Client {

        record Put(PutObjectRequest request, byte[] body) {}

        final List<Put> puts = new ArrayList<>();
        final Map<String, byte[]> objects = new HashMap<>();
        SdkException putFailure;
        SdkException deleteFailure;
        SdkException headFailure;

        @Override
        public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
            if (putFailure != null) {
                throw putFailure;
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
            if (deleteFailure != null) {
                throw deleteFailure;
            }
            objects.remove(request.key());
            return DeleteObjectResponse.builder().build();
        }

        @Override
        public HeadObjectResponse headObject(HeadObjectRequest request) {
            if (headFailure != null) {
                throw headFailure;
            }
            byte[] found = objects.get(request.key());
            if (found == null) {
                throw NoSuchKeyException.builder().message("no such key").build();
            }
            return HeadObjectResponse.builder().contentLength((long) found.length).build();
        }

        /** 서비스가 오류를 <b>응답한</b> 경우. 요청은 서버에 닿았다. */
        static S3Exception serviceError(String code) {
            return (S3Exception) S3Exception.builder()
                    .awsErrorDetails(AwsErrorDetails.builder().errorCode(code).build())
                    .message(code)
                    .build();
        }

        /**
         * 서비스에 <b>닿지 못한</b> 경우. MinIO 컨테이너 정지·DNS 실패·커넥션 타임아웃이
         * 실제로 이 타입으로 온다 ({@code SdkClientException}, {@code S3Exception} 의 형제).
         */
        static SdkClientException unreachable() {
            return SdkClientException.create(
                    "Unable to execute HTTP request: minio: nodename nor servname provided",
                    new java.net.ConnectException("Connection refused"));
        }

        @Override
        public String serviceName() {
            return "s3";
        }

        @Override
        public void close() {}
    }
}
