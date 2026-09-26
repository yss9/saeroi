package com.ssafy.upcycle.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 사용자 발화.
 *
 * <p>상한을 두는 이유는 컨텍스트 비용이다. 4000자면 담당자가 요구사항을 길게 붙여넣어도
 * 넉넉하고, 그 이상은 파일로 받아야 할 성격이다.
 */
public record SendMessageRequest(@NotBlank @Size(max = 4000) String message) {}
