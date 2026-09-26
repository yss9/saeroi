package com.ssafy.upcycle.blockchain.controller;

import com.ssafy.upcycle.blockchain.dto.ChainEvidenceResponse;
import com.ssafy.upcycle.blockchain.service.BlockchainLiveEventHub;
import com.ssafy.upcycle.blockchain.service.BlockchainLiveQueryService;
import com.ssafy.upcycle.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 관리자용 실시간 블록 피드와 온체인 원본 재조회 API. */
@Validated
@RestController
@RequestMapping("/api/blockchain/live")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BlockchainLiveController {

    private final BlockchainLiveEventHub eventHub;
    private final BlockchainLiveQueryService queryService;

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-store");
        response.setHeader("X-Accel-Buffering", "no");
        return eventHub.subscribe(parseEventId(lastEventId));
    }

    @GetMapping("/transactions/{txHash}")
    public ApiResponse<ChainEvidenceResponse> transaction(
            @PathVariable
            @Pattern(regexp = "^0x[0-9a-fA-F]{64}$", message = "트랜잭션 해시 형식이 올바르지 않습니다.")
            String txHash) {
        return ApiResponse.success(queryService.findEvidence(txHash));
    }

    private static Long parseEventId(String value) {
        if (value == null || !value.matches("^\\d+$")) return null;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
