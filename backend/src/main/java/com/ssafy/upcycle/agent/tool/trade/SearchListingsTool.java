package com.ssafy.upcycle.agent.tool.trade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolArgumentException;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.trade.dto.ListingResponse;
import com.ssafy.upcycle.trade.dto.ListingSearchCondition;
import com.ssafy.upcycle.trade.service.ListingQueryService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 마켓플레이스에서 판매 중인 소재를 찾는다.
 *
 * <h2>온체인 미등록 판매글을 걸러내지 않는다</h2>
 *
 * <p>{@code chain_listing_id} 가 {@code NULL} 인 판매글은 "등록됐지만 아직 온체인에 없음"
 * 이라는 <b>플랫폼이 정식으로 지원하는 상태</b>다 ({@code ListingCommandService} 주석). 화면도
 * 그 값을 그대로 받아 구분해 보여준다. 에이전트가 더 엄격한 규칙을 혼자 만들지 않는다.
 *
 * <p>대신 {@code onChain} 플래그를 함께 내려 모델이 담당자에게 차이를 설명하게 한다. 미등록
 * 판매글도 구매·결제·수령까지 정상 동작한다 — 잔액 이전은 DB 작업이라 체인과 무관하다.
 * 다만 온체인 주문 서명은 등록된 것에만 걸 수 있다.
 *
 * <p>걸러내면 체인을 끈 개발 환경에서 검색이 항상 0건이 되어 대화가 막힌다. 구분 표시로 두면
 * 개발과 시연이 같은 코드로 돈다.
 */
@Component
public class SearchListingsTool extends AbstractAgentTool {

    /**
     * 한 번에 돌려줄 후보 수.
     *
     * <p>결과도 입력 토큰이다 — 다음 LLM 호출에 그대로 실려 가고, tool 루프가 도는 동안
     * 반복해서 재전송된다. 담당자가 한 번에 비교할 수 있는 수는 어차피 대여섯 개다.
     */
    private static final int SIZE = 5;

    private final ListingQueryService listingQuery;

    public SearchListingsTool(ObjectMapper objectMapper, ListingQueryService listingQuery) {
        super(objectMapper);
        this.listingQuery = listingQuery;
    }

    @Override
    public String name() {
        return "search_listings";
    }

    @Override
    public String description() {
        return "마켓플레이스에서 판매 중인 소재를 찾는다. 소재 종류, 출처(신소재/폐소재), kg당 가격 상한, "
                + "최소 필요 수량으로 좁힐 수 있다. 결과의 pricePerKg 는 kg당 원화, 수량은 gram 이다. "
                + "onChain 이 false 인 판매글은 구매는 되지만 블록체인 이력이 남지 않는다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "materialType": {"type": "string",
                      "enum": ["DENIM","COTTON","LEATHER","LINING","METAL","MIXED","ETC"]},
                    "rootOrigin": {"type": "string", "enum": ["VIRGIN","WASTE"],
                      "description": "WASTE 는 폐의류·폐원단, VIRGIN 은 새 원단"},
                    "keyword": {"type": "string", "description": "소재명 검색어"},
                    "maxPricePerKg": {"type": "integer", "description": "kg당 가격 상한(원)"},
                    "minAvailableQuantity": {"type": "integer",
                      "description": "이만큼(gram) 이상 남아 있는 판매 글만"},
                    "page": {"type": "integer", "minimum": 0, "default": 0}
                  },
                  "additionalProperties": false
                }""";
    }

    @Override
    public Set<JourneyPhase> phases() {
        return Set.of(JourneyPhase.MATERIAL_SOURCING);
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        Long page = optionalLong(arguments, "page");

        ListingSearchCondition condition = new ListingSearchCondition(
                null, // status — 기본값(ON_SALE)에 맡긴다. 살 수 없는 글을 후보로 올리지 않는다
                null, // sellerId
                null, // materialId
                enumValue(arguments, "materialType", MaterialType.class),
                enumValue(arguments, "rootOrigin", MaterialRootOrigin.class),
                null, // generationType
                null, // grade
                optionalText(arguments, "keyword"),
                null, // minPricePerKg
                optionalLong(arguments, "maxPricePerKg"),
                optionalLong(arguments, "minAvailableQuantity"),
                null, // sort
                page == null ? 0 : page.intValue(),
                SIZE);

        var result = listingQuery.search(condition);
        if (result.content().isEmpty()) {
            // 0건은 장애가 아니다. 모델이 조건을 어떻게 풀지 제안할 수 있게 사실을 돌려준다.
            return AgentToolResult.of(json(Map.of(
                    "listings", List.of(),
                    "total", 0,
                    "hint", "조건에 맞는 판매 글이 없습니다. 가격 상한을 올리거나 소재 종류를 넓혀보세요.")));
        }

        List<Candidate> candidates = result.content().stream().map(Candidate::from).toList();
        long onChainCount = candidates.stream().filter(Candidate::onChain).count();

        return AgentToolResult.of(json(Map.of(
                "listings", candidates,
                "total", result.totalElements(),
                "onChainCount", onChainCount,
                "note", onChainCount == candidates.size()
                        ? "모두 블록체인 기록이 가능한 판매 글입니다."
                        : "onChain 이 false 인 판매 글은 구매는 되지만 블록체인 이력이 남지 않습니다. "
                                + "담당자에게 이 차이를 알려주세요.")));
    }

    private <E extends Enum<E>> E enumValue(JsonNode arguments, String field, Class<E> type) {
        String raw = optionalText(arguments, field);
        if (raw == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException unknown) {
            throw new AgentToolArgumentException("알 수 없는 " + field + " 값입니다: " + raw);
        }
    }

    /**
     * 모델에게 줄 후보 한 건.
     *
     * <p>{@code ListingResponse} 를 그대로 내보내지 않는다. 필드가 많아 입력 토큰이 늘고,
     * 모델이 결정에 쓰지 않는 값이 섞이면 오히려 고르기 어려워진다. 크레딧이 토큰당 차감이라
     * 이 차이가 곧 비용이다.
     */
    private record Candidate(
            Long listingId,
            String materialName,
            String materialType,
            String rootOrigin,
            String grade,
            long pricePerKg,
            long availableQuantityGram,
            String sellerName,
            boolean onChain) {

        static Candidate from(ListingResponse listing) {
            return new Candidate(
                    listing.listingId(),
                    listing.materialName(),
                    listing.materialType() == null ? null : listing.materialType().name(),
                    listing.rootOrigin() == null ? null : listing.rootOrigin().name(),
                    listing.grade(),
                    listing.pricePerKg(),
                    listing.availableQuantity(),
                    listing.sellerName(),
                    listing.chainListingId() != null);
        }
    }
}
