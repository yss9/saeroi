package com.ssafy.upcycle.agent.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.upcycle.agent.AgentErrorCode;
import com.ssafy.upcycle.agent.config.AgentProperties;
import com.ssafy.upcycle.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * GMS 를 통한 OpenAI Chat Completions 호출부.
 *
 * <p><b>왜 구현이 둘인가.</b> GMS 는 원본 API 호스트를 경로에 끼워넣는 패스스루라
 * ({@code /gmsapi/api.openai.com/...}) 각 공급자의 원래 규격을 그대로 써야 한다. 통합
 * 규격이 아니다. 그래서 Anthropic({@link GmsLlmClient})과 OpenAI(이 클래스)가 나뉜다.
 *
 * <p>주로 <b>개발용</b>이다. {@code gpt-4.1-nano} 같은 초경량 모델이 Haiku 의 10분의 1
 * 크레딧이라, 배선과 흐름을 확인하는 단계에 쓴다. <b>대화 품질을 이걸로 판단하면 안 된다</b> —
 * 여러 단계 tool 연쇄에서 약해서, 여기에 맞춰 프롬프트를 다듬으면 잘못된 대상에 튜닝하게 된다.
 *
 * <p>Anthropic 규격과 다른 점:
 *
 * <ul>
 *   <li>시스템 프롬프트가 {@code messages[0]} 의 한 항목이다 (최상위 필드가 아니다)
 *   <li>tool 정의가 {@code {type:"function", function:{...}}} 로 한 겹 더 감싸진다
 *   <li>tool 호출이 {@code message.tool_calls[]} 로 온다 (content 블록이 아니다)
 *   <li>tool 인자가 <b>JSON 문자열</b>이다 — 파싱 실패를 항상 염두에 둔다
 *   <li>tool 결과가 {@code role:"tool"} 메시지다 (user 메시지 안의 블록이 아니다)
 * </ul>
 */
@Slf4j
public class OpenAiLlmClient implements LlmClient {

    private static final String COMPLETIONS_PATH = "/v1/chat/completions";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;

    public OpenAiLlmClient(AgentProperties properties, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public LlmCompletion complete(List<LlmMessage> messages, List<ToolSpec> tools) {
        long startedAt = System.nanoTime();
        try {
            JsonNode response = restClient
                    .post()
                    .uri(COMPLETIONS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toRequest(messages, tools))
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                log.warn("LLM completion failed: reason=EMPTY_BODY latencyMs={}", elapsedMillis(startedAt));
                throw unavailable();
            }
            LlmCompletion completion = toCompletion(response);

            JsonNode usage = response.path("usage");
            log.info(
                    "LLM completion ok: toolCalls={} inputTokens={} outputTokens={} latencyMs={}",
                    completion.toolCalls().size(),
                    usage.path("prompt_tokens").asInt(-1),
                    usage.path("completion_tokens").asInt(-1),
                    elapsedMillis(startedAt));
            return completion;
        } catch (BusinessException alreadyTranslated) {
            throw alreadyTranslated;
        } catch (RuntimeException failure) {
            // 예외 메시지에 요청 본문이 섞여 나올 수 있어 클래스 이름만 남긴다.
            log.warn(
                    "LLM completion failed: reason={} latencyMs={}",
                    failure.getClass().getSimpleName(),
                    elapsedMillis(startedAt));
            throw unavailable();
        }
    }

    private ObjectNode toRequest(List<LlmMessage> messages, List<ToolSpec> tools) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", properties.model());
        request.put(outputLimitField(), properties.maxOutputTokens());
        if (isReasoningModel()) {
            // 이 값을 빼면 추론이 출력 예산을 다 써 답변이 빈 채로 돌아온다.
            request.put("reasoning_effort", properties.reasoningEffort());
        }

        ArrayNode messageArray = request.putArray("messages");
        for (LlmMessage message : messages) {
            messageArray.add(toRequestMessage(message));
        }

