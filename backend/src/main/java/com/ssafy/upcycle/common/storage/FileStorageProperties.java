package com.ssafy.upcycle.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 파일 저장 설정.
 *
 * <p>{@code root} 는 로컬 구현이 쓰는 기준 디렉터리다. 컨테이너에는 이미
 * {@code file-storage} 볼륨이 {@code /app/files} 로 마운트돼 있으므로 그것을 기본값으로 둔다
 * (`docker-compose.yml` 의 backend volumes).
 *
 * <p>MinIO/S3 구현체가 오면 이 클래스에 버킷·엔드포인트가 추가되고, 그때
 * {@code FileStorageService} 빈을 무엇으로 채울지는 설정으로 갈린다.
 */
@ConfigurationProperties(prefix = "app.storage")
public record FileStorageProperties(String root) {

    public FileStorageProperties {
        if (root == null || root.isBlank()) {
            root = "/app/files";
        }
    }
}
