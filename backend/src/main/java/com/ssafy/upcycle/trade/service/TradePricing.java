package com.ssafy.upcycle.trade.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.trade.TradeErrorCode;

/**
 * 거래 금액 계산. <b>이 한 곳에서만 계산한다.</b>
 *
 * <p>수량은 gram 정수이고 가격은 kg 당 원이라 두 단위가 어긋난다. 곱한 뒤 1000 으로 나누는
 * 이 변환이 화면과 서버 양쪽에 흩어지면 반올림 방향이 조금씩 달라져 1 원씩 벌어진다.
 * 그 차이는 결제 승인 금액 검증에서 그대로 실패로 나타난다.
 *
 * <p><b>금액의 기준은 여기다. 컨트랙트가 아니다.</b> {@code Marketplace.sol} 은 가격을
 * 아예 저장하지 않는다 — {@code createListing(materialId, quantity)} 에 가격 인자가 없고
 * {@code Order} 구조체에도 금액 필드가 없다. 결제가 전적으로 오프체인이라 온체인에는
 * 수량과 소유권만 남는다. 그래서 "컨트랙트와 같은 값이어야 한다"는 제약은 존재하지 않는다.
 *
 * <p><b>버림을 쓴다.</b> 프론트의 {@code calculateTotalPrice} 가 {@code Math.floor} 이므로
 * 맞춘다. 사용자에게 미리 보여준 금액과 승인 요청 금액이 다르면 결제가 막힌다.
 * 바꿔야 한다면 두 곳을 같은 커밋에서 바꾼다.
 */
public final class TradePricing {

    /** 가격 단위(kg)와 수량 단위(gram)의 비. */
    private static final long GRAMS_PER_KG = 1_000L;

    private TradePricing() {
    }

    /**
     * 총 거래 금액(원)을 구한다.
     *
     * <p>{@link Math#multiplyExact} 를 쓰는 것은 의도적이다. {@code long} 범위를 넘는 곱셈은
     * 조용히 음수로 감기는데, 그 값이 {@code total_price} 에 들어가면
     * {@code chk_material_trade_values} 위반으로 설명할 수 없는 500 이 된다.
     * 여기서 도메인 오류로 바꿔 400 을 만든다.
     *
     * @param quantityGram 거래 수량 (gram)
     * @param pricePerKg   kg 당 가격 (원)
     */
    public static long totalPrice(long quantityGram, long pricePerKg) {
        try {
            return Math.multiplyExact(quantityGram, pricePerKg) / GRAMS_PER_KG;
        } catch (ArithmeticException overflow) {
            throw new BusinessException(TradeErrorCode.TRADE_PRICE_OVERFLOW);
        }
    }
}
