package com.ssafy.upcycle.passport.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.common.storage.FileContent;
import com.ssafy.upcycle.passport.dto.PassportResponse;
import com.ssafy.upcycle.passport.service.PassportQueryService;
import com.ssafy.upcycle.product.service.ProductImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * QR Passport 공개 조회 (FR-051). <b>인증이 없다.</b>
 *
 * <p>{@code SecurityConfig} 의 permitAll 목록에 {@code GET /api/v1/qr/**} 를 추가해야
 * 이 경로가 401 없이 응답한다 — 그 변경 없이는 이 컨트롤러가 있어도 막혀 있다.
 *
 * <p>비회원이 QR 을 찍고 바로 들어오는 진입점이라 토큰·역할을 요구하면 서비스 목적이
 * 무너진다. 대신 {@code qrToken} 자체가 추측 불가능한 값이라 접근 통제 역할을 한다
 * ({@code ProductItem} 참고).
 */
@RestController
@RequestMapping("/api/v1/qr")
@RequiredArgsConstructor
public class PassportController {

    private final PassportQueryService passportQueryService;
    private final ProductImageService productImageService;

    @GetMapping("/{qrToken}")
    public ApiResponse<PassportResponse> findByQrToken(@PathVariable String qrToken) {
        return ApiResponse.success(passportQueryService.findByQrToken(qrToken));
    }

    /**
     * 제품 대표 이미지. {@code ProductModelController.thumbnail} 과 같은 파일을 돌려주지만
     * 이 경로는 <b>인증이 없다</b> — 같은 제품이라도 마켓용 썸네일 엔드포인트는 로그인을
     * 요구해서 QR 로 들어온 비회원은 쓸 수 없다.
     *
     * <p>{@code Cache-Control} 이 마켓용과 다르다. 그쪽은 로그인 사용자별 응답이라
     * {@code private, no-store} 인데, 여기는 로그인 여부와 무관하게 누구에게나 같은
     * 바이트라 공용 캐시가 저장해도 안전하다.
     */
    @GetMapping("/{qrToken}/thumbnail")
    public ResponseEntity<Resource> thumbnail(@PathVariable String qrToken) {
        Long productModelId = passportQueryService.resolveProductModelId(qrToken);
        FileContent file = productImageService.readThumbnail(productModelId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.size())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(file.stream()));
    }
}
