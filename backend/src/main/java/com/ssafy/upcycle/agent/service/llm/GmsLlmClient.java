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
 * GMS 를 통한 Anthropic Messages API 호출부.
 *
 * <p><b>OpenAI 호환이 아니다.</b> GMS 는 원본 API 호스트를 경로에 끼워넣는 패스스루 프록시라
 * ({@code /gmsapi/api.anthropic.com/v1/messages}), Anthropic 규격을 그대로 쓴다. 두 규격의
 * 차이가 이 파일에 전부 갇혀 있다.
 *
 * <ul>
 *   <li>시스템 프롬프트가 최상위 {@code system} 필드다 (메시지 배열이 아니다)
 *   <li>tool 정의가 {@code {name, description, input_schema}} 다
 *   <li>tool 호출이 {@code content[]} 의 {@code tool_use} 블록으로 온다
 *   <li>tool 인자가 <b>이미 파싱된 객체</b>다 (JSON 문자열이 아니다 — 파싱 실패 경로가 없다)
 *   <li>tool 결과를 {@code user} 메시지 안의 {@code tool_result} 블록으로 돌려준다
 *   <li>{@code max_tokens} 가 필수다
 * </ul>
 *
 * <p>실패는 전부 {@code AGENT_LLM_UNAVAILABLE} 로 번역한다 — {@code WeightVisionClient} 와
 * 같은 방식이다. <b>본문은 로그에 남기지 않는다.</b> 대화에는 담당자가 쓴 사업 정보가
 * 그대로 들어 있다.
 */
@Slf4j
public class GmsLlmClient implements LlmClient {

    private static final String MESSAGES_PATH = "/v1/messages";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;

    public GmsLlmClient(AgentProperties properties, RestClient restClient, ObjectMapper objectMapper) {
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
                    .uri(MESSAGES_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toRequest(messages, tools))
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                log.warn("LLM completion failed: reason=EMPTY_BODY latencyMs={}", elapsedMillis(startedAt));
                throw unavailable();
            }
            LlmCompletion completion = toCompletion(response);

            // 토큰 수를 남긴다. 크레딧이 토큰당 차감이라 이 로그가 곧 비용 추적이다.
            JsonNode usage = response.path("usage");
            log.info(
                    "LLM completion ok: toolCalls={} inputTokens={} outputTokens={} cacheRead={} latencyMs={}",
                    completion.toolCalls().size(),
                    usage.path("input_tokens").asInt(-1),
                    usage.path("output_tokens").asInt(-1),
                    usage.path("cache_read_input_tokens").asInt(-1),
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
        request.put("max_tokens", properties.maxOutputTokens());

        if (properties.thinking()) {
            // Sonnet 4.6 이상에서만 유효하다. Haiku 4.5 는 이 형태를 거부한다(400).
            request.putObject("thinking").put("type", "adaptive");
        }

        // 시스템 프롬프트는 메시지가 아니라 최상위 필드다. **여러 개를 받는다** — 첫 블록은
        // 매 턴 같은 정적 프롬프트이고, 뒤따르는 블록은 서버가 방금 조회한 실제 상태처럼
        // 턴마다 달라지는 것이다. 캐시 경계가 첫 블록 끝에 놓이므로 순서가 중요하다.
        putSystem(request, messages.stream()
                .filter(message -> message.role() == LlmRole.SYSTEM)
                .map(LlmMessage::content)
                .filter(content -> content != null && !content.isBlank())
                .toList());

        ArrayNode messageArray = request.putArray("messages");
        for (LlmMessage message : messages) {
            if (message.role() == LlmRole.SYSTEM) {
                continue;
            }
            messageArray.add(toRequestMessage(message));
        }

        if (!tools.isEmpty()) {
            ArrayNode toolArray = request.putArray("tools");
            for (ToolSpec tool : tools) {
                ObjectNode entry = toolArray.addObject();
                entry.put("name", tool.name());
                entry.put("description", tool.description());
                entry.set("input_schema", readSchema(tool));
            }
            // 마지막 tool 에 캐시 경계를 둔다. 렌더 순서가 tools → system → messages 라
            // 여기까지가 한 턴 안에서 변하지 않는 접두사다.
            if (properties.promptCache() && !toolArray.isEmpty()) {
                ((ObjectNode) toolArray.get(toolArray.size() - 1))
                        .putObject("cache_control")
                        .put("type", "ephemeral");
            }
            // tool 을 강제하지 않는다. 모델이 되물어야 할 때도 있고, 그때 억지로 tool 을
            // 부르게 하면 없는 정보를 지어내 인자를 채운다.
            request.putObject("tool_choice").put("type", "auto");
        }
        return request;
    }

