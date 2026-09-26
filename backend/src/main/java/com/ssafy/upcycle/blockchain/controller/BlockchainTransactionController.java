package com.ssafy.upcycle.blockchain.controller;

import com.ssafy.upcycle.blockchain.dto.BlockchainTransactionResponse;
import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.service.BlockchainTransactionQueryService;
import com.ssafy.upcycle.common.response.ApiResponse;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 한 대상(Material·Trade·Processing·Production)에 기록된 온체인 트랜잭션 조회.
 *
 * <p><b>인증이 필요하다.</b> {@code SecurityConfig} 의 permitAll 목록에 없으므로
 * {@code anyRequest().authenticated()} 로 떨어진다.
 *
 * <p>이 엔드포인트는 <b>읽기 전용</b>이다 — 기록 경로({@code POST /{id}/chain})는 각 도메인
 * 컨트롤러가 그대로 갖고, 여기서는 {@code blockchain_transaction} 에 이미 쌓인 것만 보여준다.
 * 도메인마다 같은 조회를 반복하지 않으려고 참조 타입을 파라미터로 받는 공통 엔드포인트
 * 하나로 뒀다 ({@code EvidenceController#findAll} 과 같은 관례).
 */
@Validated
@RestController
@RequestMapping("/api/blockchain/transactions")
@RequiredArgsConstructor
public class BlockchainTransactionController {

    private final BlockchainTransactionQueryService transactionQueryService;

    @GetMapping
    public ApiResponse<List<BlockchainTransactionResponse>> findAll(
            @RequestParam BlockchainReferenceType referenceType,
            @RequestParam @Positive(message = "조회 대상 id는 양수여야 합니다.") Long referenceId) {
        return ApiResponse.success(transactionQueryService.findAll(referenceType, referenceId));
    }
}
