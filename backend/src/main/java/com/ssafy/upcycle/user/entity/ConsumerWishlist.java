package com.ssafy.upcycle.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
 * 소비자가 찜한 상품 하나.
 *
 * <p><b>찜 대상은 {@code product_model} 이다.</b> {@code product_item} 은 같은 상품의 개별
 * 재고라 서로 구별되지 않는다 — 사용자는 "이 가방" 을 찜하는 것이지 "이 가방의 3번 개체" 를
 * 찜하지 않는다. 개체를 가리키면 그 개체가 팔리는 순간 찜이 의미를 잃는다.
 *
 * <p><b>서버가 임의로 지우지 않는다.</b> 상품이 단종되거나 품절되거나 소비자에게 감추는
 * 분류로 바뀌어도 이 행은 남는다. 사용자가 직접 담은 목록이라 소리 없이 사라지면 "내가
 * 찜한 게 없어졌다" 가 된다. 팔 수 있는 상태인지는 조회할 때 상품을 보고 판단한다.
 *
 * <p>사용자와 상품을 연관관계가 아니라 FK 값으로 들고 있는 것은 이 저장소의 관례다
 * ({@code ProductModel.brandId}, {@code ProductItem.productModelId} 와 같다).
 *
 * <p><b>같은 상품을 두 번 담을 수 없다</b> — {@code uk_consumer_wishlist_user_product} 가
 * 막는다. 조회해 보고 넣는 방식만으로는 두 요청이 같은 순간 통과한다. 하트 버튼은 연타되고
 * 네트워크 재시도도 흔해서 실제로 겹친다.
 */
@Getter
@Entity
@Table(name = "consumer_wishlist")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ConsumerWishlist {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 찜한 사람. {@code users} FK 다. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 찜한 상품. {@code product_model} FK 다. */
    @Column(name = "product_model_id", nullable = false)
    private Long productModelId;

    /**
     * 담은 시각. 목록의 기본 정렬 키다.
     *
     * <p>초 단위라 같은 초에 여러 개를 담으면 값이 겹친다. 그래서 정렬은 {@code id} 를
     * 마지막 키로 함께 쓴다 — 겹친 순서가 페이지마다 달라지면 같은 상품이 1페이지와
     * 2페이지에 동시에 보이거나 어느 쪽에도 안 보인다.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ConsumerWishlist(Long userId, Long productModelId) {
        this.userId = requireNonNull(userId, "user id");
        this.productModelId = requireNonNull(productModelId, "product model id");
    }

    public static ConsumerWishlist of(Long userId, Long productModelId) {
        return new ConsumerWishlist(userId, productModelId);
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Consumer wishlist " + field + " must not be null");
        }
        return value;
    }
}
