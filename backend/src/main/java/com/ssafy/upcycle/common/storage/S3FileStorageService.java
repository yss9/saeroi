package com.ssafy.upcycle.common.storage;

import com.ssafy.upcycle.common.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.AbortedException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * MinIO / S3 구현. S15P21E104-162 · S15P21E104-248.
 *
 * <p>{@link LocalFileStorageService} 와 <b>같은 저장 키</b>를 만든다. 두 구현이 다른
 * 경로를 쓰면 저장소를 바꿀 때 기존 {@code evidence_file.file_url} 을 찾을 수 없다.
 * 형식 규칙도 {@link FileContentPolicy}, 해시 형식도 {@link FileHash} 를 공유한다.
 *
 * <h2>임시 파일을 거친다 — 해시 때문이다</h2>
 *
 * <p>이 클래스의 핵심 제약은 <b>기록된 바이트의 해시</b>를 돌려줘야 한다는 것이다.
 * 그 값이 온체인에 올라가므로 실제 저장된 것과 달라선 안 된다.
 *
 * <p>업로드 스트림을 {@code DigestInputStream} 으로 감싸 S3 에 바로 흘리는 방법이
 * 더 간단해 보이는데 쓰지 않았다 — <b>SDK 가 요청을 재시도하면 스트림을 다시 읽는다.</b>
 * 그때 다이제스트는 같은 바이트를 두 번 먹어 <b>조용히 틀린 해시</b>가 된다. 실패가 아니라
 * 잘못된 값이 나오는 종류라 알아차릴 방법이 없다.
 *
 * <p>메모리 버퍼링도 쓰지 않았다. 파일 10MB × 10개 = 100MB 를 힙에 올리는 것이고,
 * 동시 업로드가 몇 건만 겹쳐도 서버가 죽는다.
 *
 * <p>그래서 임시 파일에 쓰면서 해시를 만들고({@code DigestOutputStream}, 로컬 구현과
 * 같은 방식) 그 파일을 업로드한다. 디스크를 한 번 더 지나지만 <b>해시가 실제 바이트를
 * 가리키고</b> 메모리가 파일 크기에 비례하지 않는다.
 *
 * <h2>업로드 실패는 흔적을 남기지 않는다</h2>
 *
 * <p>임시 파일은 {@code finally} 에서 지운다. S3 객체는 PutObject 가 실패하면 아예
 * 만들어지지 않으므로(부분 업로드는 멀티파트에서만 생긴다) 따로 정리할 것이 없다.
 *
 * <h2>SDK 실패는 {@link SdkException} 으로 잡는다 — {@code S3Exception} 으로는 부족하다</h2>
 *
 * <p>MinIO 가 <b>도달 불가</b>일 때(컨테이너 정지, DNS 실패, 커넥션 타임아웃) SDK 가 던지는
 * 것은 {@code S3Exception} 이 아니라 {@code SdkClientException} 이다. 둘은 상속 관계가 아니고
 * 공통 조상이 {@link SdkException} 뿐이다 — 즉 {@code catch (S3Exception)} 은
 * <b>"서비스가 오류를 응답한 경우" 만</b> 잡고 "서비스에 닿지도 못한 경우" 는 놓친다.
 * 그래서 이 클래스의 세 메서드는 모두 {@link SdkException} 으로 잡는다.
 *
 * <p>단 {@link AbortedException}({@code SdkClientException} 의 하위 타입, 스레드 인터럽트·
 * 요청 취소)은 저장 실패가 아니므로 {@code store}/{@code exists} 에서 그대로 다시 던진다.
 */
public class S3FileStorageService implements FileStorageService {

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");

    private final S3Client s3;
    private final String bucket;
    private final Clock clock;

    public S3FileStorageService(S3Client s3, FileStorageProperties properties, Clock clock) {
        this.s3 = s3;
        this.bucket = properties.bucket();
        this.clock = clock;
    }

