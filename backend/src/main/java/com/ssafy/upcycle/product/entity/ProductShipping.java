package com.ssafy.upcycle.product.entity;

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
 * 제품 주문의 배송 정보. {@code product_order_id} 가 UNIQUE 라 주문당 한 건이다.
 *
 * <p>소재 거래의 {@code trade_shipping} 과 거의 같은 모양인데 <b>{@code delivered_at} 이
 * 없다.</b> 수령 확인 시각은 {@code product_order.delivered_at} 이 갖는다 — 소비자의 수령이
 * 곧 주문의 완료라 두 곳에 나눠 둘 이유가 없다.
 *
 * <p>배송지는 주문 시점에 확정되고, 송장은 브랜드가 발송할 때 붙는다. 둘 다 NULL 을
 * 허용하는 이유는 송장 없는 전달 방식이 있기 때문이다.
 */
@Getter
@Entity
@Table(name = "product_shipping")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductShipping {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_order_id", nullable = false, unique = true)
    private Long productOrderId;

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

    @Column(name = "carrier", length = 50)
    private String carrier;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    private ProductShipping(
            Long productOrderId,
            String receiverName,
            String receiverPhone,
            String zipCode,
            String address,
            String addressDetail) {
        this.productOrderId = requireNonNull(productOrderId, "product order id");
        this.receiverName = requireText(receiverName, "receiver name", 100);
        this.receiverPhone = requireText(receiverPhone, "receiver phone", 20);
        this.zipCode = requireText(zipCode, "zip code", 20);
        this.address = requireText(address, "address", 255);
        this.addressDetail = requireOptionalText(addressDetail, "address detail", 255);
    }

    /** 주문 시점에 배송지를 확정한다. 송장은 아직 없다. */
    public static ProductShipping of(
            Long productOrderId,
            String receiverName,
            String receiverPhone,
            String zipCode,
            String address,
            String addressDetail) {
        return new ProductShipping(
                productOrderId, receiverName, receiverPhone, zipCode, address, addressDetail);
    }

    /** 브랜드가 발송하며 송장을 등록한다. 송장 번호는 선택이다. */
    public void registerShipment(String carrier, String trackingNumber, LocalDateTime shippedAt) {
        if (this.shippedAt != null) {
            throw new IllegalStateException(
                    "Product shipping is already shipped at " + this.shippedAt);
        }
        this.carrier = requireOptionalText(carrier, "carrier", 50);
        this.trackingNumber = requireOptionalText(trackingNumber, "tracking number", 100);
        this.shippedAt = requireTimestamp(shippedAt);
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Product shipping " + field + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Product shipping " + field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Product shipping " + field + " must not exceed " + maxLength + " characters");
        }
        return value;
    }

    /** 빈 문자열은 없는 것과 같게 다룬다. 폼에서 입력하지 않은 칸은 null 이 아니라 "" 로 온다. */
    private static String requireOptionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, field, maxLength);
    }

    private static LocalDateTime requireTimestamp(LocalDateTime timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("Product shipping timestamp must not be null");
        }
        return timestamp;
    }
}
