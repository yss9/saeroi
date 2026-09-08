package com.ssafy.upcycle.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.common.exception.GlobalExceptionHandler;
import com.ssafy.upcycle.common.response.ApiResponse;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 파일 크기 상한은 <b>세 층</b>에서 다뤄진다. 이 클래스는 그중 1층을 본다.
 *
 * <p>정책 문서({@code docs/superpowers/specs/2026-09-07-file-upload-policy.md}) 는 "10MB 를
 * 두 번 강제한다" 고 쓰지만, 실제로 관여하는 지점은 셋이고 <b>서로 다른 것을 한다.</b>
 *
 * <table border="1">
 *   <caption>크기 상한 3층</caption>
 *   <tr><th>층</th><th>어디</th><th>무엇을 하는가</th><th>누가 검증하는가</th></tr>
 *   <tr>
 *     <td>1</td>
 *     <td>{@code spring.servlet.multipart.max-file-size} / {@code max-request-size}</td>
 *     <td>바이트가 버퍼링되기 전에 컨테이너가 파싱을 중단한다. 서버를 지키는 층이다.
 *         우리 코드는 실행되지 않으므로 {@code MaxUploadSizeExceededException} 을
 *         읽을 수 있는 오류로 <b>번역</b>해야 한다</td>
 *     <td><b>이 클래스</b></td>
 *   </tr>
 *   <tr>
 *     <td>2</td>
 *     <td>{@code EvidenceUploadService.MAX_FILE_SIZE_BYTES}</td>
 *     <td>실제 버퍼링된 크기({@code MultipartFile#getSize})를 보고 정책 코드를 던진다.
 *         1층이 꺼져 있거나 느슨해도 애플리케이션이 스스로 막는다</td>
 *     <td>{@code EvidenceUploadServiceTests} ·
 *         {@code EvidenceUploadControllerTests}</td>
 *   </tr>
 *   <tr>
 *     <td>3</td>
 *     <td>{@link FileContentPolicy#requireAcceptable}</td>
 *     <td><b>상한을 전혀 모른다.</b> {@code size <= 0} 만 걸러 {@code FILE_EMPTY} 를 준다</td>
 *     <td>{@code FileContentPolicyTests}
 *         ({@code doesNotApplyAnUpperSizeLimitItself})</td>
 *   </tr>
 * </table>
 *
 * <h2>왜 1층을 종단(end-to-end)으로 못 보는가</h2>
 *
 * <p>둘 다 막힌다.
 *
 * <ol>
 *   <li>MockMvc 는 {@code MockMultipartHttpServletRequest} 를 직접 만들어 넘긴다 —
 *       <b>실제 multipart 파싱이 없다.</b> 파싱 중에 나오는 예외이므로 재현되지 않는다
 *   <li>{@code src/test/resources/application.yml} 이 운영 {@code application.yml} 을
 *       <b>가린다</b>(대체가 아니라 교체다). 테스트 프로파일에는
 *       {@code spring.servlet.multipart} 설정이 아예 없어 Spring Boot 기본값(파일 1MB)이
 *       쓰인다. 즉 <b>테스트에서 도는 1층 값은 운영 값이 아니다</b>
 * </ol>
 *
 * <p>그래서 1층을 <b>둘로 쪼개</b> 검증한다: 운영 yml 이 정책과 같은 값을 선언하는지(설정),
 * 그리고 그 층이 던지는 예외가 계약대로 번역되는지(핸들러). 실제 컨테이너 왕복은
 * {@code MockMvc} 로 대체할 수 없다 — 남긴 항목으로 보고했다.
 */
class FileSizeLimitLayerTests {

    /** 정책 문서의 파일당 상한. */
    private static final DataSize POLICY_MAX_FILE_SIZE = DataSize.ofMegabytes(10);

    /** nginx {@code client_max_body_size 20m} 에 맞춘 값. */
    private static final DataSize NGINX_MAX_REQUEST_SIZE = DataSize.ofMegabytes(20);

    // ── 1층 (a) 설정 값 ─────────────────────────────────────

    /**
     * 운영 {@code application.yml} 의 multipart 상한이 정책과 같은지.
     *
     * <p>Spring Boot 기본값은 <b>파일 1MB</b>다. 이 설정이 사라지면 휴대폰 사진(3~8MB)이
     * 우리 검증에 닿기도 전에 전부 거절되고, 사용자에게는 {@code FILE_TOO_LARGE} 로
     * 보이므로 <b>정책이 10MB 인데 1MB 처럼 동작하는</b> 상태가 된다. 2층 값(10MB)은
     * 그대로라 서비스 테스트는 전부 통과한다 — 그 실패를 잡는 테스트가 여기밖에 없다.
     *
     * <p>테스트 클래스패스에서 {@code application.yml} 은 테스트용 파일이라, 운영 파일을
     * 파일 경로로 직접 읽어 바인딩한다.
     */
    @Test
    void productionConfigDeclaresThePolicyLimits() throws IOException {
        MultipartProperties production = bindProductionMultipartProperties();

        assertThat(production.getMaxFileSize())
                .as("파일당 상한은 정책의 10MB 와 같아야 한다 (기본값 1MB 로 돌아가면 안 된다)")
                .isEqualTo(POLICY_MAX_FILE_SIZE);
        assertThat(production.getMaxRequestSize())
                .as("요청 전체 상한은 nginx client_max_body_size(20m) 에 맞춘다 — 더 크게 열면"
                        + " nginx 가 413 을 내고 사용자에게는 빈 페이지로 보인다")
                .isEqualTo(NGINX_MAX_REQUEST_SIZE);
        assertThat(production.getMaxRequestSize())
                .as("요청 상한이 파일 상한보다 작으면 정책상 허용되는 파일 하나도 못 올린다")
                .isGreaterThanOrEqualTo(production.getMaxFileSize());
    }

    private MultipartProperties bindProductionMultipartProperties() throws IOException {
        // src/test/resources/application.yml 이 클래스패스에서 운영 파일을 가리므로
        // 소스 경로로 직접 읽는다. 클래스패스로 읽으면 테스트용 파일을 검사하게 되고,
        // 그건 이 테스트가 확인하려는 대상이 아니다.
        FileSystemResource production = new FileSystemResource("src/main/resources/application.yml");
        assertThat(production.exists())
                .as("운영 application.yml 이 이 경로에 있어야 한다")
                .isTrue();

        List<PropertySource<?>> loaded =
                new YamlPropertySourceLoader().load("production", production);
        StandardEnvironment environment = new StandardEnvironment();
        loaded.forEach(source -> environment.getPropertySources().addFirst(source));

        return Binder.get(environment)
                .bind("spring.servlet.multipart", MultipartProperties.class)
                .orElseThrow(() -> new AssertionError(
                        "운영 application.yml 에 spring.servlet.multipart 설정이 없다"));
    }

    // ── 1층 (b) 예외 번역 ───────────────────────────────────

    /**
     * 1층이 걸렀을 때 사용자가 받는 것.
     *
     * <p>컨테이너가 파싱을 중단하면 우리 검증 코드는 <b>한 줄도 실행되지 않는다.</b>
     * 번역하지 않으면 {@code @ExceptionHandler(Exception.class)} 로 떨어져 500 이 나가고,
     * 사용자는 "사진을 줄이면 된다" 는 것을 알 방법이 없다.
     *
     * <p>2층이 던지는 것과 <b>같은 코드</b>여야 한다는 것이 핵심이다. 어느 층이 걸렀는지는
     * 서버 사정이고, 프론트는 {@code code} 로만 분기한다.
     */
    @Test
    void translatesTheContainerLimitIntoThePolicyErrorCode() {
        ResponseEntity<ApiResponse<Void>> response = new GlobalExceptionHandler()
                .handleMaxUploadSizeExceededException(
                        new MaxUploadSizeExceededException(POLICY_MAX_FILE_SIZE.toBytes()));

        assertThat(response.getStatusCode())
                .as("400 이다 — 서버가 고장난 것이 아니라 요청이 크다")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode())
                .as("2층(EvidenceUploadService)이 던지는 것과 같은 코드여야 한다")
                .isEqualTo(FileStorageErrorCode.FILE_TOO_LARGE.code());
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage())
                .isEqualTo(FileStorageErrorCode.FILE_TOO_LARGE.message());
    }

    /**
     * 1층은 파일 하나 초과와 요청 전체 초과를 <b>구분하지 않는다.</b>
     *
     * <p>Spring 이 두 경우에 같은 예외를 던지고, 사용자가 할 일도 같다 — 줄이거나 나눠
     * 올린다. 구분하려면 예외의 {@code maxUploadSize} 를 보고 추측해야 하는데, 그 값은
     * 두 설정 중 어느 것이 걸렸는지에 따라 달라서 신뢰할 수 없다.
     */
    @Test
    void doesNotDistinguishPerFileFromPerRequestOverflow() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiResponse<Void>> perFile = handler.handleMaxUploadSizeExceededException(
                new MaxUploadSizeExceededException(POLICY_MAX_FILE_SIZE.toBytes()));
        ResponseEntity<ApiResponse<Void>> perRequest = handler.handleMaxUploadSizeExceededException(
                new MaxUploadSizeExceededException(NGINX_MAX_REQUEST_SIZE.toBytes()));

        assertThat(perRequest.getStatusCode()).isEqualTo(perFile.getStatusCode());
        assertThat(perRequest.getBody()).isNotNull();
        assertThat(perFile.getBody()).isNotNull();
        assertThat(perRequest.getBody().getCode()).isEqualTo(perFile.getBody().getCode());
    }

    // ── 3층 경계 재확인 ─────────────────────────────────────

    /**
     * 3층이 크기 상한을 모른다는 사실을 <b>2층 상한과 붙여</b> 다시 못 박는다.
     *
     * <p>{@code FileContentPolicyTests} 에도 같은 뜻의 테스트가 있지만, 그쪽은 "정책이
     * 상한을 갖지 않는다" 를 단독으로 본다. 여기서 다시 보는 이유는 <b>어느 층이 무엇을
     * 막는지</b>가 이 클래스의 주제이고, 3층에 상한을 옮겨 넣으면 2층·3층이 서로 다른 층에서
     * 같은 오류를 던지게 되어 어느 쪽이 걸렀는지 알 수 없어지기 때문이다.
     */
    @Test
    void theContentPolicyLayerIsNotWhereTheSizeLimitLives() {
        long overTheLimit = POLICY_MAX_FILE_SIZE.toBytes() + 1;

        assertThat(FileContentPolicy.requireAcceptable("huge.jpg", "image/jpeg", overTheLimit))
                .as("10MB 를 넘겨도 통과한다 — 상한은 2층의 몫이다")
                .isEqualTo("jpg");
    }
}
