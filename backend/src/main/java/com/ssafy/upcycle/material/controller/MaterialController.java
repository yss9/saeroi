package com.ssafy.upcycle.material.controller;

import com.ssafy.upcycle.blockchain.dto.ChainLinkRequest;
import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.material.dto.MaterialBalanceResponse;
import com.ssafy.upcycle.material.dto.MaterialDetailResponse;
import com.ssafy.upcycle.material.dto.MaterialLineageResponse;
import com.ssafy.upcycle.material.dto.MaterialRegisterRequest;
import com.ssafy.upcycle.material.dto.MaterialRegisterResponse;
import com.ssafy.upcycle.material.dto.MaterialSourceVerificationRequest;
import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.service.MaterialChainLinkService;
import com.ssafy.upcycle.material.service.MaterialLineageQueryService;
import com.ssafy.upcycle.material.service.MaterialQueryService;
import com.ssafy.upcycle.material.service.MaterialRegistrationService;
import com.ssafy.upcycle.material.service.MaterialSourceVerificationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소재 등록·조회. <b>인증이 필요하다.</b>
 *
 * <p><b>경로에 {@code /v1} 이 없다.</b> 지갑 API 는 {@code /api/v1/...} 인데 여기는
 * {@code /api/materials} 다. 티켓과 엔드포인트 목록이 무버전으로 적혀 있어 그쪽에 맞췄고,
 * 한 리소스를 두 관례로 쪼개지 않기 위해 소재 계열 전체가 무버전이다.
 * 전체 표면을 {@code /v1} 로 통일하는 것은 이메일 로그인 컨트롤러가 들어올 때
 * <b>프론트와 같은 주기에</b> 한 번에 한다 — 한쪽만 옮기면 깨진다.
 *
 * <p>{@code SecurityConfig} 는 고치지 않았다. permitAll 목록은
 * {@code /api/v1/auth/**} 와 {@code GET /api/health} 뿐이고 나머지는
 * {@code anyRequest().authenticated()} 로 떨어지므로, {@code /api/materials} 는
 * <b>아무것도 하지 않아도 토큰이 필요하다.</b> 이것이 두 엔드포인트가 원하는 동작이다.
 *
 * <p>{@code ApiResponse} 를 직접 돌려주고 실패는 {@code BusinessException} 으로 던진다
 * ({@code WalletAuthController} 와 같은 방식). 상태코드는
 * {@code GlobalExceptionHandler} 한 곳에서 정한다.
 */
@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialRegistrationService materialRegistrationService;
    private final MaterialQueryService materialQueryService;
    private final MaterialLineageQueryService materialLineageQueryService;
    private final MaterialChainLinkService materialChainLinkService;
    private final MaterialSourceVerificationService materialSourceVerificationService;

    /**
     * 소재 등록. {@code SUPPLIER}·{@code BRAND} 만 가능하다.
     *
     * <p>등록자는 JWT {@code sub} 에서 얻는다 — 요청 바디로 받으면 남의 계정 이름으로
     * 소재를 올릴 수 있다.
     */
    @PostMapping
    public ApiResponse<MaterialRegisterResponse> register(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MaterialRegisterRequest request) {
        return ApiResponse.success(materialRegistrationService.register(userId(jwt), request));
    }

    /**
     * 소재 상세. <b>인증만 요구하고 역할은 보지 않는다</b> — 소재 정보는 추적성이 목적이라
     * 누가 보든 같은 값이어야 한다.
     *
     * <p>{@code verificationState} 는 조회 시점에 계산된다 — 판정 범위는
     * {@link MaterialDetailResponse} 주석 참고.
     */
    @GetMapping("/{materialId}")
    public ApiResponse<MaterialDetailResponse> findDetail(@PathVariable Long materialId) {
        return ApiResponse.success(materialQueryService.findDetail(materialId));
    }

    /**
     * 내가 보유한 소재 목록. My Materials 화면이 쓴다.
     *
     * <p><b>이 매핑이 {@code /{materialId}} 보다 먼저 선언되어 있어야 하는 것은 아니다</b> —
     * Spring MVC 는 리터럴 경로를 경로 변수보다 우선한다. 그래도 {@code /me} 가
     * {@code materialId} 로 잘못 바인딩되면 400 이 되므로 테스트로 고정한다.
     *
     * <p>보유자는 JWT {@code sub} 에서 얻는다 — 경로나 쿼리로 받으면 남의 보유 현황을
     * 들여다볼 수 있다.
     */
    @GetMapping("/me")
    public ApiResponse<List<MyMaterialResponse>> findMyMaterials(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(materialQueryService.findMyMaterials(userId(jwt)));
    }

    /**
     * 한 소재의 보유자별 잔량.
     *
     * <p>응답의 {@code available} 은 {@code quantity} 에서 <b>기한이 남은 ACTIVE 잠금만</b>
     * 뺀 값이다. 만료된 잠금은 상태가 아직 {@code ACTIVE} 여도 차감하지 않는다
     * (도메인 규칙: *"전환 전이라도 available 계산에서 제외"*).
     * {@code syncedAt} 은 이 값이 언제 기준인지 알려준다 — 온체인 잔액 동기화가 아직
     * 없어서, 시각이 없으면 "보유자 없음" 과 "동기화 전" 을 구분할 수 없다.
     */
    @GetMapping("/{materialId}/balance")
    public ApiResponse<List<MaterialBalanceResponse>> findBalances(@PathVariable Long materialId) {
        return ApiResponse.success(materialQueryService.findBalances(materialId));
    }

    /**
     * 소재 혈통. 위로 조상, 아래로 자손을 한 번에 돌려준다.
     *
     * <p>스토리 {@code -23} 이 부모 조회·자식 조회·<b>최초 Source 까지 역추적</b> 셋을
     * 요구하므로 방향을 나누지 않았다. 두 엔드포인트로 쪼개면 화면이 두 번 부르고
     * 그 사이 상한 계산이 서로 어긋난다.
     *
     * <p><b>응답의 {@code truncated} 를 반드시 확인할 것.</b> 상한(깊이 5 · 노드 200)에
     * 걸리면 일부만 담기고 {@code message} 에 안내 문구가 온다. 그걸 표시하지 않으면
     * 잘린 이력이 전체로 보인다 — 추적성 기능에서 가장 나쁜 거짓이다.
     */
    @GetMapping("/{materialId}/lineage")
    public ApiResponse<MaterialLineageResponse> findLineage(@PathVariable Long materialId) {
        return ApiResponse.success(materialLineageQueryService.findLineage(materialId));
    }

    /**
     * JWT {@code sub} 는 {@code JwtTokenService} 가 {@code String.valueOf(user.getId())} 로 넣는다.
     * 형식이 깨진 토큰은 여기 오기 전에 {@code JwtDecoder} 가 거부하므로 파싱 실패는 예상하지 않는다.
     */
    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    /**
     * 온체인 발행 결과를 연결한다. <b>등록자 본인만</b> 부를 수 있다.
     *
     * <p>화면이 지갑으로 {@code MaterialToken.mintMaterial} 을 보내고 영수증을 받은 뒤
     * {@code MaterialMinted} 이벤트의 {@code tokenId} 를 여기로 알려 준다. 서버가 mint 를
     * 보내지 않는 이유는 서명이 공급자 지갑의 것이고 서버에 개인키가 없기 때문이다.
     *
     * <p><b>체인 ID 와 컨트랙트 주소는 받지 않는다</b> — 서버 설정이 정본이다.
     */
    @PostMapping("/{materialId}/chain")
    public ApiResponse<Void> linkOnChain(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long materialId,
            @Valid @RequestBody ChainLinkRequest request) {
        materialChainLinkService.link(userId(jwt), materialId, request);
        return ApiResponse.success(null);
    }

    /**
     * 출처 검증 확정을 연결한다. <b>등록자 본인만</b> 부를 수 있다.
     *
     * <p>화면이 지갑으로 {@code MaterialToken.verifySource(tokenId, evidenceHash)} 를 보내고
     * 영수증을 받은 뒤 그 결과(Tx 해시)를 여기로 알려 준다. mint 확정과 달리 새로 채번되는
     * 값이 없어 {@code onChainId} 를 받지 않는다 — {@link MaterialSourceVerificationRequest}
     * 주석 참고.
     *
     * <p>이 호출은 {@code material} 을 바꾸지 않는다. {@code SOURCE_VERIFIED} 전환은
     * {@code MaterialVerificationStateResolver} 가 다음 조회부터 계산해 낸다.
     */
    @PostMapping("/{materialId}/source-verification")
    public ApiResponse<Void> confirmSourceVerification(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long materialId,
            @Valid @RequestBody MaterialSourceVerificationRequest request) {
        materialSourceVerificationService.confirm(userId(jwt), materialId, request);
        return ApiResponse.success(null);
    }
}
