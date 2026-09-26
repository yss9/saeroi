package com.ssafy.upcycle.agent.tool.trade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.service.CompanyNames;
import com.ssafy.upcycle.agent.project.AgentProject;
import com.ssafy.upcycle.agent.service.ConversationListings;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolArgumentException;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.common.unit.Quantities;
import com.ssafy.upcycle.material.dto.MaterialQuantityUnit;
import com.ssafy.upcycle.trade.dto.ListingResponse;
import com.ssafy.upcycle.trade.dto.PaymentReadyResponse;
import com.ssafy.upcycle.trade.dto.TradeCreateRequest;
import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.entity.ListingStatus;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.trade.repository.ListingSearchQueryRepository;
import com.ssafy.upcycle.trade.service.PaymentService;
import com.ssafy.upcycle.trade.service.TradeCommandService;
import com.ssafy.upcycle.trade.service.TradePricing;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 소재를 구매한다. <b>거래 생성과 결제를 한 카드로 묶는다.</b>
 *
 * <p>거래 생성 자체는 돈이 나가지 않으니 원칙대로면 카드 없이 실행할 수 있다. 그런데 거래를
 * 만들면 <b>판매 잔여 수량이 즉시 줄어들어</b> 다른 구매자가 못 사게 된다
 * ({@code TradeController} 주석). 그리고 담당자 입장에서 "산다" 는 하나의 행동이다.
 *
 * <p>묶으면 카드를 거절했을 때 거래 자체가 만들어지지 않는다 — 결제되지 않은 유령 거래가
 * 남지 않는다. (만료 워커가 60초마다 정리하긴 하지만, 애초에 안 만드는 편이 낫다.)
 *
 * <p>담당자가 승인하면 {@link #execute} 가 불려 거래를 만들고 결제 세션을 연다. 화면은
 * 응답의 {@code redirectUrl} 로 카카오페이를 띄운다.
 */
@Component
public class PurchaseMaterialTool extends AbstractAgentTool {

    private final TradeCommandService tradeCommand;
    private final PaymentService paymentService;

    /**
     * 카드 전에 판매 글을 볼 때 쓴다. <b>서비스가 아니라 저장소를 읽는다</b> — 서비스의 단건 조회는
     * 없는 번호에 예외를 던지는 트랜잭션 메서드라, 카드를 만드는 트랜잭션 안에서 부르면 잡아도
     * 그 트랜잭션이 rollback-only 가 된다. 실제로 모델이 없는 번호를 넣자 그 턴의 기록이 통째로
     * 사라지고 담당자는 오류 화면을 봤다. 저장소는 없으면 {@code null} 을 돌려준다.
     */
    private final ListingSearchQueryRepository listings;
    private final ConversationListings conversationListings;
    /** 결제 카드의 판매자 줄은 업체명이다 — 대표자 개인 이름이 뜨면 누구에게서 사는지 알 수 없다. */
    private final CompanyNames companyNames;

    public PurchaseMaterialTool(
            ObjectMapper objectMapper,
            TradeCommandService tradeCommand,
            PaymentService paymentService,
            ListingSearchQueryRepository listings,
            ConversationListings conversationListings,
            CompanyNames companyNames) {
        super(objectMapper);
        this.tradeCommand = tradeCommand;
        this.paymentService = paymentService;
        this.listings = listings;
        this.conversationListings = conversationListings;
        this.companyNames = companyNames;
    }

    /**
     * <b>살 수 있을 때만 결제 카드를 띄운다.</b> 결제 카드는 누른 뒤에 실패하면 가장 아프다 —
     * 담당자는 금액까지 확인하고 눌렀는데 오류를 본다.
     *
     * <p>거래를 만들 때 도메인이 거절하는 경우를 그대로 옮겼다({@code TradeCommandService.create}).
     * 모두 모델이 고칠 수 있는 것들이다 — 다른 판매 글을 찾거나 수량을 줄이면 된다. 승인 시점에는
     * 도메인이 다시 본다.
     */
    @Override
    public Optional<AgentToolResult> refuseBeforeProposal(AgentToolContext context, JsonNode arguments) {
        long listingId = requiredPositiveLong(arguments, "listingId");
        long quantityGram = requiredPositiveLong(arguments, "quantityGram");
        ListingResponse listing = listings.findDetail(listingId);
        if (listing == null) {
            return refusal("LISTING_NOT_FOUND", listingId,
                    "그런 판매 글이 없습니다. 번호를 짐작하지 말고 search_listings 로 다시 찾으세요.");
        }
        // **이 대화에서 찾아 보여 준 판매 글만 산다.** 이력이 잘려 검색 결과가 사라지자 모델이 번호를
        // 짐작했다 — 폐데님 원단(21번)으로 합의하고 본 적 없는 19번(깨끗한 데님)으로 결제 카드를 띄워,
        // 담당자는 6,000원에 동의했는데 15,000원이 결제됐다. 짐작한 번호가 실제로 있는 판매 글이면
        // 위의 확인으로는 걸리지 않는다.
        Long conversationId = context.conversation().getId();
        if (!conversationListings.seen(conversationId).contains(listingId)) {
            List<ListingResponse> shown = conversationListings.current(conversationId, 10);
            // 이름이 비어 있어도 거절문은 만들어져야 한다. 여기서 터지면 실행기는 "막을 이유가 없다"
            // 로 보고 카드를 그대로 띄운다 — 막으려던 바로 그 카드다.
            String name = listing.materialName() == null ? "소재" : listing.materialName();
            return Optional.of(AgentToolResult.of(json(Map.of(
                    "error", "LISTING_NOT_SHOWN",
                    "listingId", listingId,
                    "listingName", name,
                    "shownListings", shown.stream()
                            .map(each -> "%d번 %s".formatted(each.listingId(), each.materialName()))
                            .toList(),
                    "hint", "%d번(%s)은 이 대화에서 찾아 담당자에게 보여 준 판매 글이 아닙니다. 합의한 소재의 번호는 "
                            .formatted(listingId, name)
                            + "shownListings 에 있습니다. 거기 없는 소재라면 먼저 search_listings 로 찾아 담당자에게 "
                            + "보여 주세요. 담당자가 이 번호를 직접 알려 준 것이라면 quote_trade 로 이 판매 글과 금액을 "
                            + "확인해 보여 준 뒤 다시 부르면 됩니다."))));
        }
        if (listing.status() != ListingStatus.ON_SALE) {
            return refusal("LISTING_NOT_ON_SALE", listingId,
                    "이 판매 글은 지금 살 수 없습니다(%s). 담당자에게 알리고 search_listings 로 다른 글을 찾으세요."
                            .formatted(listing.status()));
        }
        if (context.userId().equals(listing.sellerId())) {
            return refusal("SELF_PURCHASE", listingId, "담당자 자신의 판매 글이라 살 수 없습니다.");
        }
        if (quantityGram > listing.availableQuantity()) {
            return Optional.of(AgentToolResult.of(json(Map.of(
                    "error", "QUANTITY_EXCEEDS_AVAILABLE",
                    "listingId", listingId,
                    "availableText", Quantities.text(listing.availableQuantity()),
                    "hint", "이 판매 글에 남은 양은 %s 입니다. 담당자에게 알리고 수량을 줄이거나 다른 판매 글을 찾으세요."
                            .formatted(Quantities.text(listing.availableQuantity()))))));
        }
        return Optional.empty();
    }

    private Optional<AgentToolResult> refusal(String error, long listingId, String hint) {
        return Optional.of(AgentToolResult.of(json(Map.of("error", error, "listingId", listingId, "hint", hint))));
    }

    /**
     * 카드에 <b>서버가 계산한 금액</b>을 싣는다.
     *
     * <p>이것이 없으면 담당자가 승인 전에 보는 금액은 모델이 쓴 요약뿐이다. 실제로 12.5kg ×
     * 2,000원/kg 을 <b>25,000,000원</b>이라고 적은 적이 있다 — gram 수량(12,500)에 kg 단가를
     * 곱했다. 서버는 금액을 모델에게서 받지 않으므로 실제 결제는 25,000원으로 멀쩡했겠지만,
     * 담당자는 2,500만원짜리 카드를 보고 승인 여부를 정하게 된다.
     */
    @Override
    public Map<String, Object> payloadExtras(AgentToolContext context, JsonNode arguments) {
        // **누구에게서 무엇을 사는지도 싣는다.** 판매자와 등급이 카드에 없으면, 모델이 말하지 않았을 때 담당자가
        // 확인할 곳이 없다 — 판매 글이 한 곳뿐이자 모델은 "폐데님 원단 10kg : 20,000원" 만 말했다.
        return quote(arguments)
                .map(quote -> {
                    Map<String, Object> extras = new LinkedHashMap<>();
                    extras.put("totalPrice", quote.totalPrice());
                    extras.put("pricePerKg", quote.pricePerKg());
                    extras.put("materialName", quote.materialName());
                    if (quote.sellerName() != null) {
                        extras.put("sellerName", quote.sellerName());
                    }
                    if (quote.grade() != null) {
                        extras.put("grade", quote.grade());
                    }
                    extras.put("originText", quote.originText());
                    return extras;
                })
                .orElse(Map.of());
    }

    /**
     * 카드 제목도 서버가 쓴다. 금액이 들어가는 줄이라 모델에게 맡기면 틀린 숫자가 동의 기록으로
     * 남는다 — 실제로 "폐데님 원단 12.5kg 구매 (25,000,000원)" 이 그렇게 남을 뻔했다.
     */
    @Override
    public Optional<String> serverSummary(AgentToolContext context, JsonNode arguments) {
        return quote(arguments).map(quote -> "%s %s 구매 · %,d원".formatted(
                quote.materialName(),
                Quantities.text(quote.quantityGram()),
                quote.totalPrice()));
    }

    /** 판매 글에서 이 수량을 살 때의 값. 카드 요약과 부가 정보가 같은 계산을 쓴다. */
    private Optional<Quote> quote(JsonNode arguments) {
        long listingId = requiredPositiveLong(arguments, "listingId");
        long quantityGram = requiredPositiveLong(arguments, "quantityGram");
        ListingResponse listing = listings.findDetail(listingId);
        if (listing == null || listing.pricePerKg() <= 0) {
            return Optional.empty();
        }
        // **계산은 TradePricing 에만 있다.** 여기서 따로 곱하면 반올림 방향이 달라져 카드에
        // 보인 금액과 실제 청구가 어긋난다 — 그 차이는 결제 승인 검증에서 실패로 나타난다.
        return Optional.of(new Quote(
                listing.materialName() == null ? "소재" : listing.materialName(),
                quantityGram,
                listing.pricePerKg(),
                TradePricing.totalPrice(quantityGram, listing.pricePerKg()),
                companyNames.of(listing.sellerId(), listing.sellerName()),
                listing.grade(),
                listing.rootOrigin() == MaterialRootOrigin.VIRGIN ? "신소재" : "폐소재"));
    }

    private record Quote(
            String materialName, long quantityGram, long pricePerKg, long totalPrice,
            String sellerName, String grade, String originText) {
    }

    /** 대화에서 산 거래는 이 프로젝트의 것이다. 소재는 거래에서 따라온다({@code AgentProjects}). */
    @Override
    public void claim(JsonNode arguments, JsonNode result, AgentProject.Builder project) {
        project.trade(AgentProject.idAt(result, "tradeId"));
    }

    @Override
    public String name() {
        return "purchase_material";
    }

    @Override
    public String description() {
        return "판매 글에서 소재를 구매한다. 담당자에게 결제 확인 카드를 띄운다. "
                + "부르기 전에 수량과 배송지를 대화로 합의하고, get_shipping_default 로 기본 배송지를 확인하라. "
                + "카드를 띄운 뒤에는 결과를 기다린다 — 결제됐다고 가정하고 다음 단계로 넘어가면 안 된다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "listingId": {"type": "integer", "description": "구매할 판매 글"},
                    "quantityGram": {"type": "integer", "minimum": 1,
                      "description": "구매 수량(gram). 판매 글의 전량이 아니어도 된다"},
                    "receiverName": {"type": "string"},
                    "receiverPhone": {"type": "string"},
                    "zipCode": {"type": "string"},
                    "address": {"type": "string"},
                    "addressDetail": {"type": "string"},
                    "summary": {"type": "string",
                      "description": "담당자가 무엇에 결제하는지 한 줄로. 카드에 그대로 보인다. 예: 실크 원단 1kg 구매 (30,000원)"}
                  },
                  "required": ["listingId","quantityGram","receiverName","receiverPhone","zipCode","address","summary"],
                  "additionalProperties": false
                }""";
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public PendingActionKind pendingKind() {
        return PendingActionKind.PAYMENT;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        long listingId = requiredPositiveLong(arguments, "listingId");
        long quantityGram = requiredPositiveLong(arguments, "quantityGram");

        // 금액을 인자로 받지 않는다. TradeCreateRequest 도 같은 이유로 totalPrice 를 받지
        // 않는다 — 클라이언트(여기서는 모델)가 금액을 정하면 1원짜리 거래를 만들 수 있다.
        TradeCreateRequest request = new TradeCreateRequest(
                listingId,
                quantityGram,
                MaterialQuantityUnit.G,
                PaymentMethod.KAKAO_PAY,
                requiredText(arguments, "receiverName"),
                requiredText(arguments, "receiverPhone"),
                requiredText(arguments, "zipCode"),
                requiredText(arguments, "address"),
                optionalText(arguments, "addressDetail"));

        TradeResponse trade = tradeCommand.create(context.userId(), request);
        // 복귀 주소를 대화창으로 잡는다. 기본값(거래 상세)으로 보내면 결제 후 대화가 사라진다.
        PaymentReadyResponse payment =
                paymentService.ready(context.userId(), trade.tradeId(), true);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tradeId", trade.tradeId());
        payload.put("totalPrice", trade.totalPrice());
        payload.put("quantityGram", quantityGram);
        // 화면이 이 주소로 카카오페이를 띄운다.
        payload.put("redirectUrl", payment.redirectUrl());

        // **문장을 끝맺는다.** 이 값은 ASSISTANT 역할로 이력에 들어가므로, 모델 눈에는 자기가
        // 쓰다 만 말로 보인다. 예전에는 "… 30,000원" 으로 끝나서 모델이 다음 턴에 "이 청구될
        // 예정입니다." 를 이어 붙였고, 화면에는 한 문장이 두 말풍선으로 쪼개져 떴다.
        String summary = "소재 구매 결제를 시작했습니다. %,d원이 청구됩니다.".formatted(trade.totalPrice());
        return AgentToolResult.of(json(payload), summary);
    }

    @Override
    protected String requiredText(JsonNode arguments, String field) {
        String value = super.requiredText(arguments, field);
        if (value.length() > 255) {
            throw new AgentToolArgumentException(field + " 이(가) 너무 깁니다.");
        }
        return value;
    }
}
