package com.ssafy.upcycle.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 제품 개체 하나. "RE:BAG #001" 처럼 실제로 팔리는 물건 한 점이다.
 *
 * <p><b>생산 배치가 확정될 때 만들어진다.</b> 브랜드가 임의로 개체를 늘릴 수 없다는 것이
 * 이 도메인의 신뢰 근거다 — 개체는 언제나 어떤 배치에서 나왔고, 그 배치에는 어떤 소재가
 * 얼마나 쓰였는지가 기록돼 있다.
 *
 * <h2>{@code qrToken} 을 id 와 분리하는 이유</h2>
 *
 * <p>QR 주소에 순번이 들어가면 <b>URL 을 하나씩 바꿔가며 남의 제품 이력을 전부 열람할 수
 * 있다.</b> 그래서 추측할 수 없는 랜덤값을 따로 둔다. 이력 조회는 인증 없이 열려 있는
 * 경로라 이 값이 유일한 방어선이다.
 *
 * <h2>추적 범위</h2>
 *
 * <p>같은 배치에서 나온 개체는 동일한 구성으로 간주한다. QR 이 증명하는 것은
 * <b>"이 개체는 이 배치에서 생산됐고, 그 배치에 이 소재들이 이 비율로 쓰였다"</b> 까지이며,
 * "이 가방에 바로 그 500g 이 들어갔다" 는 아니다.
 *
 * <p>생산 배치를 연관관계가 아니라 FK 값으로 들고 있다. Production 도메인이 아직 없어
 * 가리킬 엔티티가 없기도 하고, Material 도메인과 방식을 맞추기도 한다.
 */
