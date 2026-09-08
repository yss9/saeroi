package com.ssafy.upcycle.product.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.product.dto.ProductImageUploadResponse;
import com.ssafy.upcycle.product.service.ProductImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 제품 이미지 업로드. <b>인증이 필요하며 {@code BRAND} 만 올릴 수 있다.</b>
 *
 * <p>등록과 나뉘어 있는 이유는 저장 경로가 양수 id 를 요구하는데 썸네일은 제품이 생기기
 * 전에 필요하기 때문이다 ({@link ProductImageService} 주석). 브랜드에 딸린 파일로 먼저
 * 저장하고, 등록 요청이 돌려받은 키를 {@code thumbnailUrl} 에 넣는다.
 *
 * <p><b>업로드에도 자격을 본다.</b> 등록에서만 걸러도 파일은 이미 디스크에 있다 —
 * 열어 두면 아무 계정이나 우리 저장소에 파일을 쌓을 수 있다.
 *
 * <p>파일 <b>한 장</b>만 받는다. {@code product_model.thumbnail_url} 이 하나뿐이라 여러 장을
 * 받아도 하나만 쓰이고, 나머지는 아무도 가리키지 않는 채 남는다.
 */
@RestController
@RequestMapping("/api/v1/product-images")
@RequiredArgsConstructor
public class ProductImageController {

    private final ProductImageService productImageService;

    /**
     * 이미지 한 장을 올리고 저장 키를 돌려준다.
     *
     * <p>응답의 {@code storageKey} 는 <b>화면에 붙일 주소가 아니다.</b> 등록이 끝난 뒤
     * {@code GET /api/v1/product-models/{id}/thumbnail} 이 이미지를 준다. 등록 전 미리보기는
     * 사용자가 방금 고른 파일을 브라우저에서 그대로 보여 주면 된다 — 서버를 거칠 필요가 없다.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProductImageUploadResponse> upload(
            @AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(productImageService.upload(userId(jwt), file));
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
