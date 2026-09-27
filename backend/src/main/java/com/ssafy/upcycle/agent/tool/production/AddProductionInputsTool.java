package com.ssafy.upcycle.agent.tool.production;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.project.AgentProject;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolArgumentException;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.dto.ProductionInputRequest;
import com.ssafy.upcycle.production.dto.ProductionInputsRequest;
import com.ssafy.upcycle.common.unit.Quantities;
import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.service.MaterialQueryService;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.processing.entity.RequiredProcessing;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 배치에 소재를 투입한다. 소재마다 얼마를 투입했고, 그중 얼마가 제품에 들어가고, 얼마가 자투리로 남고, 얼마를
 * 폐기했는지 적는다.
 *
 * <p><b>왜 쪼개 받는가.</b> 업사이클 비율은 이 내역에서 나온다. "10kg 넣었다" 만으로는 제품에 실제로 들어간 양을
 * 알 수 없고, 그러면 제품에 붙는 업사이클 비율이 실제보다 커진다. 자투리와 폐기를 따로 적어야 숫자가 사실이 된다.
 *
 * <p><b>숫자는 담당자에게 물어서 받는다.</b> 담당자가 말한 대로 넘기면 나머지는 서버가 계산한다
 * ({@link InputBreakdown}). 빠진 값을 0 으로 채우지 않는다 — 예전에는 묻지도 않은 "제품 5kg + 자투리 0 + 폐기
 * 0" 이 카드에 올라갔다.
 *
 * <p>틀린 내역은 <b>확인 카드를 띄우기 전에</b> 돌려세운다. 엔티티도 합계를 막지만, 그건 담당자가 승인 버튼을
 * 누른 뒤에 터지고 무엇이 틀렸는지도 알려 주지 못한다.
 */
@Component
public class AddProductionInputsTool extends AbstractAgentTool {

    private final ProductionBatchService batchService;
    private final MaterialQueryService materialQuery;

    public AddProductionInputsTool(
            ObjectMapper objectMapper,
            ProductionBatchService batchService,
            MaterialQueryService materialQuery) {
        super(objectMapper);
        this.batchService = batchService;
        this.materialQuery = materialQuery;
    }

    /**
     * 대화에서 투입한 소재는 이 프로젝트의 소재가 된다. 그 소재를 받은 배치도 이 프로젝트의
     * 생산으로 본다 — 홈페이지에서 연 배치라도 대화가 소재를 넣었다면 확정까지 대화가 이끄는
     * 편이 담당자에게 자연스럽다.
     */
    @Override
    public void claim(JsonNode arguments, JsonNode result, AgentProject.Builder project) {
        project.batch(AgentProject.idAt(arguments, "batchId"));
        JsonNode inputs = arguments == null ? null : arguments.get("inputs");
        if (inputs != null && inputs.isArray()) {
            for (JsonNode input : inputs) {
                project.material(AgentProject.idAt(input, "materialId"));
            }
        }
    }

    @Override
    public String name() {
        return "add_production_inputs";
    }

