package com.ssafy.upcycle.product.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.common.storage.FileContent;
import com.ssafy.upcycle.product.dto.PublicProductModelResponse;
import com.ssafy.upcycle.product.dto.PublicProductModelSearchCondition;
import com.ssafy.upcycle.product.service.PublicProductCatalogService;
import com.ssafy.upcycle.trade.dto.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
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

/** 소비자 쇼핑몰의 비로그인 상품 카탈로그. */
@RestController
@RequestMapping("/api/v1/public/product-models")
@RequiredArgsConstructor
public class PublicProductCatalogController {

    private final PublicProductCatalogService publicProductCatalogService;

    /**
     * 소비자 SHOP 목록.
     *
     * <p>필터는 {@code category}·{@code subcategory}·{@code brandId} 이고 함께 주면
     * 교집합이다. 정렬은 {@code sort=LATEST|PRICE_ASC|PRICE_DESC} 이며 기본은 최신순이다.
     *
     * <p><b>SEARCH 페이지도 이 경로를 쓴다.</b> {@code keyword} 는 상품명 또는 브랜드명에
     * 들어간 말을 찾고, 다른 필터와 함께 주면 역시 교집합이다. 브랜드명은 <b>화면에 보이는
     * 이름</b> 기준이다 — 회사명이 있으면 회사명, 없으면 사용자 이름. 검색어가 없거나
     * 공백뿐이면 조건이 붙지 않아 평소 목록과 같다.
     *
     * <p>검색을 별도 엔드포인트로 두지 않은 이유는 지켜야 할 규칙이 같기 때문이다 —
     * {@code ON_SALE} 만, {@code ETC} 제외, 같은 페이지 봉투. 두 벌로 적으면 그 중 하나가
     * 언젠가 한쪽에서만 빠진다.
     *
     * <p><b>{@code status} 는 받지 않는다</b> — 공개 목록은 언제나 판매 중인 상품만이다.
     * <b>{@code ETC} 분류는 어떤 조건에서도 나오지 않는다</b>; 걸러내는 자리는 목록 질의라
     * 페이지 수와 총 개수가 어긋나지 않는다.
     */
    @GetMapping
    public ApiResponse<PageResponse<PublicProductModelResponse>> search(
            @ModelAttribute PublicProductModelSearchCondition condition) {
        return ApiResponse.success(publicProductCatalogService.search(condition));
    }

    /**
     * 소비자 상품 상세.
     *
     * <p>목록에 나오지 않는 상품은 여기서도 나오지 않는다 — 판매 중이 아니거나
     * {@code ETC} 분류면 404 다. 상세만 열리면 그 화면의 구매 버튼까지 함께 열린다.
     */
    @GetMapping("/{productModelId}")
    public ApiResponse<PublicProductModelResponse> findDetail(
            @PathVariable Long productModelId) {
        return ApiResponse.success(publicProductCatalogService.findDetail(productModelId));
    }

    @GetMapping("/{productModelId}/thumbnail")
    public ResponseEntity<Resource> thumbnail(
            @PathVariable Long productModelId,
            HttpServletRequest request) {
        PublicProductCatalogService.PublicImageContent imageContent =
                publicProductCatalogService.readThumbnail(productModelId);
        String etag = generateETag(imageContent.storageKey());
        return imageWithCache(imageContent.content(), etag, request);
    }

    /**
     * 상품 상세 갤러리의 사진 한 장.
     *
     * <p>상세와 같은 관문을 지난다 — 판매 중이 아니거나 소비자에게 감추는 분류면 404 다.
     *
     * <p>대표 이미지와 달리 <b>제품마다 여러 장</b>이라 이미지 id 를 함께 받는다. 그 id 가
     * 이 제품의 것이 아니면 404 다 — 다른 제품의 사진을 남의 주소로 열 수 있게 되면 그
     * 주소가 그대로 공유된다.
     */
    @GetMapping("/{productModelId}/images/{productImageId}")
    public ResponseEntity<Resource> image(
            @PathVariable Long productModelId,
            @PathVariable Long productImageId,
            HttpServletRequest request) {
        PublicProductCatalogService.PublicImageContent imageContent =
                publicProductCatalogService.readImage(productModelId, productImageId);
        String etag = generateETag(imageContent.storageKey());
        return imageWithCache(imageContent.content(), etag, request);
    }

    /**
     * 공개 이미지 응답. 대표 이미지와 갤러리가 같은 헤더를 쓴다.
     *
     * <ul>
     *   <li>{@code ETag} — storageKey 기반, 재업로드 시 자동 무효화
     *   <li>{@code Cache-Control: public, max-age=86400, immutable} — 1일 캐시, 불변 표시
     *   <li>{@code 304 Not Modified} — If-None-Match 일치 시 본문 없이 반환
     *   <li>{@code Content-Disposition: inline} — 사진은 화면에 그린다
     *   <li>{@code X-Content-Type-Options: nosniff} — 타입 추측 방지
     * </ul>
     */
    private ResponseEntity<Resource> imageWithCache(
            FileContent file, String etag, jakarta.servlet.http.HttpServletRequest request) {
        String ifNoneMatch = request.getHeader("If-None-Match");
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304).build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.size())
                .header(HttpHeaders.ETAG, etag)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400, immutable")
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(file.stream()));
    }

    private String generateETag(String storageKey) {
        // storageKey는 업로드마다 변하므로 이미지 재업로드 시 자동으로 캐시 무효화됨
        return "\"" + storageKey.hashCode() + "\"";
    }
}
