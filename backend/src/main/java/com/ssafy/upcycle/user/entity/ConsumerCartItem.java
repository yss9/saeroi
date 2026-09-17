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
 * 소비자가 장바구니에 담아 둔 상품 하나.
 *
 * <p><b>서버가 임의로 지우지 않는다.</b> 상품이 단종되거나 품절되거나 소비자에게 감추는
 * 분류로 바뀌어도 이 행은 남는다 — 사용자가 직접 담은 목록이라 소리 없이 사라지면 안 된다.
 * 살 수 있는 상태인지는 조회할 때 상품을 보고 판단한다. 찜과 같은 원칙이다.
 *
 * <p><b>재고를 예약하지 않는다.</b> 담기는 "사겠다" 가 아니라 "후보에 넣는다" 이고, 재고는
 * 주문 시점에 개체를 잠가 확보한다. 담을 때 재고를 보는 것은 뻔한 초과를 미리 막는
 * 편의일 뿐이다 ({@code ConsumerCartService} 주석).
 *
 * <p>같은 상품을 두 행으로 담을 수 없다 — {@code uk_consumer_cart_item_user_product} 가
 * 막는다. 조회해 보고 넣는 방식만으로는 두 요청이 같은 순간 통과한다.
 */
@Getter
@Entity
@Table(name = "consumer_cart_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ConsumerCartItem {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 담은 사람. {@code users} FK 다. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 담은 상품. {@code product_model} FK 다. */
    @Column(name = "product_model_id", nullable = false)
    private Long productModelId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /**
     * 이 항목을 <b>마지막으로 담을 때 사용자가 본 가격</b>.
     *
     * <p>결제 기준이 아니다. 주문 금액은 언제나 그 시점의 현재가로 정해지고, 이 값은 "담아
     * 둔 사이에 값이 바뀌었다" 를 화면이 알리기 위해서만 쓴다.
     *
     * <p><b>담기({@link #addQuantity})는 이 값을 갱신하고 수량 변경({@link #changeQuantity})은
     * 유지한다.</b> 담기는 사용자가 상품 화면에서 그 가격을 보고 한 행동이라 기준점이 그
     * 시점으로 옮겨 가는 것이 맞다 — 방금 본 가격을 두고 "값이 올랐다" 고 알리면 틀린
     * 안내이고, 2개 중 1개는 실제로 새 가격에 담은 것이라 사실과도 어긋난다. 반대로 수량
     * 변경은 장바구니 안에서 숫자만 만진 것이라, 거기서 갱신하면 알리려던 변동이 조용히
     * 사라진다.
     */
    @Column(name = "added_price", nullable = false)
    private long addedPrice;

    /** 처음 담은 시각. 목록 순서는 {@code id} 가 정하고 이 값은 화면 표시에만 쓴다. */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ConsumerCartItem(Long userId, Long productModelId, int quantity, long addedPrice) {
        this.userId = requireNonNull(userId, "user id");
        this.productModelId = requireNonNull(productModelId, "product model id");
        this.quantity = requirePositive(quantity);
        this.addedPrice = requireNonNegativePrice(addedPrice);
    }

    public static ConsumerCartItem of(
            Long userId, Long productModelId, int quantity, long addedPrice) {
        return new ConsumerCartItem(userId, productModelId, quantity, addedPrice);
    }

    /**
     * 같은 상품을 다시 담는다. <b>수량을 더하고 기준 가격을 지금 값으로 옮긴다.</b>
     *
     * <p>상세 화면에서 "장바구니 담기" 를 두 번 누르면 2개가 되는 것이 일반적인 동작이다.
     * 새 행을 만들면 같은 상품이 목록에 두 줄로 보인다.
     */
    public void addQuantity(int delta, long currentPrice) {
        this.quantity = requirePositive(this.quantity + requirePositive(delta));
        this.addedPrice = requireNonNegativePrice(currentPrice);
    }

    /** 수량을 지정한 값으로 바꾼다. <b>기준 가격은 건드리지 않는다</b> — 위 필드 주석 참고. */
    public void changeQuantity(int quantity) {
        this.quantity = requirePositive(quantity);
    }

    private static int requirePositive(int quantity) {
        // 서비스가 상한까지 포함해 먼저 거른다. 여기는 API 를 거치지 않는 경로를 위한
        // 최후 방어선이라 0 과 음수만 본다 — DB 의 CHECK 제약과 같은 범위다.
        if (quantity <= 0) {
            throw new IllegalArgumentException("Cart item quantity must be positive");
        }
        return quantity;
    }

    private static long requireNonNegativePrice(long price) {
        if (price < 0) {
            throw new IllegalArgumentException("Cart item price must not be negative");
        }
        return price;
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Cart item " + field + " must not be null");
        }
        return value;
    }
}
