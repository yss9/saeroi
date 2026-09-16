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

    @GetMapping
    public ApiResponse<PageResponse<PublicProductModelResponse>> search(
            @ModelAttribute PublicProductModelSearchCondition condition) {
        return ApiResponse.success(publicProductCatalogService.search(condition));
    }

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
