package com.ssafy.upcycle.user.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.product.dto.PublicProductModelResponse;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.service.ProductCardAssembler;
import com.ssafy.upcycle.user.CartErrorCode;
import com.ssafy.upcycle.user.dto.CartItemResponse;
import com.ssafy.upcycle.user.dto.CartResponse;
import com.ssafy.upcycle.user.entity.ConsumerCartItem;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.ConsumerCartItemRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소비자의 장바구니.
 *
 * <h2>담기는 예약이 아니다</h2>
 *
 * <p>담을 때 재고를 보지만 <b>그것이 최종 보증은 아니다.</b> 담아 둔 사이에 다른 사람이
 * 사가면 그 수량은 더 이상 유효하지 않고, 서버는 담긴 수량만큼 재고를 잡아 두지도 않는다.
 * 진짜 확보는 주문이 개체를 잠그는 시점에 일어난다
 * ({@code ProductItemRepository.lockAvailable}).
 *
 * <p>그래도 담을 때 보는 이유는 뻔한 초과를 미리 막기 위해서다 — 재고 2개인 상품에 10개를
 * 담아 두면 결제 화면에서야 실패한다. 조회 응답에 현재 재고를 함께 실어 화면이 "3개 남음"
 * 을 보여줄 수 있게 한다.
 *
 * <h2>서버가 목록을 줄이지 않는다</h2>
 *
 * <p>상품이 단종되거나 품절되거나 감추는 분류로 바뀌어도 항목은 남는다. 사용자가 직접 담은
 * 목록이라 소리 없이 사라지면 안 된다 — 찜과 같은 원칙이다.
 *
 * <p>그래서 공개 목록 조회를 부르지 않는다. 그쪽은 {@code ON_SALE} 과 감춘 분류를 조건으로
 * 들고 있어 담아 둔 상품이 단종되는 순간 빠진다. 카드 조립만 {@link ProductCardAssembler}
 * 로 공유하고 무엇을 담을지는 여기서 정한다.
 *
 * <h2>이번 범위가 아닌 것</h2>
 *
 * <p>장바구니로 주문하는 흐름과 브랜드별 주문 분리는 여기 없다. 응답을 브랜드로 묶지 않는
 * 것도 그 때문이다 — 아직 정해지지 않은 정책을 응답 구조로 굳히지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ConsumerCartService {

    /**
     * 항목당 수량 상한.
     *
     * <p>재고가 이미 실질 상한이지만, 재고가 넉넉한 상품에서 {@code 999} 같은 입력 실수가
     * 그대로 담기는 것을 막는다.
     */
    static final int MAX_QUANTITY = 99;

    /**
     * 담을 수 있는 상품 종류 수.
     *
     * <p>목록에 페이지를 두지 않기로 했으므로 응답 크기를 여기서 묶는다. 장바구니가 50종을
     * 넘는 경우는 실제로 거의 없다.
     */
    static final int MAX_ITEMS = 50;

    private final ConsumerCartItemRepository cartItemRepository;
    private final ProductModelRepository productModelRepository;
    private final ProductItemRepository productItemRepository;
    private final ProductCardAssembler productCardAssembler;
    private final UserRepository userRepository;

    /**
     * 담는다. <b>이미 담긴 상품이면 수량을 더한다.</b>
     *
     * <p>상세 화면에서 "장바구니 담기" 를 두 번 누르면 2개가 되는 것이 일반적인 동작이고,
     * 새 행을 만들면 같은 상품이 목록에 두 줄로 보인다.
     *
     * <p>기준 가격은 지금 값으로 옮긴다 — 사용자가 상품 화면에서 그 가격을 보고 담았기
     * 때문이다 ({@link ConsumerCartItem#getAddedPrice()} 주석).
     */
    @Transactional
    public CartResponse add(Long userId, Long productModelId, int quantity) {
        requireConsumer(userId);
        requireQuantityInRange(quantity);
        ProductModel product = productModelRepository.findById(productModelId)
                .orElseThrow(() -> new BusinessException(CartErrorCode.CART_PRODUCT_NOT_FOUND));

        ConsumerCartItem item =
                cartItemRepository.findByUserIdAndProductModelId(userId, productModelId)
                        .orElse(null);
        int targetQuantity = item == null ? quantity : item.getQuantity() + quantity;
        requireQuantityInRange(targetQuantity);
        requireStock(productModelId, targetQuantity);

        if (item == null) {
            if (cartItemRepository.countByUserId(userId) >= MAX_ITEMS) {
                throw new BusinessException(CartErrorCode.CART_FULL);
            }
            save(ConsumerCartItem.of(userId, productModelId, quantity, product.getPrice()));
        } else {
            item.addQuantity(quantity, product.getPrice());
        }
        return findMine(userId);
    }

    /**
     * 수량을 지정한 값으로 바꾼다. <b>기준 가격은 그대로 둔다.</b>
     *
     * <p>장바구니 안에서 숫자만 만진 것이라 상품 화면을 다시 본 것이 아니다. 여기서 기준
     * 가격을 옮기면 "담아 둔 사이에 값이 바뀌었다" 는 안내가 조용히 사라진다.
     */
    @Transactional
    public CartResponse changeQuantity(Long userId, Long productModelId, int quantity) {
        requireConsumer(userId);
        requireQuantityInRange(quantity);
        ConsumerCartItem item =
                cartItemRepository.findByUserIdAndProductModelId(userId, productModelId)
                        .orElseThrow(() ->
                                new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND));
        requireStock(productModelId, quantity);

        item.changeQuantity(quantity);
        return findMine(userId);
    }

    /**
     * 항목을 뺀다. <b>담겨 있지 않아도 성공이다</b> — 결과 상태가 같다.
     *
     * <p>사용자 id 를 조건에 함께 넣으므로 남의 항목은 지워지지 않는다. 그 요청은 아무 행도
     * 건드리지 못하고 끝난다.
     */
    @Transactional
    public CartResponse remove(Long userId, Long productModelId) {
        requireConsumer(userId);
        cartItemRepository.deleteByUserIdAndProductModelId(userId, productModelId);
        return findMine(userId);
    }

    /** 전부 비운다. 항목마다 요청을 보내면 중간에 끊겼을 때 일부만 지워진 채로 남는다. */
    @Transactional
    public CartResponse clear(Long userId) {
        requireConsumer(userId);
        cartItemRepository.deleteByUserId(userId);
        return findMine(userId);
    }

    /**
     * 내 장바구니.
     *
     * <p>상품 행을 한 번에 읽어 카드로 조립한다 — 항목마다 상품을 부르면 그게 N+1 이다.
     * 순서는 담은 순서를 그대로 따른다.
     */
    @Transactional(readOnly = true)
    public CartResponse findMine(Long userId) {
        requireConsumer(userId);
        List<ConsumerCartItem> items = cartItemRepository.findMine(userId);

        List<ProductModel> products = productModelRepository.findAllById(
                items.stream().map(ConsumerCartItem::getProductModelId).toList());
        // 장바구니는 로그인해야 보이는 화면이라 이미지도 인증 경로를 쓴다. 공개 경로는
        // 판매 중인 상품만 열려 있어 단종된 항목의 사진이 깨진다.
        Map<Long, PublicProductModelResponse> cards =
                productCardAssembler.assembleAuthenticated(products);

        List<CartItemResponse> responses = new ArrayList<>();
        int totalQuantity = 0;
        long totalPrice = 0L;
        for (ConsumerCartItem item : items) {
            PublicProductModelResponse card = cards.get(item.getProductModelId());
            if (card == null) {
                // product_model FK 가 막고 있어 실제로는 나오지 않는다. 그래도 한 건 때문에
                // 장바구니 전체가 500 이 되는 것보다는 그 줄만 빠지는 편이 낫다.
                continue;
            }
            boolean purchasable = purchasable(card);
            responses.add(new CartItemResponse(
                    card,
                    item.getQuantity(),
                    item.getAddedPrice(),
                    item.getAddedPrice() != card.price(),
                    purchasable,
                    item.getCreatedAt()));
            // 합계는 살 수 있는 항목만 센다. 품절·단종을 넣으면 화면의 합계와 실제로 결제될
            // 금액이 어긋나고, 그 차이를 화면이 다시 계산해야 한다.
            if (purchasable) {
                totalQuantity += item.getQuantity();
                totalPrice += card.price() * item.getQuantity();
            }
        }
        return new CartResponse(responses, totalQuantity, totalPrice);
    }

    /**
     * 유니크 제약이 같은 상품의 두 행을 막는다.
     *
     * <p>조회해 보고 넣는 것만으로는 두 요청이 같은 순간 존재 확인을 함께 통과할 수 있다.
     * 찜과 달리 여기서는 성공으로 접을 수 없다 — 원하던 결과가 "행이 있다" 가 아니라
     * "수량이 더해졌다" 라서, 앞선 요청이 만든 행에 다시 더해야 한다. 그래서 다시 시도하라고
     * 답한다.
     *
     * <p>담기 버튼 연타는 흔하므로 화면이 <b>상품별로 진행 중인 담기 요청을 막아 주는 것을
     * 전제</b>로 한다. 그 전제가 없으면 사용자가 이 응답을 보게 된다.
     */
    private void save(ConsumerCartItem item) {
        try {
            cartItemRepository.saveAndFlush(item);
        } catch (DataIntegrityViolationException raced) {
            throw new BusinessException(CartErrorCode.CART_CONCURRENT_UPDATE);
        }
    }

    /**
     * 남은 재고를 넘겨 담지 못하게 한다.
     *
     * <p><b>최종 보증이 아니다.</b> 담아 둔 사이에 다른 사람이 사가면 이 검사를 통과한
     * 수량도 주문 시점에 실패한다 — 재고를 실제로 확보하는 것은 주문이 개체를 잠글 때다.
     * 여기서는 뻔한 초과만 걸러 낸다.
     */
    private void requireStock(Long productModelId, int quantity) {
        long inStock = productItemRepository.countByProductModelIdAndStatus(
                productModelId, ProductItemStatus.IN_STOCK);
        if (quantity > inStock) {
            throw new BusinessException(CartErrorCode.CART_QUANTITY_EXCEEDS_STOCK);
        }
    }

    private void requireQuantityInRange(int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new BusinessException(CartErrorCode.CART_QUANTITY_OUT_OF_RANGE);
        }
    }

    /** 지금 살 수 있는가. 목록이 걸러내지 않는 대신 이 값으로 알린다. 찜과 같은 규칙이다. */
    private boolean purchasable(PublicProductModelResponse card) {
        return card.status() == ProductModelStatus.ON_SALE
                && card.stockQuantity() > 0
                && card.category().isConsumerVisible();
    }

    /**
     * 소비자만 장바구니를 쓴다. 사업자 계정은 구매 흐름을 쓰지 않는다.
     *
     * <p>역할을 토큰이 아니라 DB 에서 다시 읽는다 — 토큰은 발급 시점의 스냅샷이라 정지되거나
     * 역할이 바뀐 계정이 만료 전까지 계속 통과한다.
     */
    private void requireConsumer(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(
                () -> new BusinessException(CartErrorCode.CART_ROLE_NOT_ALLOWED));
        if (!user.isActive() || user.getRole() != UserRole.CONSUMER) {
            throw new BusinessException(CartErrorCode.CART_ROLE_NOT_ALLOWED);
        }
    }
}