@Getter
@Entity
@Table(name = "product_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ProductItem {

    private static final int ITEM_CODE_MAX_LENGTH = 50;
    private static final Pattern QR_TOKEN_PATTERN = Pattern.compile("^[0-9a-f]{32}$");

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 개체를 만들어 낸 생산 배치. */
    @Column(name = "production_batch_id", nullable = false)
    private Long productionBatchId;

    /** 조회 편의를 위해 함께 들고 있다. 배치를 거치지 않고 모델별 재고를 셀 수 있다. */
    @Column(name = "product_model_id", nullable = false)
    private Long productModelId;

    /** 배치 안에서의 순번. 1 부터 시작한다. */
    @Column(name = "serial_number", nullable = false)
    private int serialNumber;

    /** 사람이 읽는 식별자. "RE:BAG #001". */
    @Column(name = "item_code", nullable = false, length = ITEM_CODE_MAX_LENGTH, unique = true)
    private String itemCode;

    /** QR 주소에 들어가는 추측 불가 랜덤값. id 를 쓰면 남의 이력을 열람할 수 있다. */
    @Column(name = "qr_token", nullable = false, columnDefinition = "CHAR(32)", unique = true)
    private String qrToken;

    /**
     * 기본 크기 QR PNG 를 처음 만든 뒤 저장한 {@link com.ssafy.upcycle.common.storage.FileStorageService}
     * 키. 아직 한 번도 만들어지지 않았으면 {@code null} — 그 시점에 처음 렌더링하고 채운다.
     */
    @Column(name = "qr_image_key")
    private String qrImageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductItemStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ProductItem(
            Long productionBatchId,
            Long productModelId,
            int serialNumber,
            String itemCode,
            String qrToken) {
        this.productionBatchId = requireNonNull(productionBatchId, "production batch id");
        this.productModelId = requireNonNull(productModelId, "product model id");
        if (serialNumber <= 0) {
            throw new IllegalArgumentException("Product item serial number must be positive");
        }
        this.serialNumber = serialNumber;
        this.itemCode = requireText(itemCode, "item code", ITEM_CODE_MAX_LENGTH);
        this.qrToken = requireQrToken(qrToken);
        this.status = ProductItemStatus.IN_STOCK;
    }

    /**
     * 생산 확정으로 개체가 나온다. 만들어지는 즉시 재고다.
     *
     * <p>{@code qrToken} 을 여기서 만들지 않고 받는다. 생성 방식이 엔티티에 박히면
     * 테스트에서 값을 고정할 수 없고, 길이 계약({@code CHAR(32)})이 생성기와 엔티티 두
     * 곳으로 흩어진다. 만드는 일은 {@code ProductQrTokenGenerator} 가 맡는다.
     */
    public static ProductItem produce(
            Long productionBatchId,
            Long productModelId,
            int serialNumber,
            String itemCode,
            String qrToken) {
        return new ProductItem(
                productionBatchId, productModelId, serialNumber, itemCode, qrToken);
    }

    /**
     * 주문에 잡아 둔다. 결제 완료까지 다른 사람이 같은 개체를 못 사게 막는 것이 목적이다.
     *
     * <p>{@code product_order_item.product_item_id} 의 UNIQUE 가 최후 방어선이지만, 그것만
     * 믿으면 동시 주문 중 한쪽이 결제까지 마친 뒤에야 실패한다.
     */
    public void reserve() {
        requireStatus(ProductItemStatus.IN_STOCK, "reserve");
        this.status = ProductItemStatus.RESERVED;
    }

    /** 결제 전 주문이 취소되어 다시 팔 수 있게 된다. */
    public void release() {
        requireStatus(ProductItemStatus.RESERVED, "release");
        this.status = ProductItemStatus.IN_STOCK;
    }

    /**
     * 주문이 취소되어 재고로 돌아간다. <b>결제가 끝난 개체도 돌아올 수 있다.</b>
     *
     * <p>{@link #release} 와 달리 {@code SOLD} 를 받는 것이 이 메서드의 이유다. 결제된 주문도
     * 발송 전까지는 취소되고, 그때 환불이 먼저 끝난 뒤 이 개체가 다시 팔릴 수 있어야 한다.
     * 취소 순서와 환불 보장은 {@code ProductOrderPaymentService#cancel} 이 책임진다.
     *
     * <p>이미 재고면 아무 일도 하지 않는다 — 취소 정리가 두 번 들어와도 같은 결과여야 한다.
     */
    public void returnToStock() {
        this.status = ProductItemStatus.IN_STOCK;
    }

    /** 결제가 끝나 판매가 확정된다. */
    public void markSold() {
        requireStatus(ProductItemStatus.RESERVED, "sell");
        this.status = ProductItemStatus.SOLD;
    }

    public boolean isInStock() {
        return this.status == ProductItemStatus.IN_STOCK;
    }

    /**
     * 처음 렌더링한 기본 크기 QR PNG 를 저장한 뒤 그 키를 기록한다.
     *
     * <p>이미 키가 있으면 덮어쓰지 않는다 — 같은 토큰으로 다시 그려도 PNG 바이트는 항상
     * 같으므로 재저장할 이유가 없고, 호출부(<code>ProductionBatchService</code>)가 먼저
     * {@link #getQrImageKey()} 로 캐시 적중 여부를 본 뒤에만 이 메서드를 부른다.
     */
    public void attachQrImage(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("QR image storage key must not be blank");
        }
        this.qrImageKey = storageKey;
    }

    private void requireStatus(ProductItemStatus expected, String action) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "Product item can only " + action + " while " + expected
                            + " but was " + this.status);
        }
    }

    /**
     * hex 32자만 받는다. 컬럼이 {@code CHAR(32)} 라 길이가 어긋나면 잘리거나 공백이 붙는데,
     * QR 주소로 쓰이는 값이라 한 글자만 달라져도 조회가 실패한다.
     */
    private static String requireQrToken(String qrToken) {
        if (qrToken == null || !QR_TOKEN_PATTERN.matcher(qrToken).matches()) {
            throw new IllegalArgumentException(
                    "Product item qr token must be a 32 digit lowercase hex string");
        }
        return qrToken;
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Product item " + field + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Product item " + field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Product item " + field + " must not exceed " + maxLength + " characters");
        }
        return value;
    }
}
