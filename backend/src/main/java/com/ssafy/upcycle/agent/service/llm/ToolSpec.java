package com.ssafy.upcycle.agent.service.llm;

/**
 * 모델에게 알려줄 tool 하나의 명세.
 *
 * @param name 모델이 부를 이름. snake_case 로 통일한다
 * @param description 언제 이 tool 을 쓰는지. 모델이 고르는 유일한 근거이므로 구체적으로 쓴다
 * @param parametersJsonSchema 인자의 JSON Schema 문자열
 */
public record ToolSpec(String name, String description, String parametersJsonSchema) {}
