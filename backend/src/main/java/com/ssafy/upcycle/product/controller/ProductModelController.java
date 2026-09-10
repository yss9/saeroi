package com.ssafy.upcycle.product.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.common.storage.FileContent;
import com.ssafy.upcycle.product.dto.ProductModelRegisterRequest;
import com.ssafy.upcycle.product.dto.ProductModelResponse;
import com.ssafy.upcycle.product.dto.ProductModelSearchCondition;
import com.ssafy.upcycle.product.dto.ProductModelStatusRequest;
import com.ssafy.upcycle.product.dto.ProductModelUpdateRequest;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.product.service.ProductImageService;
import com.ssafy.upcycle.product.service.ProductModelQueryService;
import com.ssafy.upcycle.product.service.ProductModelRegistrationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 제품 모델 등록·판매 상태 관리. <b>인증이 필요하다.</b>
 *
 * <p>{@code SecurityConfig} 의 permitAll 목록은 {@code /api/v1/auth/**} 와
 * {@code GET /api/health} 뿐이고 나머지는 {@code anyRequest().authenticated()} 로 떨어지므로,
 * 여기는 아무것도 하지 않아도 토큰이 필요하다.
 *
 * <p><b>역할 검사를 {@code @PreAuthorize} 로 하지 않는다.</b> 그것은 토큰의 역할 클레임을
 * 보는데, 토큰은 발급 시점의 스냅샷이라 정지되거나 역할이 바뀐 계정이 만료 전까지 계속
 * 통과한다. 소재 등록과 같은 이유로 서비스가 DB 를 다시 읽어 판단한다.
 *
 * <p>경로는 {@code /api/v1/...} 다 — 거래·지갑 계열과 같은 관례다.
 * ({@code /api/materials} 만 무버전인데, 그쪽은 티켓이 무버전으로 적혀 있어서다.)
 */
@RestController
@RequestMapping("/api/v1/product-models")
@RequiredArgsConstructor
public class ProductModelController {

    private final ProductModelRegistrationService productModelRegistrationService;
    private final ProductImageService productImageService;
    private final ProductModelQueryService productModelQueryService;

    /**
     * 제품 등록. {@code BRAND} 만 가능하다.
     *
     * <p>브랜드는 JWT {@code sub} 에서 얻는다 — 바디로 받으면 남의 이름으로 제품을 올릴 수
     * 있다.
     *
     * <p>응답의 {@code status} 는 언제나 {@code DRAFT}, {@code stockQuantity} 는 {@code 0}
     * 이다. 등록만으로는 팔 개체가 없기 때문이며, 판매 시작은 아래 상태 변경 경로다.
     */
    @PostMapping
    public ApiResponse<ProductModelResponse> register(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProductModelRegisterRequest request) {
        return ApiResponse.success(productModelRegistrationService.register(userId(jwt), request));
    }

    /**
     * 제품 정보를 고친다. <b>보낸 필드만 바뀐다.</b>
     *
     * <p>이름·가격·설명·대표 이미지를 바꾼다. 카테고리와 판매 상태는 여기서 못 바꾼다 —
     * 카테고리를 바꾸면 이미 그 분류로 팔린 제품의 이력이 흔들리고, 상태는 재고를 봐야
     * 정해지는 값이라 아래 경로가 맡는다.
     *
     * <p><b>가격은 판매 중에도 바꿀 수 있다.</b> 세일을 막으면 가격을 내리려고 제품을 새로
     * 등록하게 되고, 그러면 이미 만든 개체가 옛 제품에 붙는다. 이미 결제된 주문은 주문
     * 시점 단가 스냅샷이 지킨다.
     */
    @PatchMapping("/{productModelId}")
    public ApiResponse<ProductModelResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long productModelId,
            @Valid @RequestBody ProductModelUpdateRequest request) {
        return ApiResponse.success(
                productModelRegistrationService.update(userId(jwt), productModelId, request));
    }

    /**
     * 판매 시작 / 판매 중단.
     *
     * <p>{@code PATCH} 인 것은 모델의 일부(상태)만 바꾸기 때문이다. 상태 하나만 받는 이유는
     * {@link ProductModelStatusRequest} 주석 참고 — {@code SOLD_OUT} 은 손으로 지정할 수 없다.
     *
     * <p>본인 제품만 바꿀 수 있다. 역할만 보면 브랜드 아무나 남의 제품을 단종시킬 수 있다.
     */
    @PatchMapping("/{productModelId}/status")
    public ApiResponse<ProductModelResponse> changeStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long productModelId,
            @Valid @RequestBody ProductModelStatusRequest request) {
        return ApiResponse.success(
                productModelRegistrationService.changeStatus(userId(jwt), productModelId, request));
    }

    /**
     * 마켓 제품 목록. 필터·페이지 조건은 쿼리 파라미터다.
     *
     * <p><b>아무 조건도 주지 않으면 판매 중인 제품 전체, 최신순, 첫 페이지</b>다.
     * {@code status} 를 직접 주면 그 상태로 바뀌므로 브랜드 화면이 {@code DRAFT} 를 볼 수도
     * 있지만, <b>남의 {@code DRAFT} 까지 보인다</b> — 본인 제품만 필요하면
     * {@code GET /me} 를 쓰는 것이 맞다.
     *
     * <p>봉투는 다른 도메인과 같은 {@code PageResponse} 다 — {@code page} 는 0 부터다.
     */
    @GetMapping
    public ApiResponse<PageResponse<ProductModelResponse>> search(
            @ModelAttribute ProductModelSearchCondition condition) {
        return ApiResponse.success(productModelQueryService.search(condition));
    }

    /**
     * 제품 상세.
     *
     * <p><b>인증만 요구하고 역할은 보지 않는다</b> — 제품 정보는 마켓에 걸리는 공개
     * 정보라 누가 보든 같은 값이어야 한다. 소재 상세가 같은 판단을 한다.
     *
     * <p>등록 응답과 <b>같은 DTO</b> 다. 화면이 등록 직후와 새로고침 후에 다른 모양을
     * 받으면 그 차이를 프론트가 흡수해야 한다.
     */
    @GetMapping("/{productModelId}")
    public ApiResponse<ProductModelResponse> findDetail(@PathVariable Long productModelId) {
        return ApiResponse.success(productModelQueryService.findDetail(productModelId));
    }

    /**
     * 내가 등록한 제품 목록. 브랜드 관리 화면이 쓴다.
     *
     * <p><b>{@code DRAFT} 를 포함한다.</b> 브랜드는 여기서 판매 시작을 눌러야 하는데
     * 준비 중인 제품이 안 보이면 그 버튼을 놓을 자리가 없다. 마켓 목록은 반대로
     * {@code ON_SALE} 만 보여준다.
     *
     * <p><b>이 매핑이 {@code /{productModelId}} 보다 먼저 선언되어 있어야 하는 것은
     * 아니다</b> — Spring MVC 는 리터럴 경로를 경로 변수보다 우선한다. 그래도 {@code /me}
     * 가 {@code productModelId} 로 잘못 바인딩되면 400 이 되므로 테스트로 고정한다.
     *
     * <p>브랜드는 JWT {@code sub} 에서 얻는다 — 경로나 쿼리로 받으면 남의 제품 목록을
     * 들여다볼 수 있고, 거기에는 아직 공개하지 않은 제품이 들어 있다.
     */
    @GetMapping("/me")
    public ApiResponse<List<ProductModelResponse>> findMyProductModels(
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(productModelQueryService.findMyProductModels(userId(jwt)));
    }

    /**
     * 제품 대표 이미지. 마켓 목록·상세가 쓴다.
     *
     * <p><b>저장 키가 아니라 제품 id 로 받는다.</b> 키를 URL 에 넣게 하면 {@code ..} 나 남의
     * 키를 찔러 볼 수 있고 그 검사는 틀리기 쉽다. 행에서 꺼내면 그런 요청이 만들어지지 않는다.
     *
     * <p><b>인증이 필요하다.</b> 마켓 자체가 로그인 뒤에 보이므로 이미지만 열어 둘 이유가
     * 없다. 대신 브라우저는 {@code <img src>} 에 Authorization 헤더를 붙이지 않으므로,
     * 프론트는 fetch 로 받아 blob URL 로 바꿔 써야 한다 — 증빙 이미지와 같은 방식이다.
     *
     * <h2>헤더</h2>
     *
     * <ul>
     *   <li>{@code Content-Disposition: inline} — 사진은 화면에 그린다. 원본 파일명은 싣지
     *       않는다: 한글·따옴표·개행이 들어간 이름은 헤더 인젝션이 되고 그 이름은 사용자가
     *       정한 값이다
     *   <li>{@code X-Content-Type-Options: nosniff} — 화이트리스트로 svg 를 막는 노력이
     *       브라우저의 타입 추측으로 무의미해질 수 있다
     *   <li>{@code Cache-Control: private} — 로그인 뒤에만 보이는 자료다. 공용 프록시가
     *       캐시하면 다른 사용자에게 그대로 나간다
     * </ul>
     */
    @GetMapping("/{productModelId}/thumbnail")
    public ResponseEntity<Resource> thumbnail(@PathVariable Long productModelId) {
        FileContent file = productImageService.readThumbnail(productModelId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.size())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=0, no-store")
                .header("X-Content-Type-Options", "nosniff")
                // InputStreamResource 는 한 번만 읽힌다. Spring 이 응답으로 흘린 뒤 닫아 준다.
                .body(new InputStreamResource(file.stream()));
    }

    /**
     * JWT {@code sub} 는 {@code JwtTokenService} 가 {@code String.valueOf(user.getId())} 로 넣는다.
     * 형식이 깨진 토큰은 여기 오기 전에 {@code JwtDecoder} 가 거부한다.
     */
    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
