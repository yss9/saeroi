package com.ssafy.upcycle.trade.payment;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.trade.TradeErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오페이 단건 결제 연동.
 *
 * <pre>
 *   ready   → tid + 결제창 주소       (돈 안 움직임)
 *   사용자가 결제창에서 인증 → pg_token 을 들고 복귀
 *   approve → 실제 청구
 *   cancel  → 환불
 * </pre>
 *
 * <p><b>인증은 {@code Authorization: SECRET_KEY {키}} 헤더다.</b> 예전 문서에 나오는
 * {@code KakaoAK} + Admin key 방식은 구 API({@code kapi.kakao.com})용이라 지금 호스트에서는
 * 통하지 않는다.
 *
 * <p><b>금액을 {@code long} 으로만 다룬다.</b> 원 단위 정수이고 소수가 없다. 실수형으로 받으면
 * 큰 금액에서 정밀도가 깨지는데 그게 결제 금액이면 그대로 사고다.
 *
 * <p>예외를 그대로 흘려보내지 않고 {@link TradeErrorCode#PAYMENT_GATEWAY_FAILED} 로 바꾼다.
 * PG 의 원문 오류에는 가맹점 코드 같은 내부 정보가 섞여 나올 수 있어 사용자에게 보이면 안 된다.
 * 진단에 필요한 원문은 로그에만 남긴다.
 */
public class KakaoPayGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(KakaoPayGateway.class);

    private static final String READY_PATH = "/online/v1/payment/ready";
    private static final String APPROVE_PATH = "/online/v1/payment/approve";
    private static final String CANCEL_PATH = "/online/v1/payment/cancel";

    private final KakaoPayProperties properties;
    private final RestClient restClient;

    public KakaoPayGateway(KakaoPayProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "SECRET_KEY " + properties.secretKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public PaymentReady ready(PaymentReadyCommand command) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cid", properties.cid());
        body.put("partner_order_id", command.orderId());
        body.put("partner_user_id", command.userId());
        body.put("item_name", command.itemName());
        // 수량은 항상 1 이다. 소재의 kg 수량을 여기 넣으면 카카오가 단가 × 수량으로 표시해
        // 우리가 계산한 총액과 다른 금액이 결제창에 뜬다.
        body.put("quantity", 1);
        body.put("total_amount", command.totalPrice());
        body.put("tax_free_amount", 0);
        // 세 주소가 같다. 결제창이 어느 경우든 같은 화면으로 돌려보내고, 그 화면이
        // pg_token 유무로 승인/취소를 구분한다 — 취소·실패 경로에는 토큰이 없다.
        body.put("approval_url", command.returnUrl());
        body.put("cancel_url", command.returnUrl());
        body.put("fail_url", command.returnUrl());

        Map<?, ?> response = post(READY_PATH, body, "ready");
        String tid = stringOf(response, "tid");
        String redirectUrl = stringOf(response, "next_redirect_pc_url");
        if (tid == null || redirectUrl == null) {
            log.error("KakaoPay ready response missing tid or redirect url: {}", response);
            throw new BusinessException(TradeErrorCode.PAYMENT_GATEWAY_FAILED);
        }
        return new PaymentReady(tid, redirectUrl);
    }

    @Override
    public PaymentApproval approve(PaymentApproveCommand command) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cid", properties.cid());
        body.put("tid", command.tid());
        body.put("partner_order_id", command.orderId());
        body.put("partner_user_id", command.userId());
        body.put("pg_token", command.pgToken());

        Map<?, ?> response = post(APPROVE_PATH, body, "approve");
        return new PaymentApproval(
                stringOf(response, "tid"),
                approvedAmountOf(response),
                approvedAtOf(response));
    }

    @Override
    public void cancel(PaymentCancelCommand command) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cid", properties.cid());
        body.put("tid", command.tid());
        body.put("cancel_amount", command.cancelAmount());
        body.put("cancel_tax_free_amount", 0);

        post(CANCEL_PATH, body, "cancel");
    }

    @Override
    public boolean isLive() {
        return true;
    }

    private Map<?, ?> post(String path, Map<String, Object> body, String operation) {
        try {
            Map<?, ?> response = restClient.post().uri(path).body(body).retrieve().body(Map.class);
            if (response == null) {
                throw new RestClientException("empty body");
            }
            return response;
        } catch (HttpStatusCodeException rejected) {
            // 카카오가 무엇을 거부했는지는 응답 본문에만 있다. 스택 트레이스에 묻히면
            // 진단이 불가능하므로 **상태코드와 본문을 한 줄로** 남긴다.
            // 본문에 cid 가 섞여 나올 수 있으나 cid 는 비밀값이 아니다. 시크릿 키는
            // 요청 헤더에만 있고 응답에는 실리지 않는다.
            log.error("KakaoPay {} rejected: status={} body={}",
                    operation, rejected.getStatusCode(), rejected.getResponseBodyAsString());
            throw new BusinessException(TradeErrorCode.PAYMENT_GATEWAY_FAILED);
        } catch (RestClientException failure) {
            // 응답 자체가 없는 경우 — 네트워크·타임아웃·호스트 오류.
            log.error("KakaoPay {} failed to reach the gateway: {}",
                    operation, failure.getMessage());
            throw new BusinessException(TradeErrorCode.PAYMENT_GATEWAY_FAILED);
        }
    }

    /**
     * 승인 금액은 {@code amount.total} 에 들어 있다.
     *
     * <p>{@link Number#longValue()} 로 꺼내는 것은 Jackson 이 정수를 {@code Integer} 로 줄 수도
     * {@code Long} 으로 줄 수도 있어서다. 캐스팅하면 값에 따라 {@code ClassCastException} 이
     * 난다.
     */
    private static long approvedAmountOf(Map<?, ?> response) {
        Object amount = response.get("amount");
        if (amount instanceof Map<?, ?> amountMap
                && amountMap.get("total") instanceof Number total) {
            return total.longValue();
        }
        log.error("KakaoPay approve response missing amount.total: {}", response);
        throw new BusinessException(TradeErrorCode.PAYMENT_GATEWAY_FAILED);
    }

    /**
     * 승인 시각. 파싱에 실패해도 결제 자체는 성공한 것이므로 실패시키지 않고 현재 시각을 쓴다 —
     * 여기서 예외를 던지면 <b>돈은 빠져나갔는데 거래는 미결제로 남는다.</b>
     */
    private static LocalDateTime approvedAtOf(Map<?, ?> response) {
        String approvedAt = stringOf(response, "approved_at");
        if (approvedAt == null) {
            return LocalDateTime.now();
        }
        try {
            return OffsetDateTime.parse(approvedAt).toLocalDateTime();
        } catch (DateTimeParseException notOffset) {
            try {
                return LocalDateTime.parse(approvedAt);
            } catch (DateTimeParseException unparseable) {
                log.warn("KakaoPay approved_at not parseable: {}", approvedAt);
                return LocalDateTime.now();
            }
        }
    }

    private static String stringOf(Map<?, ?> response, String key) {
        Object value = response.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
