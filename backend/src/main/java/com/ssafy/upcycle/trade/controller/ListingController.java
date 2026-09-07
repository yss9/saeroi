package com.ssafy.upcycle.trade.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.trade.dto.ListingRegisterRequest;
import com.ssafy.upcycle.trade.dto.ListingResponse;
import com.ssafy.upcycle.trade.dto.ListingSearchCondition;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.trade.service.ListingCommandService;
import com.ssafy.upcycle.trade.service.ListingQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Marketplace 판매 글. <b>인증이 필요하다.</b>
 *
 * <p><b>경로에 {@code /v1} 이 있다.</b> 같은 도메인인 {@code TradeController} 가
 * {@code /api/v1/trades} 라 맞췄다 — 판매 글과 거래가 한 화면에서 오가는데 경로 관례가
 * 갈리면 프론트가 매번 어느 쪽인지 확인해야 한다.
 *
 * <p>{@code MaterialController} 는 {@code /api/materials} 로 무버전이다.
 * <b>프로젝트 전체가 아직 한 관례로 통일되지 않았다</b> — 통일은 프론트와 같은 주기에 한 번에 한다.
 *
 * <p>{@code SecurityConfig} 를 고치지 않았다. permitAll 목록에 없으므로
 * {@code anyRequest().authenticated()} 로 떨어져 <b>아무것도 하지 않아도 토큰이 필요하다.</b>
 * 목록 조회까지 인증을 요구하는 것은 의도적이다 — 소재 시세와 재고는 사업자 간 거래 정보이고,
 * 공개 조회가 필요한 것은 Product Passport 쪽이다.
 *
 * <p>{@code ApiResponse} 를 직접 돌려주고 실패는 {@code BusinessException} 으로 던진다.
 * 상태코드는 {@code GlobalExceptionHandler} 한 곳에서 정한다.
 */
@RestController
@RequestMapping("/api/v1/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingCommandService listingCommandService;
    private final ListingQueryService listingQueryService;

    /**
     * 판매 등록. <b>{@code SUPPLIER} 만 가능하다.</b>
     *
     * <p>{@code BRAND} 는 소재를 사서 제품을 만드는 쪽이라 판매자가 되지 않는다.
     * 소재를 등록하는 것({@code SUPPLIER}·{@code BRAND})과 파는 것은 다른 권한이다.
     *
     * <p>판매자는 JWT {@code sub} 에서 얻는다 — 요청 바디로 받으면 남의 이름으로 남의 소재를
     * 올릴 수 있다.
     */
    @PostMapping
    public ApiResponse<ListingResponse> register(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ListingRegisterRequest request) {
        return ApiResponse.success(listingCommandService.register(userId(jwt), request));
    }

    /**
     * Marketplace 목록. 검색·필터·정렬·페이지가 모두 쿼리 파라미터다.
     *
     * <p><b>Brand Market 과 Supplier Market 이 같은 엔드포인트다.</b> 내 판매 글만 보려면
     * {@code sellerId} 를 준다. 화면이 다르다고 엔드포인트를 나누면 필터 하나 추가할 때마다
     * 두 곳을 고쳐야 한다.
     *
     * <p>{@code sellerId} 를 서버가 강제하지 않는 것에 주의한다. 남의 판매 목록을 보는 것은
     * 막을 이유가 없다 — 어차피 전체 목록에 다 나와 있는 정보이고, 판매자별 보기는
     * 필터일 뿐 권한이 아니다.
     */
    @GetMapping
    public ApiResponse<PageResponse<ListingResponse>> search(
            @ModelAttribute ListingSearchCondition condition) {
        return ApiResponse.success(listingQueryService.search(condition));
    }

    /** 판매 글 상세. 소재 정보를 함께 담아 화면이 두 번 호출하지 않게 한다. */
    @GetMapping("/{listingId}")
    public ApiResponse<ListingResponse> findDetail(@PathVariable Long listingId) {
        return ApiResponse.success(listingQueryService.findDetail(listingId));
    }

    /**
     * 판매 취소. 본인 글만, 진행 중인 거래가 없을 때만 가능하다.
     *
     * <p><b>{@code DELETE} 지만 행을 지우지 않는다.</b> 상태를 {@code CANCELED} 로 바꾼다 —
     * 이미 성사된 거래들이 이 판매 글을 {@code listing_id} 로 참조하고 있어 지울 수 없고,
     * 지워서도 안 된다 (거래 이력이 어디서 시작됐는지가 추적의 일부다).
     *
     * <p>수정 API 가 없는 것은 <b>티켓에 없어서가 아니라 스키마가 지원하지 않기 때문</b>이다.
     * {@code material_listing} 에 {@code updated_at} 이 없고, 단가를 바꾸면 그 전에 성사된
     * 거래의 금액 근거가 사라진다. 판매 조건을 바꾸려면 취소 후 재등록이 맞고, 그렇게 하면
     * 거래마다 어떤 조건이었는지가 판매 글 단위로 남는다.
     */
    @DeleteMapping("/{listingId}")
    public ApiResponse<ListingResponse> cancel(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long listingId) {
        return ApiResponse.success(listingCommandService.cancel(userId(jwt), listingId));
    }

    /**
     * JWT {@code sub} 는 {@code JwtTokenService} 가 {@code String.valueOf(user.getId())} 로 넣는다.
     * 형식이 깨진 토큰은 여기 오기 전에 {@code JwtDecoder} 가 거부한다.
     */
    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
