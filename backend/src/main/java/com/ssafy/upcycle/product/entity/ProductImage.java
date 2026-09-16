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
 * 상품 상세 갤러리의 사진 한 장.
 *
 * <p><b>{@code product_model.thumbnail_url} 을 대체하지 않는다.</b> 그 값은 목록 카드가
 * 쓰는 한 장이고 주문 이력까지 따라다니는 반면, 여기 행들은 상세 화면이 넘겨 보는 묶음이다.
 * 대표 사진을 갤러리로 옮기면 갤러리가 비어 있는 제품은 목록에 그릴 사진이 없어진다.
 *
 * <p><b>바이트를 들고 있지 않다.</b> {@code storageKey} 는 {@code FileStorageService} 가
 * 돌려준 키이고, 그것을 실제 파일로 바꾸는 일은 저장소가 한다 — 로컬 디스크든 S3 든
 * 도메인은 같은 값을 들고 있다. 화면에 붙일 주소도 아니다: 바이트는
 * {@code GET .../product-models/{id}/images/{imageId}} 가 흘린다.
 *
 * <p>제품을 연관관계가 아니라 FK 값으로 들고 있는 이유는 {@link ProductItem} 과 같다.
 */
@Getter
@Entity
@Table(name = "product_image")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ProductImage {

    private static final int STORAGE_KEY_MAX_LENGTH = 500;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_model_id", nullable = false)
    private Long productModelId;

    /** {@code FileStorageService} 가 돌려준 저장 키. 공개 주소가 아니다. */
    @Column(name = "storage_key", nullable = false, length = STORAGE_KEY_MAX_LENGTH)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_type", nullable = false)
    private ProductImageType imageType;

    /**
     * 갤러리 안에서의 자리. 0 부터 빈틈없이 이어진다.
     *
     * <p>브랜드가 보낸 배열의 순서가 그대로 이 값이 된다. 사진마다 번호를 따로 받으면
     * 같은 번호가 둘이거나 중간이 비는 요청을 서버가 되돌려야 하는데, 배열은 그런 상태를
     * 만들 수 없다.
     */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ProductImage(
            Long productModelId, String storageKey, ProductImageType imageType, int sortOrder) {
        this.productModelId = requireNonNull(productModelId, "product model id");
        this.storageKey = requireStorageKey(storageKey);
        this.imageType = requireNonNull(imageType, "image type");
        if (sortOrder < 0) {
            throw new IllegalArgumentException("Product image sort order must not be negative");
        }
        this.sortOrder = sortOrder;
    }

    public static ProductImage of(
            Long productModelId, String storageKey, ProductImageType imageType, int sortOrder) {
        return new ProductImage(productModelId, storageKey, imageType, sortOrder);
    }

    /** 이 사진이 그 제품의 것인가. 남의 제품 이미지 id 를 찔러 보는 요청을 막는다. */
    public boolean belongsTo(Long productModelId) {
        return this.productModelId.equals(productModelId);
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Product image " + field + " must not be null");
        }
        return value;
    }

    private static String requireStorageKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Product image storage key must not be blank");
        }
        if (value.length() > STORAGE_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Product image storage key must not exceed "
                            + STORAGE_KEY_MAX_LENGTH + " characters");
        }
        return value;
    }
}
