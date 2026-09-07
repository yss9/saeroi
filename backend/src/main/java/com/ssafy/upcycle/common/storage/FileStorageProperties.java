package com.ssafy.upcycle.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 파일 저장 설정.
 *
 * <p>{@code type} 이 어느 구현을 쓸지 정한다. 기본값은 {@link Type#LOCAL} —
 * <b>설정을 하나도 안 넣으면 지금 동작이 그대로 유지된다.</b> 새 저장소를 기본으로
 * 만들면 설정이 빠진 환경에서 조용히 다른 곳에 파일이 쌓인다.
 *
 * <p>{@code root} 는 로컬 구현이 쓰는 기준 디렉터리다. 컨테이너에는 이미
 * {@code file-storage} 볼륨이 {@code /app/files} 로 마운트돼 있으므로 그것을 기본값으로 둔다.
 *
 * <p>나머지는 S3 구현이 쓴다. MinIO 도 S3 호환 API 라 {@code endpoint} 만 바꿔 붙는다.
 *
 * @param type 저장 구현. {@code local} 또는 {@code s3}
 * @param root 로컬 구현의 기준 디렉터리
 * @param bucket S3 버킷 이름
 * @param endpoint MinIO 주소. <b>실제 AWS S3 면 비워 둔다</b> — 비어 있으면 SDK 가
 *     리전 기본 엔드포인트를 쓴다
 * @param region 실제 S3 가 아니면 아무 값이나 되지만 SDK 가 요구하므로 채운다
 * @param accessKey MinIO 루트 사용자 또는 IAM 액세스 키
 * @param secretKey 위의 비밀키
 * @param pathStyleAccess MinIO 는 {@code true} 여야 한다 — 아래 주석 참고
 */
@ConfigurationProperties(prefix = "app.storage")
public record FileStorageProperties(
        Type type,
        String root,
        String bucket,
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        Boolean pathStyleAccess) {

    public enum Type {
        LOCAL,
        S3
    }

    public FileStorageProperties {
        if (type == null) {
            type = Type.LOCAL;
        }
        if (root == null || root.isBlank()) {
            root = "/app/files";
        }
        if (region == null || region.isBlank()) {
            region = "us-east-1";
        }
        if (pathStyleAccess == null) {
            // MinIO 는 가상 호스트 방식(bucket.host)을 쓰지 않는다. false 로 두면
            // SDK 가 bucket 을 호스트명에 붙여 DNS 조회부터 실패한다 —
            // 증상이 "버킷 없음" 이 아니라 연결 실패라 원인이 잘 안 보인다.
            pathStyleAccess = true;
        }
        if (type == Type.S3) {
            requirePresent(bucket, "app.storage.bucket");
            requirePresent(accessKey, "app.storage.access-key");
            requirePresent(secretKey, "app.storage.secret-key");
        }
    }

    /**
     * 로컬 저장소 설정.
     *
     * <p><b>여덟 개 위치 인자를 늘어놓지 않게 하려고 둔다.</b> 그중 여섯 개가 S3 전용이라
     * 로컬만 쓰는 자리에서 {@code null} 을 여섯 번 적게 되고, 그러면 어느 자리가
     * 무엇인지 세어야 한다. Spring 은 setter 가 아니라 프로퍼티 바인딩으로 채우므로
     * 이 팩토리는 <b>코드에서 직접 만드는 경우</b>(테스트)만을 위한 것이다.
     */
    public static FileStorageProperties local(String root) {
        return new FileStorageProperties(Type.LOCAL, root, null, null, null, null, null, null);
    }

    /** S3 저장소 설정. 위와 같은 이유로 둔다. */
    public static FileStorageProperties s3(
            String bucket, String endpoint, String accessKey, String secretKey) {
        return new FileStorageProperties(
                Type.S3, null, bucket, endpoint, null, accessKey, secretKey, null);
    }

    /**
     * S3 를 골랐는데 값이 없으면 <b>기동을 막는다.</b>
     *
     * <p>비워 두고 넘어가면 첫 업로드에서 실패하는데, 그 시점에는 사용자가 파일을
     * 고른 뒤다. 설정 누락을 배포 시점에 드러내는 편이 낫다.
     */
    private static void requirePresent(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "app.storage.type=s3 requires " + key + " to be set");
        }
    }
}
