package com.ssafy.upcycle.agent.service.step;

import java.util.Map;
import java.util.Optional;

/**
 * 모델 없이 <b>서버가 바로 띄울 수 있는</b> 카드인가.
 *
 * <h2>왜 모델을 거치지 않나</h2>
 *
 * <p>상대(공급업체·가공업체)가 움직여 담당자 차례가 되는 순간, 대화창은 조용하다. 담당자가
 * 말을 걸어야 모델이 상태를 보고 카드를 띄운다. 그런데 <b>담당자는 자기 차례가 됐다는 것을
 * 모른다</b> — 온체인 등록이나 인계 서명 같은 단계가 있다는 것조차 모르기 때문이다.
 *
 * <p>그래서 서버가 직접 띄운다. 이런 단계는 <b>무엇을 할지 이미 정해져 있다.</b> 어느
 * 거래·작업·배치인지, 어떤 도구를 부를지, 무엇에 서명하는지가 전부 상태에서 나온다. 모델이
 * 고를 것이 없으므로 모델을 부를 이유도 없다 — 크레딧도 들지 않는다.
 *
 * <h2>무엇을 자동으로 띄우지 않나</h2>
 *
 * <p><b>담당자에게 물어야 채워지는 인자가 있으면 띄우지 않는다.</b> 소재 구매(무엇을 얼마나),
 * 배치 열기(이름과 수량), 소재 투입(제품·자투리·폐기 3분할), 제품 등록(이름·가격·분류)이
 * 그렇다. 그건 대화로 정한 뒤 모델이 띄우는 것이 맞다.
 *
 * <p>생산 확정도 뺐다. 인자는 정해지지만 <b>되돌릴 수 없는 지점</b>이라, 담당자가 먼저 말을
 * 꺼내지 않았는데 확정 카드가 떠 있는 것은 재촉으로 읽힌다.
 *
 * <h2>카드의 종류는 여기 적지 않는다</h2>
 *
 * <p>서명 카드인지 확인 카드인지, 서명이 몇 번인지는 <b>도구가 이미 알고 있다</b>
 * ({@code pendingKind}, {@code signatureSteps}). 여기에 한 벌 더 적어 두면 도구가 바뀔 때
 * 한쪽만 고쳐져, 같은 작업이 어느 문으로 들어왔는지에 따라 다른 카드로 뜬다.
 */
public enum AutoProposableStep {

    REQUEST_CHAIN_ORDER("request_chain_order", "%s 구매를 블록체인에 기록"),
    CONFIRM_RECEIPT("confirm_receipt", "%s 수령 확정"),
    // 가공의 이름은 "울 원단 5kg 가공" 처럼 이미 "가공" 으로 끝난다(BrandStepResolver). 여기서 또 붙이면
    // "울 원단 5kg 가공 가공 결과 승인" 이 된다.
    REGISTER_PROCESSING_CHAIN_JOB("register_processing_chain_job", "%s — 작업 등록 서명",
            "소재를 보내려면 서명이 두 번 필요합니다. 먼저 이 작업 등록 서명으로 보낼 소재를 이 가공 작업에 묶어 "
                    + "둡니다 — 이것만으로 소재가 나가지는 않습니다. 업체가 작업을 확인하면 소재 보내기 서명을 합니다."),
    REQUEST_CUSTODY_TRANSFER("request_custody_transfer", "%s — 소재 보내기 서명",
            "업체가 작업을 확인했습니다. 이 두 번째 서명을 하면 소재가 업체로 넘어갑니다."),
    APPROVE_PROCESSING_RESULT("approve_processing_result", "%s — 결과 승인"),
    RECORD_PRODUCTION_CHAIN("record_production_chain", "%s 생산 기록을 블록체인에 남기기"),
    SET_PRODUCT_ON_SALE("set_product_on_sale", "%s 판매 시작");

    private final String toolName;
    private final String summaryFormat;
    /**
     * 카드가 뜰 때 대화에 남기는 안내에 덧붙일 이유. 없으면 비운다.
     *
     * <p>가공의 서명 두 번은 담당자가 모르는 절차다. 소재를 보내려는데 "작업 등록 서명" 카드가 먼저 뜨자 담당자는
     * "소재를 보내야 하는데 브랜드에서 다른 서명을 하는 것 같다, 이게 무슨 과정이냐" 고 물었다.
     */
    private final String why;

    AutoProposableStep(String toolName, String summaryFormat) {
        this(toolName, summaryFormat, null);
    }

    AutoProposableStep(String toolName, String summaryFormat, String why) {
        this.toolName = toolName;
        this.summaryFormat = summaryFormat;
        this.why = why;
    }

    /** 안내에 덧붙일 이유. 없으면 빈 값이다. */
    public Optional<String> why() {
        return Optional.ofNullable(why);
    }

    public static Optional<AutoProposableStep> of(String toolName) {
        for (AutoProposableStep candidate : values()) {
            if (candidate.toolName.equals(toolName)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public String toolName() {
        return toolName;
    }

    /** 카드에 보일 한 줄. 담당자가 무엇에 서명하는지 알아야 한다. */
    public String summaryFor(String subject) {
        return summaryFormat.formatted(subject);
    }

    /**
     * 도구에 넘길 인자.
     *
     * <p>모델이 만든 인자와 같은 모양이어야 한다 — 승인 경로가 그것을 그대로 읽는다. 식별자
     * 이름이 도구마다 다르므로 여기서 갈라 준다.
     */
    public Map<String, Object> argumentsFor(long referenceId, String summary) {
        String idField = switch (this) {
            case REQUEST_CHAIN_ORDER, CONFIRM_RECEIPT -> "tradeId";
            case REGISTER_PROCESSING_CHAIN_JOB, REQUEST_CUSTODY_TRANSFER, APPROVE_PROCESSING_RESULT -> "jobId";
            case RECORD_PRODUCTION_CHAIN -> "batchId";
            case SET_PRODUCT_ON_SALE -> "productModelId";
        };
        return Map.of(idField, referenceId, "summary", summary);
    }
}
