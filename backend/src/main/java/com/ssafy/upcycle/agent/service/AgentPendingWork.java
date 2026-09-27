package com.ssafy.upcycle.agent.service;

import com.ssafy.upcycle.agent.entity.AgentPendingAction;
import com.ssafy.upcycle.agent.project.AgentProject;
import com.ssafy.upcycle.agent.project.AgentProjects;
import com.ssafy.upcycle.agent.service.step.BrandStep;
import com.ssafy.upcycle.agent.service.step.BrandStepResolver;
import com.ssafy.upcycle.common.unit.Quantities;
import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.service.MaterialQueryService;
import com.ssafy.upcycle.product.dto.ProductModelResponse;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.service.ProductModelQueryService;
import com.ssafy.upcycle.trade.dto.ListingResponse;
import com.ssafy.upcycle.trade.entity.ListingStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 담당자에게 남은 일을 <b>모델이 읽을 문장으로</b> 바꾼다.
 *
 * <h2>판정하지 않는다 — 옮겨 적기만 한다</h2>
 *
 * <p>무엇이 남았는지는 {@link BrandStepResolver} 가 정한다. 예전에는 이 클래스가 직접
 * 판정했고, 진행 막대·할 일 패널·화면이 각자 또 판정했다. 덮는 범위가 서로 달라 한 곳을
 * 고쳐도 다른 경로로 빠져나갔다 — 생산 확정 뒤 남는 블록체인 기록과 제품 연결이 어느
 * 목록에도 없어, 배치가 중간에 멈추고 개체 10개가 어느 제품의 재고도 아니게 된 일이 있었다.
 *
 * <h2>이 대화의 프로젝트만 꺼낸다</h2>
 *
 * <p>대화 하나는 제품 프로젝트 하나다({@link AgentProject}). 남은 일과 소재는 이 대화가 시작한
 * 것을 먼저 적는다. 홈페이지에서 한 일은 먼저 꺼내지 않지만, 담당자가 물으면 도구로 처리한다.
 *
 * <h2>이 문장이 들어가는 자리</h2>
 *
 * <p>매 턴 <b>시스템 프롬프트의 두 번째 블록</b>으로 들어간다({@code AgentTurnStore}).
 * 이력에 ASSISTANT 로 넣던 시절에는 담당자가 이 지시문을 그대로 읽었고, 모델은 그것을 자기
 * 말투로 배워 다음 턴에 통째로 베꼈다 — 아직 결제도 안 된 거래에 "결제는 끝났고 온체인
 * 기록이 아직이다" 라는 없는 사실까지 지어냈다.
 *
 * <p>여기서 LLM 을 부르지 않는다. 도메인 조회뿐이라 크레딧이 들지 않는다.
 */
@Component
@RequiredArgsConstructor
public class AgentPendingWork {

    /** 이 프로젝트의 소재로 보일 수. 대화 하나가 제품 하나라 이보다 많을 일은 드물다. */
    private static final int PROJECT_MATERIAL_LIMIT = 20;

    /**
     * 그 밖의 보유 소재로 보일 수. 가진 소재로 시작하려는 담당자에게 고를 거리를 주려는 것이라
     * 최근 것 몇 개면 된다 — 전부 실으면 이 프로젝트의 소재가 그 사이에 묻힌다.
     */
    private static final int OTHER_MATERIAL_LIMIT = 10;

    private final BrandStepResolver resolver;
    private final OpenCards openCards;
    private final MaterialQueryService materialQuery;
    private final AgentProjects projects;
    private final ProductModelQueryService productQuery;
    private final ConversationListings conversationListings;

    /** 이 대화에서 찾은 판매 글로 보일 수. 한 제품에 쓰는 소재가 이보다 많을 일은 드물다. */
    private static final int LISTING_LIMIT = 10;

    /**
     * 시스템 프롬프트에 넣을 문장. 적을 것이 없으면 빈 문자열이다.
     *
     * <p><b>기다리는 구간도 적는다.</b> 빼면 모델이 "할 일이 없다" 로 읽고 재촉할 도구를
     * 찾는다. "상대 차례다" 라고 적어 두어야 그대로 알린다.
     */
    public String describe(Long userId, Long conversationId) {
        AgentProject project = projects.of(userId, conversationId);
        // 부분마다 따로 붙인다. 남은 일이 없어도 보유 소재와 떠 있는 카드는 모델이 알아야
        // 한다 — 예전에는 남은 일이 비면 블록 전체를 비워, 카드가 떠 있다는 사실도 사라졌다.
        return projectState(userId, project)
                + pendingSteps(userId, project)
                + listingsFound(conversationId)
                + holdings(userId, project)
                + alreadyOnScreen(conversationId);
    }