    @Override
    public StoredFile store(
            FileReference reference,
            String originalFilename,
            String contentType,
            InputStream content,
            long size) {
        String extension =
                FileContentPolicy.requireAcceptable(originalFilename, contentType, size);
        String key = deriveKey(reference, extension);

        Path staged = null;
        try {
            staged = Files.createTempFile(".upload-", ".part");

            MessageDigest digest = FileHash.sha256();
            long written;
            try (OutputStream out = Files.newOutputStream(staged);
                    DigestOutputStream digesting = new DigestOutputStream(out, digest)) {
                written = content.transferTo(digesting);
            }

            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength(written)
                            .build(),
                    RequestBody.fromFile(staged));

            return new StoredFile(
                    key, originalFilename, contentType, written, FileHash.format(digest));
        } catch (AbortedException aborted) {
            // 인터럽트·요청 취소다. 저장 실패로 바꿔 내보내면 취소가 도메인 오류로 보고되고
            // (500 FILE_STORAGE_FAILED) 호출부는 재시도할 근거가 없는데 재시도하게 된다.
            // 임시 파일 정리(finally)는 그대로 하고 원래 신호를 올려보낸다.
            throw aborted;
        } catch (IOException | SdkException failure) {
            // SdkException 이어야 한다 — 도달 불가는 SdkClientException 이고 S3Exception 이 아니다.
            throw new BusinessException(FileStorageErrorCode.FILE_STORAGE_FAILED);
        } finally {
            deleteQuietly(staged);
        }
    }

    /**
     * 객체를 열어 준다. 응답 스트림을 그대로 넘긴다.
     *
     * <p><b>바이트를 미리 읽지 않는다.</b> {@code getObjectAsBytes} 가 더 짧지만 파일
     * 하나가 최대 10MB 이고 동시 조회가 겹치면 그만큼 힙에 쌓인다. 스트림을 그대로
     * 서블릿 출력으로 흘리면 메모리가 파일 크기에 비례하지 않는다.
     *
     * <p>크기는 응답의 {@code contentLength} 를 쓴다 — 실제로 저장된 객체의 길이다.
     * 미디어 타입은 객체 메타데이터가 아니라 <b>키의 확장자</b>에서 파생한다
     * ({@link FileContentPolicy#mediaTypeOf} 주석 참고. 로컬 구현과 답이 같아야 한다).
     */
    @Override
    public FileContent read(String storageKey) {
        try {
            ResponseInputStream<GetObjectResponse> object = s3.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(storageKey).build());
            return new FileContent(
                    object,
                    object.response().contentLength(),
                    FileContentPolicy.mediaTypeOf(storageKey));
        } catch (NoSuchKeyException absent) {
            throw new BusinessException(FileStorageErrorCode.FILE_NOT_FOUND);
        } catch (AbortedException aborted) {
            // 취소는 "있다/없다" 에 대한 답이 아니다. 도메인 오류로 바꾸지 않고 올려보낸다.
            // (AbortedException 은 SdkClientException 의 하위 타입이라 아래 catch 보다 먼저
            // 와야 한다. 인터럽트·요청 취소를 500 FILE_STORAGE_FAILED 로 보고하면 호출부는
            // 재시도할 근거가 없는데 재시도하게 된다. store()/exists() 와 같은 판단이다.)
            throw aborted;
        } catch (SdkException failure) {
            // 권한 오류·연결 실패를 "없음" 으로 답하면 화면에 "증빙 없음" 이라는 거짓이
            // 나간다. exists() 와 같은 판단이다.
            //
            // 잡는 타입이 SdkException 이어야 한다 — MinIO 도달 불가(컨테이너 정지·DNS 실패·
            // 커넥션 타임아웃)는 S3Exception 이 아니라 SdkClientException 으로 오고, 둘은
            // 상속 관계가 아니라 공통 조상이 SdkException 뿐이다. S3Exception 만 잡으면
            // 바로 위 판단이 하필 "연결 실패" 에서만 무력화된다 — 원시 SDK 예외가
            // 그대로 나가 ApiResponse 봉투 없는 500 이 된다.
            throw new BusinessException(FileStorageErrorCode.FILE_STORAGE_FAILED);
        }
    }

    @Override
    public boolean delete(String storageKey) {
        try {
            // S3 DeleteObject 는 없는 키에도 성공한다. 그래서 "지웠는지" 를 알려면
            // 먼저 확인해야 한다 — 호출부(롤백 경로)가 이 boolean 을 로그에 쓴다.
            if (!exists(storageKey)) {
                return false;
            }
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
            return true;
        } catch (BusinessException | SdkException failure) {
            // 삭제 실패로 예외를 던지지 않는다. 호출부는 롤백 경로에서 이걸 부르는데,
            // 여기서 던지면 원래 실패 원인을 덮어쓴다 (로컬 구현과 같은 계약).
            //
            // 잡는 범위가 둘인 이유:
            //   - SdkException  : 도달 불가(SdkClientException)와 서비스 오류(S3Exception)가
            //                     형제 타입이라 공통 조상으로 잡아야 둘 다 걸린다.
            //   - BusinessException : 위 exists() 는 계약대로 저장소 오류를 이 예외로 바꿔 던진다.
            //                     그것을 여기서 흘리면 결국 롤백 경로에서 던지는 것과 같다.
            //
            // AbortedException(인터럽트) 도 여기서는 의도적으로 삼킨다 — 이 메서드는 이미
            // 실패한 트랜잭션을 정리하는 자리이고, 무엇을 던져도 원래 원인을 덮는다.
            return false;
        }
    }

    @Override
    public boolean exists(String storageKey) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(storageKey).build());
            return true;
        } catch (NoSuchKeyException absent) {
            return false;
        } catch (AbortedException aborted) {
            // 취소는 "있다/없다" 에 대한 답이 아니다. 도메인 오류로 바꾸지 않고 올려보낸다.
            throw aborted;
        } catch (SdkException failure) {
            // 권한 오류나 연결 실패를 "없음" 으로 답하면 호출부가 파일이 사라졌다고
            // 판단한다. 증빙 조회에서 그건 "증빙 없음" 으로 화면에 나가는 거짓이다.
            //
            // 도달 불가(SdkClientException)는 S3Exception 이 아니다. 예전에는 여기서
            // 놓쳐 원시 SDK 예외가 그대로 밖으로 나가 ApiResponse 봉투 없는 500 이 됐다.
            throw new BusinessException(FileStorageErrorCode.FILE_STORAGE_FAILED);
        }
    }

    /**
     * 저장 키. <b>로컬 구현과 한 글자도 달라선 안 된다.</b>
     *
     * <pre>
     *   {reference_type}/{reference_id}/{yyyy}/{MM}/{uuid}.{ext}
     * </pre>
     *
     * <p>클라이언트가 준 이름은 확장자 판정에만 쓰고 키에는 들어가지 않는다.
     * S3 는 {@code ../} 를 경로로 해석하지 않지만, 키에 그런 값이 들어가면 나중에
     * 파일시스템으로 되옮길 때 문제가 된다. 무엇보다 두 구현의 키가 같아야 저장소를
     * 바꿀 때 기존 행을 그대로 쓸 수 있다.
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
