package com.ssafy.upcycle.trade.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 거래 한 건의 배송 정보. {@code trade_id} 가 UNIQUE 라 <b>거래당 한 건</b>이며,
 * 분할 배송은 지원하지 않는다.
 *
 * <p>배송지는 구매 시점에 확정되고, 송장({@code carrier} · {@code tracking_number})은
 * 판매자가 발송할 때 붙는다. 둘 다 NULL 을 허용하는 이유는 직접 수령이나 화물처럼
 * 송장이 없는 전달 방식도 있기 때문이다.
 *
 * <p>{@code shipped_at} 은 {@code material_trade} 에도 있다. 거래 상태가 넘어간 시각과
 * 물류 기록을 각각 갖는 구조이므로, 발송 처리 시 두 값을 같은 시각으로 함께 남긴다.
 *
 * <p>감사 컬럼이 없다. schema.sql 의 trade_shipping 에는 created_at 도 updated_at 도 없고,
 * 배송 흐름의 시각은 {@code shipped_at} · {@code delivered_at} 이 직접 표현한다.
 */
@Getter
@Entity
@Table(name = "trade_shipping")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeShipping {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_id", nullable = false, unique = true)
    private Long tradeId;

    @Column(name = "receiver_name", nullable = false, length = 100)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    @Column(name = "zip_code", nullable = false, length = 20)
    private String zipCode;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "address_detail", length = 255)
    private String addressDetail;

    /** 택배사. 송장 없이 전달하는 경우 비어 있다. */
    @Column(name = "carrier", length = 50)
    private String carrier;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    /** 판매자가 발송 처리한 시각. */
    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    /** 실제 배송이 완료된 시각. 구매자 수령 확인 시 남긴다. */
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    private TradeShipping(
            Long tradeId,
            String receiverName,
            String receiverPhone,
            String zipCode,
            String address,
            String addressDetail) {
        this.tradeId = requireNonNull(tradeId, "trade id");
        this.receiverName = requireText(receiverName, "receiver name", 100);
        this.receiverPhone = requireText(receiverPhone, "receiver phone", 20);
        this.zipCode = requireText(zipCode, "zip code", 20);
        this.address = requireText(address, "address", 255);
        this.addressDetail = requireOptionalText(addressDetail, "address detail", 255);
    }

    /** 구매 시점에 배송지를 확정한다. 송장은 아직 없다. */
    public static TradeShipping of(
            Long tradeId,
            String receiverName,
            String receiverPhone,
            String zipCode,
            String address,
            String addressDetail) {
        return new TradeShipping(
                tradeId, receiverName, receiverPhone, zipCode, address, addressDetail);
    }

    /**
     * 판매자가 발송하며 송장을 등록한다.
     *
     * <p>송장 번호는 선택이다 — 없이도 발송 처리는 성립한다. 다만 한 번 발송한 건을
     * 다시 발송 처리할 수는 없다.
     */
    public void registerShipment(String carrier, String trackingNumber, LocalDateTime shippedAt) {
        if (this.shippedAt != null) {
            throw new IllegalStateException(
                    "Trade shipping is already shipped at " + this.shippedAt);
        }
        this.carrier = requireOptionalText(carrier, "carrier", 50);
        this.trackingNumber = requireOptionalText(trackingNumber, "tracking number", 100);
        this.shippedAt = requireTimestamp(shippedAt);
    }

    /** 구매자가 수령을 확인해 배송이 끝났다. */
    public void markDelivered(LocalDateTime deliveredAt) {
        if (this.shippedAt == null) {
            throw new IllegalStateException("Trade shipping cannot be delivered before it is shipped");
        }
        if (this.deliveredAt != null) {
            throw new IllegalStateException(
                    "Trade shipping is already delivered at " + this.deliveredAt);
        }
        this.deliveredAt = requireTimestamp(deliveredAt);
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Trade shipping " + field + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Trade shipping " + field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Trade shipping " + field + " must not exceed " + maxLength + " characters");
        }
        return value;
    }

    /**
     * 선택 항목의 빈 문자열은 <b>없는 것과 같게</b> 다룬다.
     *
     * <p>폼에서 입력하지 않은 칸은 {@code null} 이 아니라 {@code ""} 로 온다. 그걸 필수 검사에
     * 넘기면 "송장 없이 발송" 이라는 정상 경로가 매번 예외로 끝난다 — 실제로 프론트에서
     * 500 이 재현됐다. 값이 없다는 사실을 두 가지 방법으로 표현할 이유가 없으므로 여기서
     * 하나로 모은다.
     */
    private static String requireOptionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, field, maxLength);
    }

    private static LocalDateTime requireTimestamp(LocalDateTime timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("Trade shipping timestamp must not be null");
        }
        return timestamp;
    }
}