    /**
     * 프로젝트가 어디쯤인지 — <b>시작 전</b>이거나 <b>끝났을 때</b>만 적는다. 그 사이는 남은
     * 일 목록이 말해 준다.
     *
     * <p>끝났다는 것을 알려야 하는 이유: 판매가 시작되면 남은 일 목록이 비고, 그러면 모델은
     * 할 일을 찾으려 다른 거래나 배치를 뒤진다. 이 대화의 제품은 끝났고 다음 제품은 새
     * 대화라고 적어 두어야 거기서 멈춘다.
     */
    private String projectState(Long userId, AgentProject project) {
        if (project.isEmpty()) {
            return """


                    ## 이 대화의 프로젝트

                    아직 이 대화에서 시작한 일이 없다. 담당자가 만들 제품과 필요한 소재를 말하면
                    판매 글을 찾는다. 가진 소재로 시작하겠다고 하면 아래 보유 소재에서 고르게 한다.""";
        }
        return onSaleProduct(userId, project)
                .map(model -> """


                        ## 이 대화의 프로젝트

                        이 프로젝트의 제품 "%s" 이(가) 판매 중이다. **이 대화의 여정은 끝났다.**
                        담당자에게 그렇게 알리고, 다른 제품을 만들려면 화면 위의 "완료" 버튼으로 이
                        대화를 마친 뒤 새 대화에서 시작하도록 안내한다.
                        들어온 주문을 묻는 것처럼 이 제품에 관한 물음에는 계속 답한다."""
                        .formatted(model.name()))
                .orElse("");
    }

    private Optional<ProductModelResponse> onSaleProduct(Long userId, AgentProject project) {
        if (project.productModelIds().isEmpty()) {
            return Optional.empty();
        }
        try {
            return productQuery.findMyProductModels(userId).stream()
                    .filter(model -> project.ownsProductModel(model.productModelId()))
                    .filter(model -> model.status() == ProductModelStatus.ON_SALE)
                    .findFirst();
        } catch (RuntimeException failure) {
            // 끝났다는 한 줄을 못 붙일 뿐이다. 남은 일 목록과 보유 소재는 그대로 나간다.
            return Optional.empty();
        }
    }

    private String pendingSteps(Long userId, AgentProject project) {
        List<BrandStep> steps = resolver.resolve(userId, project);
        if (steps.isEmpty()) {
            return "";
        }

        List<BrandStep> actionableSteps = steps.stream().filter(BrandStep::isActionable).toList();
        String actionable = render(actionableSteps);
        String waiting = render(steps.stream().filter(step -> !step.isActionable()).toList());

        StringBuilder text = new StringBuilder(
                "\n\n## 이 프로젝트에서 담당자 손이 필요한 일 (서버가 방금 조회한 실제 상태)\n\n");
        // **다음 한 가지는 서버가 고른다.** 목록만 주면 모델이 여럿 중 무엇부터 할지 정해야 하고,
        // 승인 뒤 "완료되었습니다" 로 끝내며 다음을 꺼내지 않는 일이 반복됐다. 여정의 앞 단계부터
        // 푼다 — 판정 목록이 거래 · 가공 · 생산 · 제품 순이라 첫 줄이 가장 앞선 일이다.
        if (!actionableSteps.isEmpty()) {
            text.append("**바로 다음 할 일:** ").append(nextLine(actionableSteps.getFirst())).append("\n\n");
        }
        text.append(actionable.isEmpty() ? "- 지금 담당자가 할 일은 없습니다.\n" : actionable);
        if (!waiting.isEmpty()) {
            text.append("\n### 상대를 기다리는 중 (담당자가 할 일 없음)\n\n").append(waiting);
        }
        text.append("""

                이 목록이 정본이다. 앞선 조회 결과가 이것과 다르면 이 목록을 따르고,
                **담당자가 묻기 전에** 해당하는 도구를 그 자리에서 부른다 — 담당자는 어떤
                단계가 남았는지 모른다. 기다리는 중인 건은 재촉할 도구를 찾지 말고 그대로 알린다.""");
        return text.toString();
    }

