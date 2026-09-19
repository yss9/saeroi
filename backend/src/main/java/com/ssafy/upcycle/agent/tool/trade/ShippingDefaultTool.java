package com.ssafy.upcycle.agent.tool.trade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 등록된 회사 주소를 배송지 기본값으로 제안한다.
 *
 * <p>{@code TradeCreateRequest} 가 수취인 이름·연락처·우편번호·주소를 <b>전부 필수</b>로
 * 받는다. 채팅에서 주소를 타이핑하게 하면 최악의 경험이고, 오타가 나면 물건이 엉뚱한 곳으로
 * 간다. 회사 프로필에 이미 있는 값을 제안하고 담당자가 한 마디로 확인하게 한다.
 *
 * <p>다른 곳으로 받겠다고 하면 그때만 되묻는다.
 */
@Component
public class ShippingDefaultTool extends AbstractAgentTool {

    private final CompanyProfileRepository companyProfiles;
    private final UserRepository users;

    public ShippingDefaultTool(
            ObjectMapper objectMapper,
            CompanyProfileRepository companyProfiles,
            UserRepository users) {
        super(objectMapper);
        this.companyProfiles = companyProfiles;
        this.users = users;
    }

    @Override
    public String name() {
        return "get_shipping_default";
    }

    @Override
    public String description() {
        return "등록된 회사 주소와 담당자 연락처를 배송지 기본값으로 가져온다. "
                + "소재를 구매하기 전에 이걸 불러 담당자에게 확인받고, 그대로 쓰겠다고 하면 "
                + "purchase_material 에 이 값을 그대로 넘긴다.";
    }

    @Override
    public String parametersJsonSchema() {
        return NO_ARGUMENTS_SCHEMA;
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
        Map<String, Object> payload = new LinkedHashMap<>();

        users.findById(context.userId()).ifPresent(user -> {
            payload.put("receiverName", user.getName());
            payload.put("receiverPhone", user.getPhone());
        });

        companyProfiles.findById(context.userId()).ifPresentOrElse(
                profile -> {
                    payload.put("zipCode", profile.getZipCode());
                    payload.put("address", profile.getAddress());
                    payload.put("addressDetail", profile.getAddressDetail());
                    payload.put("hasDefault", true);
                },
                () -> {
                    payload.put("hasDefault", false);
                    payload.put("hint", "등록된 회사 주소가 없습니다. 담당자에게 배송지를 물어보세요.");
                });

        return AgentToolResult.of(json(payload));
    }
}
