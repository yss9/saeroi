package com.ssafy.upcycle.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * CONSUMER 계정의 배송지. 사업자의 {@code company_profile} 과 짝을 이루는 자리다.
 *
 * <p><b>1:N 인 것이 사업자와의 차이다.</b> 업체 주소는 사업자등록상 한 곳이지만 배송지는
 * 집·회사처럼 여러 곳일 수 있고, 그래서 {@code is_default} 가 있다. ERD 의
 * {@code consumer_address (1:N, 소비자)} 가 처음부터 그렇게 설계되어 있다.
 *
 * <p><b>수령인이 계정 주인과 다를 수 있다.</b> {@code receiver_name}/{@code receiver_phone}
 * 을 {@code users} 에서 가져오지 않고 이 행에 따로 두는 이유다 — 선물이나 가족 집으로 보내는
 * 경우 택배 송장에 찍혀야 하는 것은 주문자가 아니라 받는 사람이다. 가입 시 만드는 첫 행은
 * 주문자 본인으로 채우지만, 그건 기본값일 뿐 고정이 아니다.
 *
 * <p>주문이 이 행을 <b>참조하지 않는다</b>는 점도 중요하다. 결제 시점에
 * {@code product_shipping} 으로 값을 복사해 얼려 둔다. 그래야 나중에 배송지를 고치거나
 * 지워도 과거 주문의 배송 정보가 바뀌지 않는다.
 */
@Getter
@Entity
@Table(name = "consumer_address")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsumerAddress {

    /** 가입이 자동으로 만드는 첫 배송지의 이름. 사용자가 언제든 바꿀 수 있다. */
    public static final String DEFAULT_ALIAS = "기본 배송지";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 사용자가 붙이는 이름("집", "회사"). 화면에서 주소를 고를 때 보이는 값이다. */
    @Column(name = "alias", nullable = false, length = 100)
    private String alias;

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

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    private ConsumerAddress(
            Long userId,
            String alias,
            String receiverName,
            String receiverPhone,
            String zipCode,
            String address,
            String addressDetail) {
        this.userId = requireNonNull(userId, "userId");
        this.alias = requireText(alias, "alias", 100);
        this.receiverName = requireText(receiverName, "receiverName", 100);
        this.receiverPhone = requireText(receiverPhone, "receiverPhone", 20);
        this.zipCode = requireText(zipCode, "zipCode", 20);
        this.address = requireText(address, "address", 255);
        this.addressDetail = optionalText(addressDetail, "addressDetail", 255);
        this.isDefault = false;
    }

    public static ConsumerAddress register(
            Long userId,
            String alias,
            String receiverName,
            String receiverPhone,
            String zipCode,
            String address,
            String addressDetail) {
        return new ConsumerAddress(
                userId, alias, receiverName, receiverPhone, zipCode, address, addressDetail);
    }

    /**
     * 가입 화면이 받는 주소로 첫 배송지를 만든다.
     *
     * <p>가입 폼에는 수령인 칸이 없다. 그 자리를 주문자 본인으로 채우는 판단을 서비스마다
     * 반복하지 않도록 여기 한 곳에 둔다.
     */
    public static ConsumerAddress ofSignup(
            Long userId,
            String ownerName,
            String ownerPhone,
            String zipCode,
            String address,
            String addressDetail) {
        return register(
                userId, DEFAULT_ALIAS, ownerName, ownerPhone, zipCode, address, addressDetail);
    }

    /**
     * 기본 배송지 여부는 여기서만 바뀐다.
     *
     * <p><b>이 메서드를 단독으로 부르면 안 된다.</b> "기본 배송지는 사용자당 하나" 는 이 행
     * 혼자서는 지킬 수 없는 규칙이라 {@code ConsumerAddressService} 가 같은 트랜잭션에서
     * 나머지 행을 내리는 것과 짝지어 호출한다. DB 에도 이를 막는 제약이 없다
     * (부분 UNIQUE 인덱스는 MySQL 8 에 없다).
     */
    public void markDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    /**
     * 별칭·수령인·주소를 갱신한다.
     *
     * <p>기본 배송지 여부는 <b>여기서 바뀌지 않는다</b>. 주소를 고치다가 기본 배송지가
     * 조용히 옮겨 가면 사용자가 눈치채지 못한 채 다른 곳으로 물건을 받게 된다. 기본 지정은
     * 그것만 하는 별도 경로로 남긴다.
     */
    public void update(
            String alias,
            String receiverName,
            String receiverPhone,
            String zipCode,
            String address,
            String addressDetail) {
        this.alias = requireText(alias, "alias", 100);
        this.receiverName = requireText(receiverName, "receiverName", 100);
        this.receiverPhone = requireText(receiverPhone, "receiverPhone", 20);
        this.zipCode = requireText(zipCode, "zipCode", 20);
        this.address = requireText(address, "address", 255);
        this.addressDetail = optionalText(addressDetail, "addressDetail", 255);
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("ConsumerAddress " + field + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ConsumerAddress " + field + " must not be blank");
        }
        return checkLength(value.trim(), field, maxLength);
    }

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return checkLength(value.trim(), field, maxLength);
    }

    private static String checkLength(String value, String field, int maxLength) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "ConsumerAddress " + field + " must not exceed " + maxLength + " characters");
        }
        return value;
    }
}
