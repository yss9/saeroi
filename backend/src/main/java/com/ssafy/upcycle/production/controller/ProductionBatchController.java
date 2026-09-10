package com.ssafy.upcycle.production.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.production.dto.ProductionBatchOpenRequest;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.dto.ProductionInputsRequest;
import com.ssafy.upcycle.production.dto.ProductionChainLinkRequest;
import com.ssafy.upcycle.production.dto.ProductionItemResponse;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import com.ssafy.upcycle.production.service.ProductionChainLinkService;
import com.ssafy.upcycle.production.service.ProductionConfirmService;
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
 * <p>확정({@code POST /{id}/confirm})은 소재를 소비하고 개체를 만드는 <b>되돌릴 수 없는</b>
 * 행위다. 확정된 배치는 취소할 수 없다.
 *
 * <p>역할 검사를 {@code @PreAuthorize} 로 하지 않는 이유는 제품 등록과 같다 — 토큰의 역할
 * 클레임은 발급 시점의 스냅샷이라 정지되거나 역할이 바뀐 계정이 만료 전까지 통과한다.
 */
@RestController
@RequestMapping("/api/v1/production-batches")
@RequiredArgsConstructor
public class ProductionBatchController {

    private final ProductionBatchService productionBatchService;
    private final ProductionConfirmService productionConfirmService;
    private final ProductionChainLinkService productionChainLinkService;

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
     * <p><b>목록으로 받고 전부 성공하거나 전부 실패한다.</b> 한 건씩 받으면 중간에 실패했을
     * 때 앞의 것들이 잠긴 채 남고, 프론트가 어디까지 성공했는지 추적해 되돌려야 한다.
     * 단건은 원소 하나짜리 목록으로 보내면 된다.
     *
     * <p>응답은 배치 전체다 — 화면이 투입 목록과 합계를 함께 다시 그리므로 한 번에 준다.
     */
    @PostMapping("/{batchId}/inputs")
    public ApiResponse<ProductionBatchResponse> addInputs(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long batchId,
            @Valid @RequestBody ProductionInputsRequest request) {
        return ApiResponse.success(productionBatchService.addInputs(userId(jwt), batchId, request));
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

    /**
     * 배치를 확정한다. <b>되돌릴 수 없다.</b>
     *
     * <p>여기서 소재가 소비되고 제품 개체가 만들어진다. 자투리가 있으면 새 소재로 발행되고
     * 혈통이 이어지며, 업사이클 비율이 계산되어 배치에 굳는다.
     *
     * <p>바디가 없다 — 확정에 필요한 것은 전부 이미 배치에 있다. 수량이나 비율을 여기서
     * 받으면 실제 투입과 다른 값을 적어 넣을 수 있고, 그 값이 그대로 제품에 붙는다.
     */
    @PostMapping("/{batchId}/confirm")
    public ApiResponse<ProductionBatchResponse> confirm(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long batchId) {
        return ApiResponse.success(productionConfirmService.confirm(userId(jwt), batchId));
    }

    /**
     * 배치를 취소한다. <b>작성 중인 배치만 가능하다.</b>
     *
     * <p>남은 투입의 잠금이 함께 풀려 소재 수량이 돌아온다. 투입 행은 이력으로 남는다.
     *
     * <p>확정된 배치는 취소할 수 없다 — 소재는 이미 소비됐고 개체는 이미 만들어졌다.
     */
    @PostMapping("/{batchId}/cancel")
    public ApiResponse<ProductionBatchResponse> cancel(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long batchId) {
        return ApiResponse.success(productionBatchService.cancel(userId(jwt), batchId));
    }

    /**
     * 확정으로 만들어진 제품 개체 목록. <b>QR 라벨 인쇄가 쓴다.</b>
     *
     * <p>{@code qrToken} 이 실려 나가므로 배치 소유자만 볼 수 있다. 그 토큰은 인증 없이
     * 열리는 조회 경로의 열쇠이고, 브랜드는 그것을 제품에 인쇄해야 한다.
     *
     * <p>확정 전 배치는 빈 목록이다 — 오류가 아니다. 개체는 확정이 만든다.
     */
    @GetMapping("/{batchId}/items")
    public ApiResponse<List<ProductionItemResponse>> findItems(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long batchId) {
        return ApiResponse.success(productionBatchService.findItems(userId(jwt), batchId));
    }

    /**
     * 온체인 기록 결과를 연결한다. <b>확정된 배치만, 생산자 본인만</b> 부를 수 있다.
     *
     * <p>화면이 지갑으로 {@code ProductionRegistry} 를 호출하고 영수증을 받은 뒤 그 결과를
     * 여기로 알려 준다. 서버가 트랜잭션을 보내지 않는 이유는 서명이 브랜드 지갑의 것이고
     * 서버에 개인키가 없기 때문이다 — 소재·거래와 같다.
     *
     * <p><b>체인 ID 와 컨트랙트 주소는 받지 않는다</b> — 서버 설정이 정본이다.
     *
     * <p>온체인 번호도 받지 않는다. {@code production_batch} 에 그 값을 담을 컬럼이 없어서,
     * 받아도 넣을 자리가 없다. 대신 {@code evidenceHash} 와 {@code manifestUrl} 이 배치에
     * 붙는다.
     */
    @PostMapping("/{batchId}/chain")
    public ApiResponse<Void> linkOnChain(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long batchId,
            @Valid @RequestBody ProductionChainLinkRequest request) {
        productionChainLinkService.link(userId(jwt), batchId, request);
        return ApiResponse.success(null);
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