    /**
     * 저장된 메시지 한 줄을 Anthropic 형식으로 바꾼다.
     *
     * <p>tool 결과가 {@code user} 역할이라는 점이 OpenAI 와 가장 크게 다르다.
     */
    /**
     * 시스템 프롬프트를 넣는다. 캐싱을 켜면 블록 배열 형태여야 {@code cache_control} 을 붙일 수 있다.
     *
     * <p>tool 루프는 한 턴에 같은 시스템 프롬프트와 tool 명세를 네 번까지 다시 보낸다.
     * 캐시가 먹으면 두 번째 호출부터 그 부분의 입력 비용이 크게 떨어진다 — 크레딧이 토큰당
     * 차감되는 이 환경에서 가장 큰 절약 수단이다.
     */
    /**
     * 시스템 블록들을 넣는다.
     *
     * <p><b>캐시 경계는 첫 블록에만 둔다.</b> 정적 프롬프트(300줄 남짓)와 tool 명세가 한 턴에
     * 여러 번 재전송되므로 절약폭이 거기 있고, 뒤 블록은 턴마다 달라져 캐시될 수 없다. 뒤
     * 블록에 경계를 두면 매번 캐시가 무효화돼 앞 블록의 절약까지 날아간다.
     */
    private void putSystem(ObjectNode request, List<String> prompts) {
        if (prompts.isEmpty()) {
            return;
        }
        if (!properties.promptCache()) {
            request.put("system", String.join(System.lineSeparator() + System.lineSeparator(), prompts));
            return;
        }
        ArrayNode blocks = request.putArray("system");
        for (int index = 0; index < prompts.size(); index++) {
            ObjectNode block = blocks.addObject();
            block.put("type", "text");
            block.put("text", prompts.get(index));
            if (index == 0) {
                block.putObject("cache_control").put("type", "ephemeral");
            }
        }
    }

    private ObjectNode toRequestMessage(LlmMessage message) {
        ObjectNode node = objectMapper.createObjectNode();

        if (message.role() == LlmRole.TOOL) {
            node.put("role", "user");
            ObjectNode block = node.putArray("content").addObject();
            block.put("type", "tool_result");
            block.put("tool_use_id", message.toolCallId());
            block.put("content", message.content());
            return node;
        }

        node.put("role", message.role() == LlmRole.ASSISTANT ? "assistant" : "user");

        if (!message.hasToolCalls()) {
            node.put("content", message.content());
            return node;
        }

        // tool 을 부른 턴은 블록 배열이다. 설명 텍스트가 있으면 먼저 넣는다.
        ArrayNode content = node.putArray("content");
        if (StringUtils.hasText(message.content())) {
            content.addObject().put("type", "text").put("text", message.content());
        }
        for (ToolCall call : message.toolCalls()) {
            ObjectNode block = content.addObject();
            block.put("type", "tool_use");
            block.put("id", call.id());
            block.put("name", call.name());
            block.set("input", readInput(call));
        }
        return node;
    }

    private LlmCompletion toCompletion(JsonNode response) {
        JsonNode content = response.path("content");
        if (!content.isArray()) {
            log.warn("LLM completion failed: reason=NO_CONTENT");
            throw unavailable();
        }

        StringBuilder text = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();

        for (JsonNode block : content) {
            String type = block.path("type").asText("");
            switch (type) {
                case "text" -> text.append(block.path("text").asText(""));
                case "tool_use" -> {
                    String id = block.path("id").asText(null);
                    String name = block.path("name").asText(null);
                    if (!StringUtils.hasText(id) || !StringUtils.hasText(name)) {
                        log.warn("LLM completion failed: reason=MALFORMED_TOOL_USE");
                        throw unavailable();
                    }
                    // input 은 이미 객체다. 문자열로 직렬화해 저장 형식을 통일한다.
                    toolCalls.add(new ToolCall(id, name, block.path("input").toString()));
                }
                default -> {
                    // thinking 블록 등은 무시한다. 다시 돌려줄 필요가 없다.
                }
            }
        }

        String answer = text.isEmpty() ? null : text.toString();
        if (toolCalls.isEmpty() && !StringUtils.hasText(answer)) {
            // **예외로 던지지 않는다.** 빈 답은 장애가 아니라 "이 호출로는 할 말을 못 찾았다"
            // 는 사실이고, 호출부가 다시 시도할 수 있는 일이다.
            //
            // 던지던 때는 그 예외가 턴 전체를 끊어, 모델이 하려던 <b>다음 단계까지 사라졌다.</b>
            // 생산 확정 카드를 승인한 뒤 이어지는 턴이 그렇게 죽어 record_production_chain 이
            // 제안되지 않았고, 배치는 createBatch 만 된 채 멈췄다 — 만들어진 개체 10개가 어느
            // 제품의 재고도 아니게 됐다. 담당자는 그 단계가 있는 줄도 몰랐다.
            //
            // 카드 승인 직후 턴에서 특히 자주 난다. 사용자 발화 없이 모델이 말하게 하는
            // 자리라, tool 결과만 있고 새 질문이 없는 상태가 되기 때문으로 보인다.
            log.warn("LLM completion empty: reason=EMPTY_CONTENT");
            return new LlmCompletion(null, List.of());
        }
        return new LlmCompletion(answer, toolCalls);
    }

    private JsonNode readSchema(ToolSpec tool) {
        try {
            return objectMapper.readTree(tool.parametersJsonSchema());
        } catch (Exception invalidSchema) {
            // 스키마는 우리가 코드로 쓴 상수다. 여기서 깨지면 배포된 코드의 버그이지
            // 외부 장애가 아니므로 첫 호출에서 크게 드러나는 편이 낫다.
            throw new IllegalStateException("Invalid JSON schema for tool " + tool.name(), invalidSchema);
        }
    }

    /** 저장된 인자 문자열을 객체로 되돌린다. 우리가 저장한 값이라 깨질 일은 없다. */
    private JsonNode readInput(ToolCall call) {
        try {
            return objectMapper.readTree(call.argumentsJson());
        } catch (Exception malformed) {
            return objectMapper.createObjectNode();
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private BusinessException unavailable() {
        return new BusinessException(AgentErrorCode.AGENT_LLM_UNAVAILABLE);
    }
}
