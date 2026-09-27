package com.ssafy.upcycle.agent.controller;

import com.ssafy.upcycle.agent.dto.CompleteActionRequest;
import com.ssafy.upcycle.agent.dto.ConversationDetailResponse;
import com.ssafy.upcycle.agent.service.PendingActionService;
import com.ssafy.upcycle.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 확인 카드의 결과를 받는 지점.
 *
 * <p>에이전트가 제안한 쓰기는 이 경로를 통해서만 실행된다. 모델은 여기에 닿을 수 없다.
 */
@RestController
@RequestMapping("/api/v1/agent/actions")
@RequiredArgsConstructor
public class AgentActionController {

    private final PendingActionService pendingActionService;

    @PostMapping("/{actionId}/complete")
    public ApiResponse<ConversationDetailResponse> complete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long actionId,
            @Valid @RequestBody(required = false) CompleteActionRequest request) {
        String resultJson = request == null ? null : request.resultJson();
        return ApiResponse.success(pendingActionService.complete(userId(jwt), actionId, resultJson));
    }

    /**
     * 다단계 서명의 중간 단계 보고.
     *
     * <p>LLM 을 부르지 않으므로 대화 전체를 돌려주지 않는다 — 화면은 카드의 진행 표시만
     * 갱신하면 되고, 매 단계 전체 응답을 받으면 불필요하게 무겁다.
     */
    @PostMapping("/{actionId}/step")
    public ApiResponse<Void> step(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long actionId,
            @Valid @RequestBody(required = false) CompleteActionRequest request) {
        pendingActionService.recordStep(
                userId(jwt), actionId, request == null ? null : request.resultJson());
        return ApiResponse.success(null);
    }

    @PostMapping("/{actionId}/reject")
    public ApiResponse<ConversationDetailResponse> reject(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long actionId) {
        return ApiResponse.success(pendingActionService.reject(userId(jwt), actionId));
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
