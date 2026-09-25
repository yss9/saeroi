package com.ssafy.upcycle.agent.tool.production;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.upcycle.common.unit.Quantities;

/**
 * 투입 한 줄 — 소재 하나를 <b>얼마 투입했고 그중 얼마가 제품에 들어가고, 얼마가 자투리로 남고, 얼마를 폐기했는지.</b>
 *
 * <h2>담당자가 말한 숫자를 그대로 받는다</h2>
 *
 * <p>담당자는 두 가지로 말한다. "5kg 넣었고 0.5kg 남고 0.5kg 버렸어" 처럼 <b>투입량</b>을 말하기도 하고,
 * "4kg 썼고 0.5kg 남고 0.5kg 버렸어" 처럼 <b>제품에 들어간 양</b>을 말하기도 한다. 수동 화면은 뒤쪽으로
 * 받는다(제품 사용·자투리·폐기를 적으면 투입량은 합이다). 모델에게 한쪽으로 바꿔 넘기게 하면 산수를 시키는
 * 것이고, 실제로 틀렸다 — "전부 쓰고 500g 손실" 에 투입을 6,500g 으로 올려 손실을 <b>더해</b> 버렸다.
 *
 * <p>그래서 들은 쪽을 들은 대로 받는다. 투입량만 있으면 제품 사용을, 제품 사용만 있으면 투입량을 서버가 계산한다.
 * <b>둘 다 들었으면 합이 맞는지 본다</b> — 맞지 않으면 담당자가 말한 숫자끼리 어긋난 것이라 모델이 맞춰서는 안
 * 되고 다시 물어야 한다.
 *
 * <h2>자투리와 폐기는 0 이라도 들은 값이어야 한다</h2>
 *
 * <p>빠지면 0 으로 채우지 않는다. 예전에는 빠진 값이 0 이 되어, 묻지도 않은 "제품 5kg + 자투리 0 + 폐기 0" 이
 * 카드에 올라갔다. 그 숫자에서 제품의 업사이클 비율이 나오고 구매자가 그것을 본다.
 */
public record InputBreakdown(
        long materialId, long inputQuantity, long productQuantity, long scrapQuantity, long wasteQuantity,
        String wasteReason) {

    /** 한 줄을 읽은 결과. 둘 중 하나만 있다. */
    public record Read(InputBreakdown line, String problem, String kind) {
        public boolean ok() {
            return line != null;
        }
    }

    public static final String NOT_ASKED = "INPUT_BREAKDOWN_NOT_ASKED";
    public static final String MISMATCH = "INPUT_BREAKDOWN_MISMATCH";
    public static final String LEFTOVER_EXCEEDS_INPUT = "LEFTOVER_EXCEEDS_INPUT";
    public static final String INVALID = "INPUT_BREAKDOWN_INVALID";

    public static Read read(JsonNode input) {
        long materialId = input.path("materialId").asLong();
        Amount scrap = amount(input, "reusableScrapQuantity");
        Amount waste = amount(input, "wasteQuantity");
        Amount total = amount(input, "inputQuantity");
        Amount product = amount(input, "productQuantity");

        if (scrap.invalid() || waste.invalid() || total.invalid() || product.invalid()) {
            return problem(INVALID, "수량은 0 이상의 gram 정수여야 합니다");
        }
        if (scrap.missing() || waste.missing()) {
            return problem(NOT_ASKED, "자투리와 폐기를 담당자에게 묻지 않았습니다 — 없다고 해도 0 을 들은 값으로 넘깁니다");
        }
        if (total.missing() && product.missing()) {
            return problem(NOT_ASKED, "투입량이나 제품에 들어간 양 중 담당자가 말한 것을 넘겨야 합니다");
        }
        long scrapGram = scrap.value();
        long wasteGram = waste.value();
        if (!total.missing() && !product.missing()) {
            long parts = product.value() + scrapGram + wasteGram;
            if (parts != total.value()) {
                return problem(MISMATCH, "투입 %s 인데 제품 %s + 자투리 %s + 폐기 %s = %s 입니다"
                        .formatted(Quantities.text(total.value()), Quantities.text(product.value()),
                                Quantities.text(scrapGram), Quantities.text(wasteGram), Quantities.text(parts)));
            }
        }
        long inputGram = total.missing() ? product.value() + scrapGram + wasteGram : total.value();
        long productGram = product.missing() ? inputGram - scrapGram - wasteGram : product.value();
        if (productGram < 0) {
            return problem(LEFTOVER_EXCEEDS_INPUT, "자투리 %s 와 폐기 %s 의 합이 투입 %s 보다 많습니다"
                    .formatted(Quantities.text(scrapGram), Quantities.text(wasteGram), Quantities.text(inputGram)));
        }
        if (inputGram <= 0) {
            return problem(INVALID, "투입량이 0 입니다");
        }
        String reason = wasteGram > 0 && input.hasNonNull("wasteReason") && !input.get("wasteReason").asText().isBlank()
                ? input.get("wasteReason").asText().strip()
                : null;
        return new Read(new InputBreakdown(materialId, inputGram, productGram, scrapGram, wasteGram, reason), null, null);
    }

    private static Read problem(String kind, String text) {
        return new Read(null, text, kind);
    }

    private record Amount(boolean missing, boolean invalid, long value) {}

    private static Amount amount(JsonNode input, String field) {
        JsonNode node = input.get(field);
        if (node == null || node.isNull()) {
            return new Amount(true, false, 0);
        }
        if (!node.isIntegralNumber() || node.asLong() < 0) {
            return new Amount(false, true, 0);
        }
        return new Amount(false, false, node.asLong());
    }
}
