package com.ssafy.upcycle.product.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.common.storage.FileContent;
import com.ssafy.upcycle.product.dto.PublicProductModelResponse;
import com.ssafy.upcycle.product.dto.PublicProductModelSearchCondition;
import com.ssafy.upcycle.product.service.PublicProductCatalogService;
import com.ssafy.upcycle.trade.dto.PageResponse;
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
    public ResponseEntity<Resource> thumbnail(@PathVariable Long productModelId) {
        FileContent file = publicProductCatalogService.readThumbnail(productModelId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.size())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(file.stream()));
    }
}
