package com.ssafy.upcycle.trade.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.trade.dto.TradeShipRequest;
import com.ssafy.upcycle.trade.dto.TradeShipResponse;
import com.ssafy.upcycle.trade.service.TradeShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소재 거래. 구매 이후의 흐름을 다룬다.
 *
 * <p>파는 쪽은 SUPPLIER 뿐이다. BRAND 는 소재를 사서 제품을 만드는 쪽이며 판매자가
 * 되지 않는다.
 *
 * <p>역할 검사만으로는 부족하다 — SUPPLIER 라면 누구나 남의 거래를 발송 처리할 수
 * 있게 되기 때문이다. 실제 판매자인지는 서비스가 거래의 {@code seller_id} 와 대조한다.
 */
@RestController
@RequestMapping("/api/v1/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeShipmentService tradeShipmentService;

    /**
     * 판매자 발송 처리. 송장 정보는 선택이다.
     *
     * <p>잠금과 보유량은 여기서 바뀌지 않는다. 소유권은 구매자 수령 확인 시점에 넘어간다.
     */
    @PatchMapping("/{tradeId}/ship")
    @PreAuthorize("hasRole('SUPPLIER')")
    public ApiResponse<TradeShipResponse> ship(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long tradeId,
            @Valid @RequestBody TradeShipRequest request) {
        return ApiResponse.success(tradeShipmentService.ship(userId(jwt), tradeId, request));
    }

    /**
     * JWT {@code sub} 는 {@code WalletAuthService} 가
     * {@code String.valueOf(user.getId())} 로 넣는다.
     */
    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
