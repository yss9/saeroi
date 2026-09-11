package com.ssafy.upcycle.production.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.production.dto.ProductionBatchOpenRequest;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.dto.ProductionInputsRequest;
import com.ssafy.upcycle.production.dto.ProductionChainBatchRequest;
import com.ssafy.upcycle.production.dto.ProductionChainLinkRequest;
import com.ssafy.upcycle.production.dto.ProductionConsumeChainRequest;
import com.ssafy.upcycle.production.dto.ProductionItemResponse;
import com.ssafy.upcycle.production.dto.ProductionItemsChainRequest;
import com.ssafy.upcycle.production.dto.ProductionManifestResponse;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import com.ssafy.upcycle.production.service.ProductionChainLinkService;
import com.ssafy.upcycle.production.service.ProductionConsumeChainService;
import com.ssafy.upcycle.production.service.ProductionManifestService;
import com.ssafy.upcycle.production.service.ProductionConfirmService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final ProductionConsumeChainService productionConsumeChainService;
    private final ProductionManifestService productionManifestService;

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
     * 온체인에 올릴 manifest 와 그 해시. <b>화면은 이 해시를 그대로 서명에 넣는다.</b>
     *
     * <p>직접 계산하지 않는다 — 계산 주체가 둘이면 필드 순서·공백·인코딩 차이로 같은
     * 배치에서 브라우저마다 다른 해시가 나오고, 그러면 나중에 재현해 대조할 수 없다.
     * 추적성의 마지막 한 칸이 그 대조에 걸려 있다.
     *
     * <p>내용도 서버가 이미 갖고 있다 — 투입·개체·비율은 확정 시점에 굳은 값이다.
     */
    @GetMapping("/{batchId}/chain/manifest")
    public ApiResponse<ProductionManifestResponse> manifest(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long batchId) {
        return ApiResponse.success(productionManifestService.build(userId(jwt), batchId));
    }

    /**
     * {@code createBatch} 결과를 연결한다. <b>온체인 4단계 중 첫 번째다.</b>
     *
     * <pre>
     *   createBatch        → POST /{id}/chain/batch     ← 여기
     *   submitPlanAndLock  → POST /{id}/chain
     *   confirmProduction  → POST /{id}/chain/consume
     * </pre>
     *
     * <p>온체인 배치 번호를 서버가 받아 두는 이유는 뒤의 단계가 모두 그 번호를 인자로 받기
     * 때문이다. 화면이 붙들고 있으면 새로고침 한 번에 잃어버린다.
     */
    @PostMapping("/{batchId}/chain/batch")
    public ApiResponse<Void> linkChainBatch(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long batchId,
            @Valid @RequestBody ProductionChainBatchRequest request) {
        productionChainLinkService.linkChainBatch(userId(jwt), batchId, request);
        return ApiResponse.success(null);
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
     * <p>{@code evidenceHash} 는 {@code GET /{id}/chain/manifest} 가 준 값이어야 한다 —
     * 다르면 화면이 서버가 모르는 내용을 체인에 올린 것이고, 그 해시는 재현할 수 없다.
     *
     * <p>{@code createBatch} 를 먼저 연결해야 한다. 그 번호 없이는 {@code submitPlanAndLock}
     * 을 부를 인자가 없다.
     */
    @PostMapping("/{batchId}/chain")
    public ApiResponse<Void> linkOnChain(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long batchId,
            @Valid @RequestBody ProductionChainLinkRequest request) {
        productionChainLinkService.link(userId(jwt), batchId, request);
        return ApiResponse.success(null);
    }

    /**
     * 소재 소각의 온체인 결과를 연결한다. <b>확정된 배치만, 생산자 본인만, 한 번만.</b>
     *
     * <p>확정은 소재를 DB 에서만 줄인다. 온체인 토큰은 그대로 남아 있어서, 체인만 보면
     * 브랜드가 여전히 그 소재를 갖고 있는 것처럼 보인다 — 같은 소재로 두 번 업사이클했다고
     * 주장할 수 있게 된다. 화면이 {@code consumeLockedMaterial} 로 소각한 뒤 결과를 여기로
     * 알려 주면 두 쪽이 맞춰진다.
     *
     * <p><b>투입 전부를 보고해야 한다.</b> 확정이 투입 전체를 한꺼번에 소비하므로, 일부만
     * 소각하고 보고하면 체인에 남아 있는 소재가 생긴다.
     *
     * <p>온체인 잠금 번호({@code chainLockId})가 이때 DB 에 붙는다 — 그 값을 채우는 경로가
     * 지금까지 없었다.
     */
    @PostMapping("/{batchId}/chain/consume")
    public ApiResponse<Void> linkConsume(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long batchId,
            @Valid @RequestBody ProductionConsumeChainRequest request) {
        productionConsumeChainService.linkConsume(userId(jwt), batchId, request);
        return ApiResponse.success(null);
    }

    /**
     * {@code linkProductItems} 결과를 연결한다. <b>온체인 4단계 중 마지막이다.</b>
     *
     * <p>개체 번호는 요청으로 받지 않는다 — 컨트랙트가 받는 {@code productItemIds[]} 는 우리
     * {@code product_item.id} 를 그대로 쓰고, 온체인에서 따로 채번하지 않는다. 화면은
     * {@code GET /{id}/items} 의 {@code productItemId} 를 {@code serialNumber} 순서 그대로
     * 보내면 된다.
     */
    @PostMapping("/{batchId}/chain/items")
    public ApiResponse<Void> linkChainItems(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long batchId,
            @Valid @RequestBody ProductionItemsChainRequest request) {
        productionChainLinkService.linkProductItems(userId(jwt), batchId, request);
        return ApiResponse.success(null);
    }

    /**
     * 개체의 QR 이미지. <b>라벨 인쇄가 쓴다.</b>
     *
     * <p>개체를 {@code qrToken} 이 아니라 id 로 받는다 — 토큰으로 받으면 경로가 공개 열쇠를
     * 그대로 드러내고, 아무나 남의 QR 이미지를 만들어낼 수 있다. 배치 소유자만 자기 개체의
     * 라벨을 뽑는다.
     *
     * <p><b>캐시를 길게 잡는다.</b> 토큰이 바뀌지 않는 한 같은 개체의 QR 은 영원히 같은
     * 이미지다. 라벨 수십 장을 미리보기로 띄울 때 재요청이 나가지 않는다. {@code private}
     * 인 것은 로그인 뒤에만 보이는 자료이기 때문이다.
     *
     * @param size 한 변 픽셀. 생략하면 512, 범위를 벗어나면 400
     */
    @GetMapping("/{batchId}/items/{productItemId}/qr")
    public ResponseEntity<byte[]> itemQrCode(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long batchId,
            @PathVariable Long productItemId,
            @RequestParam(required = false) Integer size) {
        byte[] png = productionBatchService.qrImage(userId(jwt), batchId, productItemId, size);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(png.length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=31536000, immutable")
                .header("X-Content-Type-Options", "nosniff")
                .body(png);
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
