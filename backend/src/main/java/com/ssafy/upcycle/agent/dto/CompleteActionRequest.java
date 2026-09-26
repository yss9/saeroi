package com.ssafy.upcycle.agent.dto;

import jakarta.validation.constraints.Size;

/**
 * 사용자가 화면에서 서명·결제를 끝낸 결과.
 *
 * <p>{@code resultJson} 은 액션 종류에 따라 다르다 — 서명이면 {@code txHash} 와
 * {@code onChainId}, 결제면 {@code pgToken} 이 들어온다. 형태를 하나로 고정하지 않는 것은
 * 액션 종류가 늘어날 때마다 이 DTO 를 고치지 않기 위해서다. 실제 검증은 각 tool 이 한다.
 */
public record CompleteActionRequest(@Size(max = 4000) String resultJson) {}
