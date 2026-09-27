package com.ssafy.upcycle.agent.service;

import com.ssafy.upcycle.agent.dto.JourneyProgressResponse.Spend;
import com.ssafy.upcycle.agent.project.AgentProject;
import com.ssafy.upcycle.agent.tool.processing.ProcessLabels;
import com.ssafy.upcycle.common.unit.Quantities;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.processing.dto.ProcessingJobResponse;
import com.ssafy.upcycle.processing.entity.ProcessingRequestType;
import com.ssafy.upcycle.processing.repository.ProcessingRequestTypeRepository;
import com.ssafy.upcycle.processing.service.ProcessingJobWorkflowService;
import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.dto.TradeSearchCondition;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.trade.service.TradeQueryService;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 이 대화의 프로젝트에서 <b>결제한 금액</b> — 소재 구매와 가공비.
 *
 * <p>오른쪽 패널의 "결제한 금액" 은 화면이 거래 목록만 더해 그렸다. 그래서 150,000원을 낸 가공비가 빠져 있었다.
 * 가공은 <b>작업 하나가 한 줄</b>이다 — 업체 한 곳에 세척·해체·선별을 함께 맡기면 공정 셋이 한 줄에 적히고 금액은
 * 그 작업의 가공비다. 업체가 다르면 작업도 달라 따로 적힌다. 합계는 화면이 더한다.
 *
 * <p>환불된 가공비(업체가 거절했거나 기한이 지난 작업)와 취소된 거래는 세지 않는다.
 */
@Component
@RequiredArgsConstructor
public class AgentSpending {

    /** 프로젝트 거래를 찾으려고 읽을 구매 거래 수. {@code BrandStepResolver} 와 같다. */
    private static final int TRADE_WINDOW = 100;

    private final TradeQueryService tradeQuery;
    private final ProcessingJobWorkflowService jobWorkflow;
    private final ProcessingRequestTypeRepository requestTypes;
    private final MaterialRepository materials;
    private final UserRepository users;
    /** 가공업체는 대표자 이름이 아니라 업체명으로 적는다. */
    private final CompanyNames companyNames;

    /** 진행 막대를 그리는 트랜잭션 안에서 불린다({@code AgentJourneyProgress}). */
    public List<Spend> of(Long userId, AgentProject project) {
        List<Spend> spending = new ArrayList<>();
        if (project.isEmpty()) {
            return spending;
        }
        spending.addAll(materials(userId, project));
        spending.addAll(processing(userId, project));
        return spending;
    }

    private List<Spend> materials(Long userId, AgentProject project) {
        if (project.tradeIds().isEmpty()) {
            return List.of();
        }
        List<TradeResponse> trades = tradeQuery.findMyTrades(
                        userId, new TradeSearchCondition(TradeSearchCondition.TradeSide.BUY, null, 0, TRADE_WINDOW))
                .content();
        return trades.stream()
                .filter(trade -> project.ownsTrade(trade.tradeId()))
                .filter(trade -> trade.status() != TradeStatus.CANCELED && trade.paymentAmount() > 0)
                .sorted(Comparator.comparing(TradeResponse::tradeId))
                .map(trade -> new Spend(
                        "MATERIAL",
                        "%s %s".formatted(trade.materialName(), Quantities.text(trade.quantity())),
                        null,
                        trade.paymentAmount()))
                .toList();
    }

    private List<Spend> processing(Long userId, AgentProject project) {
        if (project.jobIds().isEmpty()) {
            return List.of();
        }
        return jobWorkflow.list(userId).stream()
                .filter(job -> project.ownsJob(job.jobId()))
                // 돌려받은 가공비는 쓴 돈이 아니다.
                .filter(job -> job.paymentAmount() > 0 && job.refundedAt() == null)
                .sorted(Comparator.comparing(ProcessingJobResponse::jobId))
                .map(job -> new Spend("PROCESSING", labelOf(job), detailOf(job), job.paymentAmount()))
                .toList();
    }

    /** "폐데님 원단 10kg 가공". 투입 소재를 찾지 못하면 작업 번호로. */
    private String labelOf(ProcessingJobResponse job) {
        String name = job.inputMaterialId() == null
                ? null
                : materials.findById(job.inputMaterialId()).map(Material::getName).orElse(null);
        return name == null || name.isBlank()
                ? "가공 작업 %d".formatted(job.jobId())
                : "%s %s 가공".formatted(name, Quantities.text(job.inputQuantity()));
    }

    /** "세척·해체·선별 · pro" — 한 업체에 맡긴 공정들과 그 업체. */
    private String detailOf(ProcessingJobResponse job) {
        List<String> parts = new ArrayList<>();
        if (job.requestId() != null) {
            List<ProcessingRequestType> types =
                    requestTypes.findAllByProcessingRequestIdOrderBySequenceNoAsc(job.requestId());
            if (!types.isEmpty()) {
                parts.add(ProcessLabels.joined(types.stream().map(ProcessingRequestType::getProcessType).toList()));
            }
        }
        if (job.processorId() != null) {
            users.findById(job.processorId())
                    .map(user -> companyNames.of(user.getId(), user.getName()))
                    .ifPresent(parts::add);
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }
}
