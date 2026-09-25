package com.ssafy.upcycle.agent.tool.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.project.AgentProject;
import com.ssafy.upcycle.agent.project.AgentProjects;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.service.ProcessingTodo;
import com.ssafy.upcycle.processing.dto.ProcessingJobResponse;
import com.ssafy.upcycle.processing.service.ProcessingJobWorkflowService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ssafy.upcycle.common.unit.Quantities;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 맡긴 가공이 지금 어디까지 왔는지.
 *
 * <p>가공은 담당자가 <b>가장 오래 기다리는 단계</b>다. 소재를 보내고 업체가 작업을 끝낼
 * 때까지 기다린다. 그런데 기다리는 동안 "지금 어떻게 됐냐" 를 물어볼 수단이 없었다 —
 * 소재 거래에는 {@code get_my_trades} 가 있는데 가공에는 대응하는 tool 이 없었고, 그래서
 * 모델은 대답하지 못하거나 이력에 남은 낡은 값으로 지어냈다.
 *
 * <p><b>견적을 기다리는 단계는 없다.</b> 요청을 만드는 순간 업체의 등록 단가로 가공비가
 * 계산되고 작업이 함께 생긴다({@code ProcessingRequestService.create}). 그래서 요청과 작업을
 * 따로 보여줄 것이 없고, 여기서는 작업만 돌려준다. 여러 업체에 요청했다면 이 목록의
 * {@code feeWon} 이 그대로 비교표가 된다.
 *
 * <p>줄마다 <b>지금 담당자가 할 일</b>을 붙인다. 상태 이름만 주면 모델이 그 뜻을 추측해
 * 엉뚱한 tool 을 부른다. 기다리는 상태에 "할 일이 없다" 고 적어 두는 것도 같은 이유다 —
 * 비워 두면 모델이 무언가 할 일이 있다고 보고 재촉할 tool 을 찾는다.
 *
 * <p>그 판정은 {@link ProcessingTodo} 가 한다. 경과 막대와 사건 보고가 같은 물음에
 * 답하는데, 각자 판정하면 모델이 받는 답과 화면이 어긋난다.
 *
 * <p><b>이 대화의 작업인지 적는다({@code thisProject}).</b> 목록은 담당자의 모든 작업이라, 모델이 지난 제품의 작업
 * 7·6·5 까지 "완료" 로 늘어놓은 일이 있었다. 이 대화의 작업을 앞에 두고, 소재 이름을 붙여 번호 대신 부르게 한다.
 */
@Component
public class MyProcessingTool extends AbstractAgentTool {

    private final ProcessingJobWorkflowService jobWorkflow;
    private final MaterialRepository materials;
    /**
     * 늦게 꺼낸다. 프로젝트 판정이 도구 목록을 쓰고 도구 목록이 이 도구를 품어서, 바로 받으면 서로를 기다린다.
     */
    private final ObjectProvider<AgentProjects> projects;

    public MyProcessingTool(
            ObjectMapper objectMapper,
            ProcessingJobWorkflowService jobWorkflow,
            MaterialRepository materials,
            ObjectProvider<AgentProjects> projects) {
        super(objectMapper);
        this.jobWorkflow = jobWorkflow;
        this.materials = materials;
        this.projects = projects;
    }

    @Override
    public String name() {
        return "get_my_processing";
    }

    @Override
    public String description() {
        return "맡긴 가공 작업의 현재 상태를 본다. 가공비와 진행 단계, 지금 담당자가 할 일이 함께 나온다. "
                + "여러 업체에 맡겼다면 가공비 비교표가 된다. "
                + "담당자가 가공 진행을 물으면 기억에 의존하지 말고 이것을 먼저 부른다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {"type": "object", "properties": {}, "additionalProperties": false}""";
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        List<ProcessingJobResponse> jobs = jobWorkflow.list(context.userId());
        AgentProject project = projectOf(context);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProcessingJobResponse job : jobs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("jobId", job.jobId());
            row.put("thisProject", project.ownsJob(job.jobId()));
            row.put("materialName", nameOf(job.inputMaterialId()));
            row.put("status", job.status());
            row.put("feeWon", job.fee());
            row.put("inputMaterialId", job.inputMaterialId());
            row.put("inputQuantityGram", job.inputQuantity());
            row.put("inputText", Quantities.text(job.inputQuantity()));
            row.put("returnTrackingNumber", job.returnTrackingNumber());
            row.put("todo", ProcessingTodo.of(job.status()).instruction());
            rows.add(row);
        }

        // 이 대화의 작업을 앞에 둔다. 모델은 앞에 있는 것부터 말한다.
        rows.sort((a, b) -> Boolean.compare((boolean) b.get("thisProject"), (boolean) a.get("thisProject")));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobs", rows);
        payload.put("count", rows.size());
        payload.put(
                "hint",
                "todo 가 담당자의 다음 행동이다. '기다리는 중' 인 건은 업체 차례이므로 "
                        + "재촉할 tool 을 찾지 말고 그대로 알린다. feeWon 은 이미 정해진 "
                        + "가공비다 — 여러 건이면 나란히 보여 준다. thisProject 가 false 인 작업은 지난 "
                        + "제품의 것이다 — 담당자가 묻지 않았으면 꺼내지 않는다. 작업은 번호보다 "
                        + "materialName 과 양으로 부른다(\"울 원단 5kg 가공\").");
        return AgentToolResult.of(json(payload));
    }

    /** 이 대화의 프로젝트. 판정이 실패해도 목록은 돌려준다 — 전부 이 대화의 것이 아닌 것으로 본다. */
    private AgentProject projectOf(AgentToolContext context) {
        try {
            Long conversationId = context.conversation() == null ? null : context.conversation().getId();
            return conversationId == null
                    ? AgentProject.empty()
                    : projects.getObject().of(context.userId(), conversationId);
        } catch (RuntimeException failure) {
            return AgentProject.empty();
        }
    }

    private String nameOf(Long materialId) {
        if (materialId == null) {
            return null;
        }
        return materials.findById(materialId).map(Material::getName).orElse(null);
    }
}
