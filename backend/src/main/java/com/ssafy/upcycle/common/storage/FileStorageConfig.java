package com.ssafy.upcycle.common.storage;

import java.net.URI;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * 저장 구현을 고른다.
 *
 * <p>이 프로젝트는 {@code @ConfigurationPropertiesScan} 을 쓰지 않는다
 * ({@code JwtSecurityConfig} 참고). 프로퍼티 클래스는 이렇게 직접 등록해야 빈으로 잡힌다.
 *
 * <h2>선택을 한곳에서 한다</h2>
 *
 * <p>두 구현에 {@code @ConditionalOnProperty} 를 각각 붙이는 방법도 있지만 그러지 않았다 —
 * 조건이 두 곳에 흩어지면 둘 다 참(빈 충돌)이거나 <b>둘 다 거짓</b>(빈 없음)인 조합이
 * 생긴다. 후자가 특히 나쁘다. 기동은 되는데 업로드에서 처음 드러난다.
 *
 * <p>여기서는 {@code switch} 가 모든 값을 덮으므로 빈이 없는 경우가 존재하지 않는다.
 *
 * <h2>S3 클라이언트를 빈으로 두지 않는다</h2>
 *
 * <p>처음에는 {@code type=local} 일 때도 {@code S3Client} 빈을 만들었다. <b>전체 테스트가
 * 깨졌다</b> — {@code AwsBasicCredentials.create("")} 가
 * {@code "Access key ID cannot be blank"} 로 거부한다. "자격증명이 비어 있어도 빌더는
 * 통과한다" 고 적어 두었는데 사실이 아니었다.
 *
 * <p>그래서 클라이언트는 <b>S3 를 고른 분기 안에서만</b> 만든다. 로컬 배포는 AWS SDK 를
 * 한 줄도 실행하지 않는다. 가짜 클라이언트를 끼우는 것은 테스트가
 * {@link S3FileStorageService} 를 직접 만들어 해결한다 — 빈 오버라이드보다 단순하다.
 */
@Configuration
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileStorageConfig {

    private static final Logger log = LoggerFactory.getLogger(FileStorageConfig.class);

    /**
     * 어느 저장소가 붙었는지 <b>시작 로그에 남긴다.</b>
     *
     * <p>설정 한 줄로 파일이 다른 곳에 쌓이는데, 그 사실이 어디에도 안 남으면
     * "업로드는 성공했는데 파일이 없다" 를 조사할 실마리가 없다.
     * {@code PaymentGatewayConfig} 가 Mock/실결제를 로그로 남기는 것과 같은 이유다.
     */
    @Bean
    public FileStorageService fileStorageService(FileStorageProperties properties, Clock clock) {
        return switch (properties.type()) {
            case LOCAL -> {
                log.info("File storage: LOCAL filesystem at {}", properties.root());
                yield new LocalFileStorageService(properties, clock);
            }
            case S3 -> {
                log.info(
                        "File storage: S3-compatible bucket={} endpoint={}",
                        properties.bucket(),
                        hasEndpoint(properties) ? properties.endpoint() : "(AWS default)");
                yield new S3FileStorageService(s3Client(properties), properties, clock);
            }
        };
    }

    /**
     * S3 클라이언트. <b>S3 를 고른 경우에만 불린다.</b>
     *
     * <p>{@code @Bean} 이 아니다 — 빈으로 두면 로컬 배포에서도 만들어지고, 자격증명이
     * 비어 있어 기동이 실패한다 (클래스 주석 참고).
     */
    private static S3Client s3Client(FileStorageProperties properties) {
        var builder = S3Client.builder()
                // HTTP 클라이언트를 명시한다. 클래스패스 탐색에 맡기면 구현이 없을 때
                // 기동이 아니라 첫 업로드에서 실패한다.
                .httpClientBuilder(ApacheHttpClient.builder())
                .region(Region.of(properties.region()))
                .serviceConfiguration(S3Configuration.builder()
                        // MinIO 는 가상 호스트 방식(bucket.host)을 쓰지 않는다. false 면
                        // SDK 가 bucket 을 호스트명에 붙여 DNS 조회부터 실패하고,
                        // 증상이 "버킷 없음" 이 아니라 연결 실패라 원인이 잘 안 보인다.
                        .pathStyleAccessEnabled(properties.pathStyleAccess())
                        .build())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())));

        // 비어 있으면 SDK 가 리전 기본 엔드포인트를 쓴다 (실제 AWS S3).
        if (hasEndpoint(properties)) {
            builder = builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return builder.build();
    }

    private static boolean hasEndpoint(FileStorageProperties properties) {
        return properties.endpoint() != null && !properties.endpoint().isBlank();
    }
}