        if (!tools.isEmpty()) {
            ArrayNode toolArray = request.putArray("tools");
            for (ToolSpec tool : tools) {
                ObjectNode entry = toolArray.addObject();
                entry.put("type", "function");
                ObjectNode function = entry.putObject("function");
                function.put("name", tool.name());
                function.put("description", tool.description());
                function.set("parameters", readSchema(tool));
            }
            // tool 을 강제하지 않는다. 모델이 되물어야 할 때도 있고, 그때 억지로 tool 을
            // 부르게 하면 없는 정보를 지어내 인자를 채운다.
            request.put("tool_choice", "auto");
        }
        return request;
    }

    /**
     * 출력 길이를 제한하는 필드 이름. 모델 세대에 따라 다르다.
     *
     * <p>{@code gpt-5} 계열은 {@code max_tokens} 를 <b>거부하고</b>
     * {@code max_completion_tokens} 만 받는다. 이름만 다르고 뜻은 같은데, 틀리면 400 이
     * 떨어져 대화가 통째로 죽는다 — 모델만 바꿔 보려다 원인을 못 찾기 쉬운 자리다.
     *
     * <p>이전 세대({@code gpt-4.1} 등)는 {@code max_tokens} 를 쓰므로 둘을 갈라 둔다.
     * 새 이름을 옛 모델에 보내도 거부되기 때문에 한쪽으로 통일할 수 없다.
     */
    private String outputLimitField() {
        return isReasoningModel() ? "max_completion_tokens" : "max_tokens";
    }

    /** gpt-5 계열은 추론 모델이라 요청 규격이 다르다. */
    private boolean isReasoningModel() {
        return properties.model().startsWith("gpt-5");
    }

    private ObjectNode toRequestMessage(LlmMessage message) {
        ObjectNode node = objectMapper.createObjectNode();

        if (message.role() == LlmRole.TOOL) {
            node.put("role", "tool");
            node.put("tool_call_id", message.toolCallId());
            node.put("content", message.content());
            return node;
        }

        node.put("role", message.role().name().toLowerCase());
        // content 가 null 이어도 키를 남긴다. tool 호출만 하는 턴에서 null 을 허용하지만
        // 키 자체가 없으면 거부하는 구현이 있다.
        node.put("content", message.content());

        if (message.hasToolCalls()) {
            ArrayNode calls = node.putArray("tool_calls");
            for (ToolCall call : message.toolCalls()) {
                ObjectNode entry = calls.addObject();
                entry.put("id", call.id());
                entry.put("type", "function");
                ObjectNode function = entry.putObject("function");
                function.put("name", call.name());
                // Anthropic 과 달리 인자가 문자열이다. 저장 형식이 이미 문자열이라 그대로 쓴다.
                function.put("arguments", call.argumentsJson());
            }
        }
        return node;
    }

    private LlmCompletion toCompletion(JsonNode response) {
        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            log.warn("LLM completion failed: reason=NO_CHOICES");
            throw unavailable();
        }
        JsonNode message = choices.get(0).path("message");

        String content = message.path("content").isNull() ? null : message.path("content").asText(null);

        List<ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode call : message.path("tool_calls")) {
            String id = call.path("id").asText(null);
            JsonNode function = call.path("function");
            String name = function.path("name").asText(null);
            if (!StringUtils.hasText(id) || !StringUtils.hasText(name)) {
                log.warn("LLM completion failed: reason=MALFORMED_TOOL_CALL");
                throw unavailable();
            }
            // 인자가 비어 오는 경우가 실제로 있다(인자 없는 tool). 빈 객체로 정규화해
            // tool 실행부가 항상 JSON 을 파싱할 수 있게 한다.
            String arguments = function.path("arguments").asText("");
            toolCalls.add(new ToolCall(id, name, StringUtils.hasText(arguments) ? arguments : "{}"));
        }

        if (toolCalls.isEmpty() && !StringUtils.hasText(content)) {
            log.warn("LLM completion failed: reason=EMPTY_CHOICE");
            throw unavailable();
        }
        return new LlmCompletion(content, toolCalls);
    }

    private JsonNode readSchema(ToolSpec tool) {
        try {
            return objectMapper.readTree(tool.parametersJsonSchema());
        } catch (Exception invalidSchema) {
            throw new IllegalStateException("Invalid JSON schema for tool " + tool.name(), invalidSchema);
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private BusinessException unavailable() {
        return new BusinessException(AgentErrorCode.AGENT_LLM_UNAVAILABLE);
    }
}
