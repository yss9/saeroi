package com.ssafy.upcycle.product.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.common.storage.FileContent;
import com.ssafy.upcycle.product.dto.ProductImageResponse;
import com.ssafy.upcycle.product.dto.ProductModelImagesReplaceRequest;
import com.ssafy.upcycle.product.service.ProductModelImageService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 브랜드가 관리하는 상품 상세 갤러리. <b>인증이 필요하고 본인 제품만 다룰 수 있다.</b>
 *
 * <p>대표 이미지({@code thumbnail})와는 다른 것이다. 그쪽은 목록 카드가 쓰는 한 장이고
 * 여기는 상세 화면이 넘겨 보는 묶음이라, 갤러리를 비워도 목록은 그대로 그려진다.
 *
 * <p>파일 자체는 {@code POST /api/v1/product-images} 가 받는다. 여기서는 그 응답이 준
 * 저장 키를 순서대로 엮을 뿐이다 — 등록 요청이 {@code thumbnailUrl} 을 다루는 방식과 같다.
 */
@RestController
@RequestMapping("/api/v1/product-models/{productModelId}/images")
@RequiredArgsConstructor
public class ProductModelImageController {

    private final ProductModelImageService productModelImageService;

    /** 내 상품의 갤러리. 판매 상태와 무관하게 보이므로 {@code DRAFT} 도 확인할 수 있다. */
    @GetMapping
    public ApiResponse<List<ProductImageResponse>> findGallery(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long productModelId) {
        return ApiResponse.success(
                productModelImageService.findManagementGallery(userId(jwt), productModelId));
    }

    /**
     * 갤러리를 통째로 바꾼다. <b>보낸 목록이 곧 갤러리 전체다.</b>
     *
     * <p>{@code PUT} 인 것은 부분 수정이 아니기 때문이다. 순서는 배열의 순서이고, 빈 배열은
     * 갤러리를 비운다. 추가·삭제·순서변경을 따로 열면 세 요청이 섞이는 사이 자리가
     * 겹치거나 비는 구간이 생긴다 ({@link ProductModelImagesReplaceRequest} 주석).
     */
    @PutMapping
    public ApiResponse<List<ProductImageResponse>> replaceGallery(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long productModelId,
            @Valid @RequestBody ProductModelImagesReplaceRequest request) {
        return ApiResponse.success(
                productModelImageService.replace(userId(jwt), productModelId, request));
    }

    /**
     * 갤러리 사진 한 장. 브랜드 관리 화면이 쓴다.
     *
     * <p><b>저장 키가 아니라 이미지 id 로 받는다</b> — 키를 URL 에 넣게 하면 {@code ..} 나
     * 남의 키를 찔러 볼 수 있고, 그 검사는 틀리기 쉽다. 대표 이미지 조회와 같은 판단이다.
     *
     * <p>{@code Cache-Control: private} — 아직 공개하지 않은 제품의 사진일 수 있다. 공용
     * 프록시가 캐시하면 다른 사용자에게 그대로 나간다. 공개용은 소비자 쪽 경로가 따로 있다.
     */
    @GetMapping("/{productImageId}")
    public ResponseEntity<Resource> image(
            @PathVariable Long productModelId, @PathVariable Long productImageId) {
        FileContent file = productModelImageService.readImage(productModelId, productImageId);
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

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
