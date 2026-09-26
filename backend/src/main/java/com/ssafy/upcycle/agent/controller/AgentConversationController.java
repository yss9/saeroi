package com.ssafy.upcycle.agent.controller;

import com.ssafy.upcycle.agent.dto.ConversationDetailResponse;
import com.ssafy.upcycle.agent.dto.JourneyProgressResponse;
import com.ssafy.upcycle.agent.dto.ConversationSummaryResponse;
import com.ssafy.upcycle.agent.dto.SendMessageRequest;
import com.ssafy.upcycle.agent.service.AgentConversationService;
import com.ssafy.upcycle.common.response.ApiResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * BRAND 담당자의 대화형 에이전트.
 *
 * <p>역할 검사를 {@code @PreAuthorize} 로 하지 않는다. 이 저장소의 인가 정본은 서비스 계층의
 * DB 재조회다({@code specs/2026-09-08-role-permission-matrix.md}) — 게다가 "이 대화가 내
 * 것인가" 는 토큰 claim 으로 표현할 수 없어 어차피 서비스가 봐야 한다.
 */
@RestController
@RequestMapping("/api/v1/agent/conversations")
@RequiredArgsConstructor
public class AgentConversationController {

    private final AgentConversationService conversationService;

    @PostMapping
    public ApiResponse<ConversationDetailResponse> start(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.success(conversationService.start(userId(jwt), request.message()));
    }

    @GetMapping
    public ApiResponse<List<ConversationSummaryResponse>> list(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(conversationService.list(userId(jwt), size));
    }

    /** 진행 중인 대화. 없으면 data 가 null 이라 화면이 "새로 시작" 을 그린다. */
    @GetMapping("/active")
    public ApiResponse<ConversationDetailResponse> active(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(conversationService.active(userId(jwt)));
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationDetailResponse> detail(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long conversationId) {
        return ApiResponse.success(conversationService.detail(userId(jwt), conversationId));
    }

    /**
     * 대화 위에 뜨는 경과 표시.
     *
     * <p>대화 상세와 나누는 이유는 갱신 주기가 다르기 때문이다. 이쪽은 상대방이 움직였는지를
     * 보려고 주기적으로 다시 읽는데, 대화 전체를 그 주기로 받으면 메시지 수십 건이 매번
     * 따라온다.
     */
    @GetMapping("/{conversationId}/progress")
    public ApiResponse<JourneyProgressResponse> progress(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long conversationId) {
        return ApiResponse.success(conversationService.progress(userId(jwt), conversationId));
    }

    @PostMapping("/{conversationId}/messages")
    public ApiResponse<ConversationDetailResponse> send(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long conversationId,
            @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.success(
                conversationService.send(userId(jwt), conversationId, request.message()));
    }

    /**
     * 대화 밖에서 끝낸 일을 알린다 (결제 복귀 등).
     *
     * <p>모델을 한 번 부르므로 크레딧이 든다. 아무 때나 부르지 않는다.
     */
    @PostMapping("/{conversationId}/events")
    public ApiResponse<ConversationDetailResponse> reportEvent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long conversationId,
            @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.success(
                conversationService.reportEvent(userId(jwt), conversationId, request.message()));
    }

    /** 담당자가 완료 처리한다. 그때까지 만들어진 거래·가공 요청은 그대로 남는다. */
    @PostMapping("/{conversationId}/complete")
    public ApiResponse<Void> complete(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long conversationId) {
        conversationService.complete(userId(jwt), conversationId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{conversationId}")
    public ApiResponse<Void> abandon(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long conversationId) {
        conversationService.abandon(userId(jwt), conversationId);
        return ApiResponse.success(null);
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
