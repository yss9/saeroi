package com.ssafy.upcycle.agent.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 대화 하나가 맡은 제품 프로젝트 — <b>이 대화가 시작한 일</b>의 번호들.
 *
 * <h2>왜 대화가 자기 일을 알아야 하나</h2>
 *
 * <p>대화 하나는 제품 하나를 만든다. 그런데 서버는 어떤 거래·가공·배치가 어느 대화에서
 * 생겼는지 몰랐고, 브랜드의 모든 일을 대화에 들이밀었다. 에코백을 만드는 대화에 홈페이지에서
 * 산 다른 소재의 "수령 확인" 카드가 뜨고, 진행 막대는 "대화 시작 이후에 만든 거래" 라는
 * 추측으로 엉뚱한 거래를 잡았다.
 *
 * <h2>무엇이 이 프로젝트의 것인가</h2>
 *
 * <ul>
 *   <li><b>대화에서 승인한 카드로 생긴 것.</b> 구매한 거래, 맡긴 가공, 연 배치, 등록한 제품.
 *       홈페이지에서 직접 시작한 일은 홈페이지의 것이다.
 *   <li><b>대화에서 쓴 소재.</b> 대화에서 산 소재, 대화의 가공 요청이나 생산 투입에 쓴 보유
 *       소재, 그 가공의 결과물. 가진 소재로 시작할 때 따로 "가져오는" 단계를 두지 않는다 —
 *       쓰는 순간 들어온다.
 * </ul>
 *
 * <p>이 범위는 <b>대화가 먼저 꺼내는 것</b>에만 적용된다(할 일 안내, 자동 카드, 진행 막대).
 * 담당자가 물으면 브랜드의 무엇이든 처리한다 — 도구는 이 범위로 막지 않는다.
 */
public record AgentProject(
        Set<Long> tradeIds,
        Set<Long> jobIds,
        Set<Long> batchIds,
        Set<Long> productModelIds,
        Set<Long> materialIds) {

    public AgentProject {
        tradeIds = Set.copyOf(tradeIds);
        jobIds = Set.copyOf(jobIds);
        batchIds = Set.copyOf(batchIds);
        productModelIds = Set.copyOf(productModelIds);
        materialIds = Set.copyOf(materialIds);
    }

    public static AgentProject empty() {
        return new Builder().build();
    }

    /** 아직 이 대화에서 시작한 일이 없다. */
    public boolean isEmpty() {
        return tradeIds.isEmpty()
                && jobIds.isEmpty()
                && batchIds.isEmpty()
                && productModelIds.isEmpty()
                && materialIds.isEmpty();
    }

    /**
     * 이 일들이 들어가 있는 가장 먼 단계. 아무것도 없으면 비어 있다.
     *
     * <p>소재만 있는 것은 단계를 정하지 않는다 — 가진 소재로 가공부터 시작하는 경우 소재는
     * 가공 요청과 함께 들어오고, 그때 단계는 가공 요청이 정한다.
     */
    public Optional<JourneyPhase> furthestStage() {
        if (!productModelIds.isEmpty()) {
            return Optional.of(JourneyPhase.PRODUCT);
        }
        if (!batchIds.isEmpty()) {
            return Optional.of(JourneyPhase.PRODUCTION);
        }
        if (!jobIds.isEmpty()) {
            return Optional.of(JourneyPhase.PROCESSING);
        }
        if (!tradeIds.isEmpty()) {
            return Optional.of(JourneyPhase.MATERIAL_SOURCING);
        }
        return Optional.empty();
    }

    public boolean ownsTrade(Long id) {
        return id != null && tradeIds.contains(id);
    }

    public boolean ownsJob(Long id) {
        return id != null && jobIds.contains(id);
    }

    public boolean ownsBatch(Long id) {
        return id != null && batchIds.contains(id);
    }

    public boolean ownsProductModel(Long id) {
        return id != null && productModelIds.contains(id);
    }

    public boolean ownsMaterial(Long id) {
        return id != null && materialIds.contains(id);
    }

    /**
     * 저장된 JSON 에서 번호 하나를 읽는다. 없거나 숫자가 아니면 {@code null} 이다.
     *
     * <p>카드 결과는 도구가 그때그때 만든 JSON 이라 오래된 카드는 모양이 다를 수 있다. 읽지
     * 못하면 그 번호를 빼고 넘어간다 — 프로젝트 하나를 못 읽었다고 대화가 멈추면 안 된다.
     */
    public static Long idAt(JsonNode node, String... path) {
        JsonNode current = node;
        for (String field : path) {
            if (current == null) {
                return null;
            }
            current = current.get(field);
        }
        return current != null && current.canConvertToLong() && current.isIntegralNumber()
                ? current.asLong()
                : null;
    }

    /** 도구가 자기 카드에서 무엇이 생겼는지 적는 곳({@code AgentTool#claim}). */
    public static final class Builder {

        private final Set<Long> tradeIds = new LinkedHashSet<>();
        private final Set<Long> jobIds = new LinkedHashSet<>();
        private final Set<Long> batchIds = new LinkedHashSet<>();
        private final Set<Long> productModelIds = new LinkedHashSet<>();
        private final Set<Long> materialIds = new LinkedHashSet<>();

        public Builder trade(Long id) {
            add(tradeIds, id);
            return this;
        }

        public Builder job(Long id) {
            add(jobIds, id);
            return this;
        }

        public Builder batch(Long id) {
            add(batchIds, id);
            return this;
        }

        public Builder productModel(Long id) {
            add(productModelIds, id);
            return this;
        }

        public Builder material(Long id) {
            add(materialIds, id);
            return this;
        }

        public AgentProject build() {
            return new AgentProject(tradeIds, jobIds, batchIds, productModelIds, materialIds);
        }

        private static void add(Set<Long> into, Long id) {
            if (id != null && id > 0) {
                into.add(id);
            }
        }
    }
}
