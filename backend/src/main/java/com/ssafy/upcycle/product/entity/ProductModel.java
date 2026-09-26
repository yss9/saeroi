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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 브랜드가 파는 제품의 종류. "RE:BAG" 같은 상품 하나에 해당한다.
 *
 * <p><b>재고 컬럼이 없다.</b> 재고는 {@code product_item} 을 {@link ProductItemStatus} 로
 * 세어 구한다. 컬럼으로 두면 판매 처리 중 한쪽만 실패했을 때 화면 재고와 실제가 조용히
 * 어긋나고, 그 어긋남은 주문이 실패하고 나서야 드러난다.
 *
 * <p>{@link ProductModelStatus#SOLD_OUT} 도 그 사실의 사본일 뿐 근거가 아니다.
 * 실제 판매 가능 여부는 언제나 개체를 세어 판단한다.
 *
 * <p><b>가격은 모델이 갖는다.</b> 개체마다 다르지 않다. 다만 주문 시점의 단가는
 * {@code product_order_item.price} 에 스냅샷으로 남는다 — 나중에 가격을 바꿔도 이미 팔린
 * 주문의 금액은 바뀌면 안 된다.
 *
 * <p>브랜드를 연관관계가 아니라 FK 값으로 들고 있는 이유는 Material 도메인과 같다.
 */
@Getter
@Entity
@Table(name = "product_model")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ProductModel {

    private static final int NAME_MAX_LENGTH = 200;
    private static final int CATEGORY_MAX_LENGTH = 50;
    private static final int THUMBNAIL_URL_MAX_LENGTH = 500;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = CATEGORY_MAX_LENGTH)
    private ProductCategory category;

    /**
     * 하위 분류. <b>{@code null} 일 수 있다.</b>
     *
     * <p>{@code WALLET}·{@code ACCESSORY}·{@code HOME}·{@code ETC} 는 2단 메뉴가 없어
     * 언제나 비어 있고, 하위 분류가 생기기 전에 등록된 제품도 비어 있다. 그 제품들은
     * 상위 분류 목록에는 그대로 나오고 2단 메뉴에서만 빠진다 — 필수로 만들면 기존 행이
     * 전부 위반 상태가 되고, 임의의 값을 채워 넣으면 브랜드가 정하지 않은 분류가 된다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "subcategory", length = CATEGORY_MAX_LENGTH)
    private ProductSubcategory subcategory;

    /** 파는 브랜드. users FK 다. */
    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    /** 판매가 (원). */
    @Column(name = "price", nullable = false)
    private long price;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "thumbnail_url", nullable = false, length = THUMBNAIL_URL_MAX_LENGTH)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductModelStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ProductModel(
            String name,
            ProductCategory category,
            ProductSubcategory subcategory,
            Long brandId,
            long price,
            String description,
            String thumbnailUrl) {
        this.name = requireText(name, "name", NAME_MAX_LENGTH);
        this.category = requireNonNull(category, "category");
        this.subcategory = requireMatchingSubcategory(category, subcategory);
        this.brandId = requireNonNull(brandId, "brand id");
        if (price < 0) {
            throw new IllegalArgumentException("Product model price must not be negative");
        }
        this.price = price;
        this.description = requireText(description, "description", Integer.MAX_VALUE);
        this.thumbnailUrl = requireText(thumbnailUrl, "thumbnail url", THUMBNAIL_URL_MAX_LENGTH);
        this.status = ProductModelStatus.DRAFT;
    }

    /**
     * 제품 모델을 등록한다. <b>{@link ProductModelStatus#DRAFT} 로 시작한다.</b>
     *
     * <p>등록 즉시 팔리지 않는 이유는 개체가 아직 없기 때문이다. 제품 개체는 생산 배치가
     * 확정될 때 만들어지므로, 등록 직후의 모델은 살 수 있는 물건이 하나도 없는 상태다.
     */
    public static ProductModel register(
            String name,
            ProductCategory category,
            Long brandId,
            long price,
            String description,
            String thumbnailUrl) {
        return register(name, category, null, brandId, price, description, thumbnailUrl);
    }

    /**
     * 하위 분류까지 정해 등록한다.
     *
     * <p>하위 분류는 선택이다 — 위 오버로드는 "하위 분류 없음" 을 뜻한다. 다만 값을 줬다면
     * 상위 분류에 실제로 속해야 한다. "가방인데 하위 분류가 원피스" 인 제품은 SHOP 의 어느
     * 2단 메뉴에도 걸리지 않으면서 목록 수만 늘린다.
     */
    public static ProductModel register(
            String name,
            ProductCategory category,
            ProductSubcategory subcategory,
            Long brandId,
            long price,
            String description,
            String thumbnailUrl) {
        return new ProductModel(
                name, category, subcategory, brandId, price, description, thumbnailUrl);
    }

    /**
     * 제품 정보를 고친다. <b>{@code null} 인 값은 건드리지 않는다.</b>
     *
     * <p>부분 수정이라 "안 보냄" 과 "빈 값으로 바꿈" 을 구분해야 하는데, 여기 오는 값은
     * 이미 요청 DTO 가 걸러낸 것이다 — 보낸 필드는 비어 있을 수 없고, 안 보낸 필드는
     * {@code null} 로 온다.
     *
     * <p><b>카테고리와 상태는 여기서 못 바꾼다.</b> 카테고리를 바꾸면 이미 그 분류로 팔린
     * 제품의 이력이 흔들리고, 상태는 재고를 봐야 정해지는 값이라 별도 경로가 있다.
     *
     * <p><b>가격은 판매 중에도 바꿀 수 있다.</b> 세일을 아예 막으면 가격을 내리려고 제품을
     * 새로 등록하게 되고, 그러면 이미 만든 개체가 옛 제품에 붙어 이력이 갈라진다.
     * 이미 결제된 주문은 {@code product_order_item.price} 스냅샷이 지키므로 영향이 없다.
     */
    public void update(String name, Long price, String description, String thumbnailUrl) {
        if (name != null) {
            this.name = requireText(name, "name", NAME_MAX_LENGTH);
        }
        if (price != null) {
            if (price < 0) {
                throw new IllegalArgumentException("Product model price must not be negative");
            }
            this.price = price;
        }
        if (description != null) {
            this.description = requireText(description, "description", Integer.MAX_VALUE);
        }
        if (thumbnailUrl != null) {
            this.thumbnailUrl =
                    requireText(thumbnailUrl, "thumbnail url", THUMBNAIL_URL_MAX_LENGTH);
        }
    }

    /**
     * 하위 분류를 정하거나 바꾼다. <b>상위 분류는 그대로다.</b>
     *
     * <p>하위 분류가 생기기 전에 등록된 제품은 값이 비어 있어 SHOP 2단 메뉴에 걸리지
     * 않는다. 브랜드가 나중에라도 채울 수 있어야 그 제품들이 메뉴에 들어온다.
     *
     * <p><b>비우는 경로는 없다.</b> 부분 수정 요청에서 {@code null} 은 "안 보냄" 이라
     * "비움" 과 구분되지 않는다. 잘못 넣은 하위 분류는 같은 상위 분류 안의 다른 값으로
     * 바꾸면 된다.
     */
    public void assignSubcategory(ProductSubcategory subcategory) {
        this.subcategory = requireMatchingSubcategory(
                this.category, requireNonNull(subcategory, "subcategory"));
    }

    /** 판매를 시작한다. 이미 내린 제품은 다시 올리지 않는다 — 새 모델로 등록한다. */
    public void putOnSale() {
        requireNotDiscontinued("put on sale");
        this.status = ProductModelStatus.ON_SALE;
    }

    /**
     * 재고가 떨어졌음을 표시한다.
     *
     * <p>이 값이 재고의 근거는 아니다. 개체를 세는 쪽이 진실이고 이것은 화면에 보여줄
     * 표시다. 그래서 판매 중이 아니었다면 아무것도 하지 않는다 — DRAFT 인 모델을
     * SOLD_OUT 으로 만들면 "팔린 적 없는데 품절" 이 된다.
     */
    public void markSoldOut() {
        if (this.status == ProductModelStatus.ON_SALE) {
            this.status = ProductModelStatus.SOLD_OUT;
        }
    }

    /** 품절이었다가 재고가 생겼다. 취소로 개체가 돌아오는 경우가 여기 해당한다. */
    public void restock() {
        if (this.status == ProductModelStatus.SOLD_OUT) {
            this.status = ProductModelStatus.ON_SALE;
        }
    }

    /** 단종. 남은 개체가 있어도 더 팔지 않는다. 되돌릴 수 없다. */
    public void discontinue() {
        requireNotDiscontinued("discontinue");
        this.status = ProductModelStatus.DISCONTINUED;
    }

    /** 판매 가능 상태인지. 재고까지 보는 것은 아니다 — 개체를 세는 쪽이 따로 판단한다. */
    public boolean isOnSale() {
        return this.status == ProductModelStatus.ON_SALE;
    }

    private void requireNotDiscontinued(String action) {
        if (this.status == ProductModelStatus.DISCONTINUED) {
            throw new IllegalStateException(
                    "Discontinued product model cannot " + action);
        }
    }

    /**
     * 상위·하위 분류의 조합을 검사한다. 값이 없으면 통과한다 — 하위 분류는 선택이다.
     *
     * <p>서비스가 {@code PRODUCT_SUBCATEGORY_NOT_ALLOWED} 로 먼저 걸러내므로 여기까지
     * 오는 것은 시드처럼 API 를 거치지 않는 경로뿐이다. 최후 방어선으로 남긴다.
     */
    private static ProductSubcategory requireMatchingSubcategory(
            ProductCategory category, ProductSubcategory subcategory) {
        if (subcategory != null && !subcategory.belongsTo(category)) {
            throw new IllegalArgumentException(
                    "Product model subcategory " + subcategory + " does not belong to " + category);
        }
        return subcategory;
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Product model " + field + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Product model " + field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Product model " + field + " must not exceed " + maxLength + " characters");
        }
        return value;
    }
}