    /**
     * 이 대화에서 찾은 판매 글. <b>구매할 때 번호를 여기서 고른다.</b>
     *
     * <p>이력은 최근 것만 남아서 검색 결과가 잘려 나간다. 모델이 담당자에게 한 말에는 이름과 단가만
     * 있어, 구매할 즈음 번호를 짐작했다 — 폐데님 원단(21번)으로 합의하고 본 적 없는 19번(깨끗한
     * 데님)으로 결제 카드를 띄웠다. 저장된 도구 결과에서 다시 읽으므로 잘리지 않는다
     * ({@link ConversationListings}). 단가와 남은 양은 지금 값이다.
     */
    private String listingsFound(Long conversationId) {
        List<ListingResponse> found;
        try {
            found = conversationListings.current(conversationId, LISTING_LIMIT);
        } catch (RuntimeException failure) {
            return "";
        }
        if (found.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder("\n\n## 이 대화에서 찾은 판매 글 (서버가 방금 조회한 실제 값)\n\n");
        for (ListingResponse listing : found) {
            text.append("- 판매 글 %d · %s · %s등급 · %s · kg당 %,d원 · 남은 양 %s%s%n".formatted(
                    listing.listingId(),
                    listing.materialName(),
                    listing.grade(),
                    listing.rootOrigin() == null ? "출처 미상" : switch (listing.rootOrigin()) {
                        case VIRGIN -> "신소재";
                        default -> "폐소재";
                    },
                    listing.pricePerKg(),
                    Quantities.text(listing.availableQuantity()),
                    listing.status() == ListingStatus.ON_SALE ? "" : " · 지금은 살 수 없음(%s)".formatted(listing.status())));
        }
        text.append("""

                purchase_material 의 listingId 는 **이 목록에서만** 고른다. 담당자에게 보여 주고 합의한 소재와
                번호가 같은지 이름으로 확인한다 — 번호를 짐작해 다른 소재를 산 일이 있다. 목록에 없는 소재를
                사려면 먼저 search_listings 로 찾는다. 담당자가 판매 글 번호를 직접 알려 주면 quote_trade 로
                그 글과 금액을 확인해 보여 준다.""");
        return text.toString();
    }

    /**
     * 지금 가진 소재. <b>번호와 이름과 사용 가능량을 서버가 준다.</b>
     *
     * <p>이게 없던 동안 모델은 소재 번호를 <b>대화 기억</b>에서 꺼냈다. 가공을 거치면 결과물이
     * 새 번호로 발행되는데, 모델은 가공 전 번호(34·35·36)를 넘겼고 실제 보유는 40·41·42 였다.
     * 도구가 막아 주긴 했지만 모델은 한 번 틀리고 거절당한 뒤에야 고쳤고, 담당자는 그동안 같은
     * 오류를 두 번 봤다.
     *
     * <p><b>이 프로젝트의 소재를 먼저, 따로 적는다.</b> 섞어 두면 모델이 홈페이지에서 산 다른
     * 소재를 이 제품에 넣자고 먼저 꺼낸다. 그 밖의 소재는 담당자가 쓰겠다고 할 때 고를 거리다.
     *
     * <p>양은 {@link Quantities#text} 로 준다. 모델이 gram 을 직접 환산하다 94,000g 을 "94g"
     * 이라고 말한 적이 있다.
     */
    private String holdings(Long userId, AgentProject project) {
        List<MyMaterialResponse> held;
        try {
            held = materialQuery.findMyMaterials(userId).stream()
                    .filter(material -> material.available() > 0)
                    .sorted(Comparator.comparing(MyMaterialResponse::materialId).reversed())
                    .toList();
        } catch (RuntimeException failure) {
            // 보유 조회가 흔들려도 대화는 이어져야 한다. 모델은 get_my_materials 로 볼 수 있다.
            return "";
        }
        List<MyMaterialResponse> mine = held.stream()
                .filter(material -> project.ownsMaterial(material.materialId()))
                .limit(PROJECT_MATERIAL_LIMIT)
                .toList();
        List<MyMaterialResponse> others = held.stream()
                .filter(material -> !project.ownsMaterial(material.materialId()))
                .limit(OTHER_MATERIAL_LIMIT)
                .toList();
        if (mine.isEmpty() && others.isEmpty()) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        if (!mine.isEmpty()) {
            text.append("\n\n## 이 프로젝트의 소재 (서버가 방금 조회한 실제 값)\n\n");
            mine.forEach(material -> text.append(line(material)));
        }
        if (!others.isEmpty()) {
            text.append(mine.isEmpty()
                    ? "\n\n## 지금 보유한 소재 (서버가 방금 조회한 실제 값)\n\n"
                    : "\n### 그 밖에 보유한 소재 (이 프로젝트 밖)\n\n");
            others.forEach(material -> text.append(line(material)));
        }
        text.append("""

                소재 번호는 **이 목록에서만** 고른다. 가공을 거친 소재는 결과물이 새 번호로
                발행되고 원래 소재는 줄어든다 — 앞선 대화에 나온 번호를 그대로 쓰지 않는다.
                양은 위 문장을 그대로 옮기고 직접 환산하지 않는다.""");
        if (!mine.isEmpty() && !others.isEmpty()) {
            text.append("""

                    가공과 생산에는 이 프로젝트의 소재를 쓴다. 프로젝트 밖의 소재는 담당자가 쓰겠다고
                    할 때만 쓰고, 먼저 권하지 않는다.""");
        }
        return text.toString();
    }

    private String line(MyMaterialResponse material) {
        return "- 소재 %d · %s · %s · 사용 가능 %s%s%n".formatted(
                material.materialId(),
                material.name(),
                originLabel(material),
                Quantities.text(material.available()),
                processedLabel(material));
    }

    private String originLabel(MyMaterialResponse material) {
        return material.rootOrigin() == null ? "출처 미상" : switch (material.rootOrigin()) {
            case VIRGIN -> "신소재";
            default -> "폐소재";
        };
    }

    private String processedLabel(MyMaterialResponse material) {
        if (material.completedProcessTypes() == null || material.completedProcessTypes().isEmpty()) {
            return "";
        }
        return " · 거친 공정 " + material.completedProcessTypes().stream()
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    /**
     * 이미 담당자 화면에 떠 있는 카드.
     *
     * <p><b>위의 할 일 목록은 카드가 떠 있는지 모른다.</b> 그래서 그 목록만 보면 모델은
     * 이미 띄워 둔 카드를 한 번 더 띄우려 한다. 실제로 그랬다 — 서버가 온체인 기록 카드를
     * 자동으로 띄운 뒤, 모델이 같은 거래에 카드를 하나 더 만들었다. 담당자는 한쪽만 누르고
     * 나머지는 서명이 다 끝난 뒤에도 계속 떠 있었다.
     *
     * <p>중복 자체는 서버가 막지만({@code AgentToolExecutor}), 막힌 호출은 턴 한 번을
     * 쓰고 모델을 헷갈리게 한다. 여기서 미리 알려 주면 그 호출을 아예 하지 않는다.
     */
    private String alreadyOnScreen(Long conversationId) {
        List<AgentPendingAction> open = openCards.of(conversationId);
        if (open.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder(
                "\n\n### 이미 담당자 화면에 떠 있는 카드 (다시 띄우지 말 것)\n\n");
        for (AgentPendingAction card : open) {
            text.append("- %s — %s%n".formatted(card.getToolName(), card.getSummary()));
        }
        text.append("""

                이 카드들은 담당자가 아직 누르지 않았을 뿐, 이미 띄워 둔 것이다. 같은 일에
                도구를 다시 부르지 말고 위에 떠 있는 카드를 확인해 달라고만 안내한다.""");
        return text.toString();
    }

    private String render(List<BrandStep> steps) {
        return steps.stream()
                .map(step -> "- %s %d(%s): %s"
                        .formatted(domainLabel(step), step.referenceId(), step.subject(), step.instruction()))
                .collect(Collectors.joining(System.lineSeparator()))
                + (steps.isEmpty() ? "" : System.lineSeparator());
    }

    private String nextLine(BrandStep step) {
        String line = "%s %d(%s) — %s".formatted(
                domainLabel(step), step.referenceId(), step.subject(), step.label());
        return step.toolName() == null ? line : line + " → " + step.toolName();
    }

    /** 번호가 무엇의 번호인지 밝힌다. 모델이 거래 번호를 배치 번호로 쓴 적이 있다. */
    private String domainLabel(BrandStep step) {
        return switch (step.domain()) {
            case TRADE -> "거래";
            case PROCESSING -> "가공 작업";
            case PRODUCTION -> "생산 배치";
            case PRODUCT -> "제품";
        };
    }
}