    @Override
    public String description() {
        return "생산 배치에 소재를 투입한다. 소재마다 **담당자에게 물어** 제품에 들어간 양(제품 사용), 남아서 다시 "
                + "쓸 자투리, 못 써서 버린 양(폐기)을 적는다. 담당자가 말한 숫자를 그대로 넘긴다 — 투입량을 말했으면 "
                + "inputQuantity, 제품에 들어간 양을 말했으면 productQuantity, 둘 다 말했으면 둘 다. 더하거나 빼서 "
                + "맞추지 않는다. 자투리와 폐기는 0 이라도 담당자에게 들은 값이어야 한다. "
                + "투입 가능한 양은 get_my_materials 로 확인한다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "batchId": {"type": "integer",
                      "description": "생산 배치 번호. get_my_production_batches 의 productionBatchId 이거나 시스템 프롬프트의 현재 상태 목록에 적힌 번호다. **카드 번호(cardId)를 쓰지 않는다** — 실제로 그렇게 넘겨 배치를 찾지 못한 일이 있었다"},
                    "inputs": {
                      "type": "array", "minItems": 1, "maxItems": 20,
                      "items": {
                        "type": "object",
                        "properties": {
                          "materialId": {"type": "integer"},
                          "inputQuantity": {"type": "integer", "minimum": 1,
                            "description": "투입량 — 이 소재에서 꺼내 쓴 총량(gram). 담당자가 총량을 말했을 때만 넣는다"},
                          "productQuantity": {"type": "integer", "minimum": 0,
                            "description": "제품 사용 — 실제로 제품에 들어간 양(gram). 담당자가 말했을 때만 넣는다"},
                          "reusableScrapQuantity": {"type": "integer", "minimum": 0,
                            "description": "자투리 — 쓰고 남았지만 다시 쓸 수 있는 양(gram). 생산을 확정하면 새 소재로 보유 목록에 돌아온다. 담당자에게 들은 값"},
                          "wasteQuantity": {"type": "integer", "minimum": 0,
                            "description": "폐기 — 못 쓰게 되어 버린 양(gram, 손실). 사라진다. 담당자에게 들은 값"},
                          "wasteReason": {"type": "string", "maxLength": 200,
                            "description": "폐기가 있을 때 그 이유. 담당자가 말하지 않으면 비운다"}
                        },
                        "required": ["materialId", "reusableScrapQuantity", "wasteQuantity"],
                        "additionalProperties": false
                      }
                    },
                    "summary": {"type": "string", "description": "확인 카드에 보일 한 줄 설명"}
                  },
                  "required": ["batchId", "inputs", "summary"],
                  "additionalProperties": false
                }""";
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public PendingActionKind pendingKind() {
        return PendingActionKind.CONFIRM;
    }

    /**
     * 내역이 빠졌거나 어긋난 줄, 보유하지 않았거나 모자란 소재, 필수 공정이 남은 소재를 카드 이전에 돌려준다.
     *
     * <p><b>예외를 올리지 않는다.</b> 이 검사가 터지면 실행기는 "막을 이유가 없다" 로 보고 카드를 그대로 띄운다.
     */
    @Override
    public Optional<AgentToolResult> refuseBeforeProposal(
            AgentToolContext context, JsonNode arguments) {
        JsonNode inputs = arguments == null ? null : arguments.get("inputs");
        if (inputs == null || !inputs.isArray() || inputs.isEmpty()) {
            return Optional.of(AgentToolResult.of(json(Map.of(
                    "error", "INPUTS_REQUIRED",
                    "hint", "투입할 소재가 없습니다. 어떤 소재를 넣는지 담당자에게 확인하세요."))));
        }
        List<InputBreakdown> lines = new ArrayList<>();
        List<Map<String, Object>> problems = new ArrayList<>();
        String kind = null;
        for (JsonNode input : inputs) {
            InputBreakdown.Read read = InputBreakdown.read(input);
            if (read.ok()) {
                lines.add(read.line());
            } else {
                problems.add(Map.of("materialId", input.path("materialId").asLong(), "reason", read.problem()));
                kind = kind == null ? read.kind() : kind;
            }
        }
        if (!problems.isEmpty()) {
            return Optional.of(AgentToolResult.of(json(Map.of(
                    "error", kind,
                    "problems", problems,
                    "hint", hintFor(kind)))));
        }
        return unavailableMaterials(context, lines);
    }

    private static String hintFor(String kind) {
        return switch (kind) {
            case InputBreakdown.MISMATCH -> "담당자가 말한 투입량과 나눈 양의 합이 다릅니다. 계산해서 맞추지 말고 "
                    + "담당자에게 다시 확인하세요 — \"투입 5kg 중 제품 4kg, 자투리 0.5kg, 폐기 0.5kg 가 맞나요?\" 처럼 "
                    + "숫자를 늘어놓고 묻습니다.";
            case InputBreakdown.LEFTOVER_EXCEEDS_INPUT -> "자투리와 폐기의 합이 투입량보다 많습니다. 손실은 투입량에서 "
                    + "빠지는 양이지 투입량에 더하는 양이 아닙니다 — 6kg 을 넣어 전부 쓰고 500g 을 버렸다면 투입 6,000g 에 "
                    + "폐기 500g 입니다. 담당자에게 실제 내역을 다시 확인하세요.";
            default -> "소재마다 담당자에게 물어서 채우세요. 제품에 들어간 양(제품 사용), 남아서 다시 쓸 수 있는 양"
                    + "(자투리 — 생산을 확정하면 새 소재로 돌아옵니다), 못 써서 버린 양(폐기 — 사라집니다)입니다. 투입량이나 "
                    + "제품 사용 중 담당자가 말한 것을 넘기고, 자투리와 폐기는 없다고 해도 0 을 넘깁니다.";
        };
    }

    /**
     * 보유하지 않았거나 수량이 모자란 소재를 <b>카드 이전에</b> 돌려세운다.
     *
     * <h2>왜 필요한가 — 가공을 거치면 소재 번호가 바뀐다</h2>
     *
     * <p>가공 결과는 <b>새 소재로 발행된다.</b> 폴리에스터 원단을 세척에 보내면 결과물은
     * "세척 폴리에스터 원단" 이라는 다른 번호의 소재이고, 원래 소재는 줄어든다. 그런데
     * 모델은 대화 앞부분에서 본 <b>가공 전 번호</b>를 그대로 쓰는 일이 있다.
     *
     * <p>실제로 그렇게 막혔다. 보유는 40·41·42 인데 모델이 34·35·36 을 넘겼고, 승인 뒤
     * 도메인이 "사용 가능한 수량이 부족합니다" 로 거절했다. 그 문장에는 <b>어느 소재가
     * 문제인지도, 무엇을 써야 하는지도 없어서</b> 모델은 수량만 바꿔 같은 번호로 다시
     * 시도했고 담당자는 같은 오류를 두 번 봤다.
     *
     * <p>그래서 여기서 막고 <b>실제 보유 목록을 번호와 함께</b> 돌려준다. 모델이 고칠 수
     * 있는 형태로 알려주지 않으면 지어내는 것 말고 할 수 있는 일이 없다.
     */
    private Optional<AgentToolResult> unavailableMaterials(AgentToolContext context, List<InputBreakdown> lines) {
        List<MyMaterialResponse> held;
        try {
            held = materialQuery.findMyMaterials(context.userId());
        } catch (RuntimeException failure) {
            // 사전 점검이 흔들린다고 정상 흐름을 세우지 않는다. 못 본 것은 도메인이 거절한다.
            return Optional.empty();
        }
        Map<Long, MyMaterialResponse> byId = held.stream()
                .collect(Collectors.toMap(MyMaterialResponse::materialId, each -> each, (a, b) -> a));

        List<Map<String, Object>> problems = new ArrayList<>();
        for (InputBreakdown line : lines) {
            MyMaterialResponse owned = byId.get(line.materialId());
            if (owned == null) {
                problems.add(Map.of("materialId", line.materialId(), "reason", "보유하지 않은 소재입니다"));
            } else if (owned.available() < line.inputQuantity()) {
                problems.add(Map.of(
                        "materialId", line.materialId(),
                        "reason", "사용 가능한 수량이 모자랍니다",
                        "requested", line.inputQuantity(),
                        "available", owned.available()));
            }
        }
        if (problems.isEmpty()) {
            return unprocessedMaterials(byId, lines);
        }
        return Optional.of(AgentToolResult.of(json(Map.of(
                "error", "MATERIAL_NOT_AVAILABLE",
                "problems", problems,
                "heldMaterials", held.stream()
                        .filter(each -> each.available() > 0)
                        .map(each -> Map.of(
                                "materialId", each.materialId(),
                                "name", each.name(),
                                "available", each.available()))
                        .toList(),
                "hint", "가공을 거친 소재는 결과물이 **새 번호로 발행됩니다** — 가공 전 번호는 "
                        + "이미 줄어들었거나 없습니다. 위 heldMaterials 의 번호와 수량으로 다시 "
                        + "부르세요. 담당자에게는 어떤 소재를 얼마나 넣을지만 확인하고, 번호는 "
                        + "이 목록에서 고릅니다 — 앞선 대화에 나온 번호를 그대로 쓰지 마세요."))));
    }

    /**
     * 필수 공정이 남은 폐소재를 <b>카드 이전에</b> 돌려세운다.
     *
     * <p>생산 확정은 폐소재가 세척·해체·선별을 모두 거쳤는지 본다({@code ProductionConfirmService}). 그 검사는
     * 투입보다 한참 뒤라, 여기서 넘기면 담당자는 배치를 열고 소재를 묶은 뒤 확정에서야 막힌다. 실제로 폐울과
     * 폐데님을 산 직후 모델이 가공 없이 생산 카드부터 띄웠고, 담당자가 "가공은 안 해도 돼?" 라고 물어서야
     * 멈췄다. 판정은 확정과 같은 규칙({@link RequiredProcessing})으로 한다.
     */
    private Optional<AgentToolResult> unprocessedMaterials(
            Map<Long, MyMaterialResponse> byId, List<InputBreakdown> lines) {
        List<Map<String, Object>> unprocessed = new ArrayList<>();
        for (InputBreakdown line : lines) {
            MyMaterialResponse owned = byId.get(line.materialId());
            if (owned == null) {
                continue;
            }
            List<ProcessType> completed =
                    owned.completedProcessTypes() == null ? List.of() : owned.completedProcessTypes();
            List<ProcessType> missing = RequiredProcessing.missing(owned.rootOrigin(), completed);
            if (!missing.isEmpty()) {
                unprocessed.add(Map.of(
                        "materialId", owned.materialId(),
                        "name", owned.name() == null ? "소재" : owned.name(),
                        "missingRequiredProcessTypes", missing));
            }
        }
        if (unprocessed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(AgentToolResult.of(json(Map.of(
                "error", "MATERIAL_NOT_PROCESSED",
                "unprocessed", unprocessed,
                "hint", "폐소재는 세척·해체·선별을 모두 거쳐야 생산을 확정할 수 있습니다. 위 소재는 남은 공정이 "
                        + "있어 지금 투입하면 확정에서 막힙니다. 담당자에게 무엇이 남았는지 알리고 가공부터 "
                        + "제안하세요(create_processing_request). 가공이 끝나면 결과물이 새 번호로 들어오니 "
                        + "그 번호로 투입합니다."))));
    }

    /**
     * 카드에 <b>서버가 계산한 내역</b>을 싣는다 — 소재 이름과 투입·제품 사용·자투리·폐기 네 숫자.
     *
     * <p>모델이 넘긴 인자에는 들은 숫자만 있다(투입량이나 제품 사용 중 하나). 카드가 그것을 그대로 읽으면 빈
     * 칸이 생기고, 승인 때 실행하는 숫자와 카드의 숫자가 다른 곳에서 계산된다. 여기서 한 번 풀어 인자를 덮으면
     * 담당자가 본 숫자가 그대로 실행된다.
     */
    @Override
    public Map<String, Object> payloadExtras(AgentToolContext context, JsonNode arguments) {
        List<InputBreakdown> lines = linesOf(arguments);
        Map<Long, String> names = namesOf(context);
        List<Map<String, Object>> resolved = new ArrayList<>();
        for (InputBreakdown line : lines) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("materialId", line.materialId());
            row.put("materialName", names.getOrDefault(line.materialId(), "소재 " + line.materialId()));
            row.put("inputQuantity", line.inputQuantity());
            row.put("productQuantity", line.productQuantity());
            row.put("reusableScrapQuantity", line.scrapQuantity());
            row.put("wasteQuantity", line.wasteQuantity());
            if (line.wasteReason() != null) {
                row.put("wasteReason", line.wasteReason());
            }
            resolved.add(row);
        }
        return Map.of("inputs", resolved);
    }

    /**
     * 카드 제목을 서버가 쓴다 — 무엇을 얼마나 넣는지가 담당자가 동의하는 내용이다.
     *
     * <p>소재 이름은 <b>지금 보유 목록</b>에서 가져온다. 모델이 쓰면 가공 전 이름("폴리에스터
     * 원단")을 쓰기 쉬운데, 실제로 들어가는 것은 가공 후 소재("세척 폴리에스터 원단")다.
     */
    @Override
    public Optional<String> serverSummary(AgentToolContext context, JsonNode arguments) {
        List<InputBreakdown> lines = linesOf(arguments);
        if (lines.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, String> names = namesOf(context);
        List<String> parts = new ArrayList<>();
        long scrap = 0;
        long waste = 0;
        for (InputBreakdown line : lines) {
            parts.add("%s %s".formatted(
                    names.getOrDefault(line.materialId(), "소재 " + line.materialId()),
                    Quantities.text(line.inputQuantity())));
            scrap += line.scrapQuantity();
            waste += line.wasteQuantity();
        }
        String line = "소재 투입 — " + String.join(" · ", parts);
        return Optional.of(scrap + waste > 0
                ? line + " (자투리 %s · 폐기 %s)".formatted(Quantities.text(scrap), Quantities.text(waste))
                : line);
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        long batchId = requiredPositiveLong(arguments, "batchId");

        List<ProductionInputRequest> inputs = new ArrayList<>();
        for (JsonNode input : inputsNode(arguments)) {
            InputBreakdown.Read read = InputBreakdown.read(input);
            if (!read.ok()) {
                throw new AgentToolArgumentException(read.problem());
            }
            InputBreakdown line = read.line();
            inputs.add(new ProductionInputRequest(
                    line.materialId(),
                    line.inputQuantity(),
                    line.productQuantity(),
                    line.scrapQuantity(),
                    line.wasteQuantity(),
                    line.wasteReason()));
        }

        ProductionBatchResponse updated =
                batchService.addInputs(context.userId(), batchId, new ProductionInputsRequest(inputs));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batch", updated);
        String message = "소재 %d건을 투입했습니다. 현재 업사이클 비율은 %s 입니다."
                .formatted(inputs.size(), updated.upcycleRatio());
        payload.put("message", message);
        return AgentToolResult.of(json(payload), message);
    }

    /** 읽을 수 있는 줄만. 카드 부가 정보는 검사를 지난 뒤에 만들어지므로 여기서 빠지는 줄은 없다. */
    private List<InputBreakdown> linesOf(JsonNode arguments) {
        JsonNode inputs = arguments == null ? null : arguments.get("inputs");
        if (inputs == null || !inputs.isArray()) {
            return List.of();
        }
        List<InputBreakdown> lines = new ArrayList<>();
        for (JsonNode input : inputs) {
            InputBreakdown.Read read = InputBreakdown.read(input);
            if (read.ok()) {
                lines.add(read.line());
            }
        }
        return lines;
    }

    /** 소재 이름. 이름이 빈 소재는 뺀다 — 값이 null 이면 {@code toMap} 이 터져 카드 내역이 통째로 빠진다. */
    private Map<Long, String> namesOf(AgentToolContext context) {
        return materialQuery.findMyMaterials(context.userId()).stream()
                .filter(material -> material.name() != null)
                .collect(Collectors.toMap(MyMaterialResponse::materialId, MyMaterialResponse::name, (a, b) -> a));
    }

    private JsonNode inputsNode(JsonNode arguments) {
        JsonNode inputs = arguments.get("inputs");
        if (inputs == null || !inputs.isArray() || inputs.isEmpty()) {
            throw new AgentToolArgumentException("inputs 가 필요합니다.");
        }
        return inputs;
    }
}
