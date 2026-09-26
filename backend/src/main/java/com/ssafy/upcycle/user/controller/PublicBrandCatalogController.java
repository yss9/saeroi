package com.ssafy.upcycle.user.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.common.storage.FileContent;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.user.dto.PublicBrandResponse;
import com.ssafy.upcycle.user.dto.PublicBrandSearchCondition;
import com.ssafy.upcycle.user.service.BrandLogoService;
import com.ssafy.upcycle.user.service.PublicBrandCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소비자 BRANDS 화면의 비로그인 브랜드 카탈로그.
 *
 * <p>공개 상품 카탈로그({@code /api/v1/public/product-models})와 같은 자리에 둔다 —
 * {@code /api/v1/public/**} 아래는 토큰 없이 열리는 경로다.
 *
 * <p><b>사업자 정보는 내려가지 않는다.</b> 사업자등록번호·주소·좌표는 공개 DTO 에 필드가
 * 아예 없다({@link PublicBrandResponse}). 사업자 본인 화면은 {@code /api/v1/companies/me} 가
 * 따로 있고 그쪽 응답은 그대로다.
 *
 * <p><b>브랜드별 상품 목록은 여기 없다.</b> {@code GET /api/v1/public/product-models?brandId=}
 * 가 정확히 같은 일을 하고 응답도 SHOP 과 같아, 화면이 상품 카드와 페이지네이션을 그대로
 * 재사용한다. 같은 일을 하는 입구를 둘 두면 나중에 한쪽만 고치게 된다.
 */
@RestController
@RequestMapping("/api/v1/public/brands")
@RequiredArgsConstructor
public class PublicBrandCatalogController {

    private final PublicBrandCatalogService publicBrandCatalogService;
    private final BrandLogoService brandLogoService;

    /**
     * 브랜드 목록.
     *
     * <p><b>판매 중인 공개 상품이 하나 이상인 브랜드만 나온다.</b> 상품이 없는 브랜드가
     * 목록에 뜨면 눌렀을 때 빈 화면이 되고, {@code ETC} 상품만 가진 브랜드도 같은 이유로
     * 나오지 않는다 — 카드에 적히는 {@code productCount} 도 같은 조건으로 센 값이다.
     *
     * <p>{@code keyword} 는 <b>화면에 보이는 브랜드명</b>을 찾는다. 정렬은
     * {@code sort=PRODUCT_COUNT|NAME} 이며 기본은 상품이 많은 순이다.
     */
    @GetMapping
    public ApiResponse<PageResponse<PublicBrandResponse>> search(
            @ModelAttribute PublicBrandSearchCondition condition) {
        return ApiResponse.success(publicBrandCatalogService.search(condition));
    }

    /** 브랜드 상세. 목록에 나오지 않는 브랜드는 여기서도 404 다. */
    @GetMapping("/{brandId}")
    public ApiResponse<PublicBrandResponse> findDetail(@PathVariable Long brandId) {
        return ApiResponse.success(publicBrandCatalogService.findDetail(brandId));
    }

    /**
     * 브랜드 로고.
     *
     * <p>브랜드가 <b>파일로 올린 로고</b>만 나간다. 업체 정보 수정 폼에 손으로 입력된 옛
     * 자유 문자열은 저장 키가 아니라 404 이고, 응답의 {@code logoUrl} 도 그때는
     * {@code null} 이라 화면이 이 주소를 부르지 않는다.
     *
     * <ul>
     *   <li>{@code Content-Disposition: inline} — 원본 파일명은 싣지 않는다. 한글·따옴표·
     *       개행이 들어간 이름은 헤더 인젝션이 되고 그 이름은 사용자가 정한 값이다
     *   <li>{@code X-Content-Type-Options: nosniff} — 화이트리스트로 svg 를 막는 노력이
     *       브라우저의 타입 추측으로 무의미해질 수 있다
     *   <li>{@code Cache-Control: public} — 비로그인에게 열린 자료라 공용 프록시가 캐시해도
     *       된다. 공개 상품 이미지와 같은 값이다
     * </ul>
     */
    @GetMapping("/{brandId}/logo")
    public ResponseEntity<Resource> logo(@PathVariable Long brandId) {
        FileContent file = brandLogoService.readPublicLogo(brandId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.size())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .header("X-Content-Type-Options", "nosniff")
                // InputStreamResource 는 한 번만 읽힌다. Spring 이 응답으로 흘린 뒤 닫아 준다.
                .body(new InputStreamResource(file.stream()));
    }
}
