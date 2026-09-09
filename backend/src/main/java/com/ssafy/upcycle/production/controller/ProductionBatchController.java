package com.ssafy.upcycle.production.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.production.dto.ProductionBatchOpenRequest;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.dto.ProductionInputRequest;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 생산 배치와 소재 연결. <b>인증이 필요하며 {@code BRAND} 만 쓸 수 있다.</b>
 *
 * <p>확정은 여기 없다. 확정은 소재를 소비하고 개체를 만들고 온체인 기록까지 남기는
 * 되돌릴 수 없는 행위라 별도 티켓이며, 그때 {@code POST /{id}/confirm} 이 붙는다.
 *
 * <p>역할 검사를 {@code @PreAuthorize} 로 하지 않는 이유는 제품 등록과 같다 — 토큰의 역할
 * 클레임은 발급 시점의 스냅샷이라 정지되거나 역할이 바뀐 계정이 만료 전까지 통과한다.
 */
@RestController
@RequestMapping("/api/v1/production-batches")
@RequiredArgsConstructor
public class ProductionBatchController {

    private final ProductionBatchService productionBatchService;

    /**
     * 배치를 연다. 생산자와 서명 지갑은 JWT 와 등록된 활성 지갑에서 가져온다 —
     * 바디로 받으면 남의 이름과 주소로 생산할 수 있다.
     */
    @PostMapping
    public ApiResponse<ProductionBatchResponse> open(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProductionBatchOpenRequest request) {
        return ApiResponse.success(productionBatchService.open(userId(jwt), request));
    }

    /**
     * 소재를 배치에 연결한다. <b>연결과 동시에 그 수량이 잠긴다.</b>
     *
     * <p>응답은 배치 전체다 — 화면이 투입 목록과 합계를 함께 다시 그리므로 한 번에 준다.
     */
    @PostMapping("/{batchId}/inputs")
    public ApiResponse<ProductionBatchResponse> addInput(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long batchId,
            @Valid @RequestBody ProductionInputRequest request) {
        return ApiResponse.success(productionBatchService.addInput(userId(jwt), batchId, request));
    }

    /** 투입을 취소한다. 잠금이 함께 풀려 수량이 돌아온다. */
    @DeleteMapping("/{batchId}/inputs/{inputId}")
    public ApiResponse<ProductionBatchResponse> removeInput(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long batchId,
            @PathVariable Long inputId) {
        return ApiResponse.success(
                productionBatchService.removeInput(userId(jwt), batchId, inputId));
    }

    /** 배치 상세. 투입 목록을 함께 담는다. */
    @GetMapping("/{batchId}")
    public ApiResponse<ProductionBatchResponse> findDetail(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long batchId) {
        return ApiResponse.success(productionBatchService.findDetail(userId(jwt), batchId));
    }

    /**
     * 내 배치 목록.
     *
     * <p>{@code /me} 가 {@code batchId} 로 잘못 바인딩되면 400 이 되므로 테스트로 고정한다 —
     * Spring MVC 는 리터럴 경로를 우선하지만 그 동작에 기대지 않는다.
     */
    @GetMapping("/me")
    public ApiResponse<List<ProductionBatchResponse>> findMyBatches(
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(productionBatchService.findMyBatches(userId(jwt)));
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
