package com.ssafy.upcycle.user.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.product.dto.PublicProductModelResponse;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.service.ProductCardAssembler;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.user.WishlistErrorCode;
import com.ssafy.upcycle.user.dto.WishlistItemResponse;
import com.ssafy.upcycle.user.entity.ConsumerWishlist;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.ConsumerWishlistRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소비자의 찜.
 *
 * <h2>서버가 목록을 줄이지 않는다</h2>
 *
 * <p>찜은 사용자가 직접 담은 목록이다. 상품이 단종되거나 품절되거나 소비자에게 감추는 분류로
 * 바뀌어도 <b>그대로 남긴다</b> — 소리 없이 사라지면 "내가 찜한 게 없어졌다" 가 된다.
 *
 * <p>그래서 공개 목록 조회를 부르지 않는다. 그쪽은 {@code ON_SALE} 과 감춘 분류를 조건으로
 * 들고 있어서 찜해 둔 상품이 단종되는 순간 빠진다. 카드를 조립하는 일만
 * {@link ProductCardAssembler} 로 공유하고 무엇을 담을지는 여기서 정한다.
 *
 * <p>대신 살 수 있는 상태인지를 {@code purchasable} 로 함께 내려보낸다 — 판매 중이고, 재고가
 * 있고, 노출되는 분류여야 한다. 화면이 이 규칙을 다시 만들면 판단하는 자리가 둘이 되고,
 * 감춘 분류로 바뀐 상품은 화면이 알아낼 방법도 없다.
 *
 * <h2>남의 찜에 닿지 않는다</h2>
 *
 * <p>모든 경로가 JWT 에서 꺼낸 사용자 id 를 조건에 넣는다. 찜 행의 id 를 받는 경로가 없으므로
 * 남의 찜을 가리키는 요청 자체가 만들어지지 않는다 — 배송지가 같은 방식을 쓴다.
 */
@Service
@RequiredArgsConstructor
public class ConsumerWishlistService {

    /** SHOP 상품 카드 그리드를 그대로 쓰므로 상품 목록과 같은 값이다. */
    private static final int DEFAULT_SIZE = 12;

    /** 상한이 없으면 요청 한 번으로 찜 전체를 끌어올 수 있다. 다른 목록과 같은 값이다. */
    private static final int MAX_SIZE = 100;

    private final ConsumerWishlistRepository wishlistRepository;
    private final ProductModelRepository productModelRepository;
    private final ProductCardAssembler productCardAssembler;
    private final UserRepository userRepository;

    /**
     * 찜한다. <b>이미 찜한 상품을 다시 보내도 성공이다.</b>
     *
     * <p>하트 버튼은 연타되고 네트워크 재시도도 흔하다. 두 번째 요청에 오류로 답하면 화면이
     * 그 오류를 성공처럼 해석하는 코드를 따로 써야 하고, 그 분기는 언젠가 빠진다. 결과 상태가
     * 같으므로 같은 답을 준다.
     *
     * <p>조회해 보고 넣는 것만으로는 부족하다 — 두 요청이 같은 순간 존재 확인을 함께 통과할
     * 수 있다. 실제로 행이 하나만 남는 근거는 {@code uk_consumer_wishlist_user_product} 이고,
     * 그 제약에 걸린 요청은 <b>이미 담겨 있다는 뜻</b>이라 성공으로 접는다.
     *
     * <p><b>판매 상태는 보지 않는다.</b> 품절이나 판매 종료 상품도 담을 수 있어야 한다 —
     * 다시 들어오기를 기다리며 담아 두는 것이 찜의 쓰임 중 하나다.
     */
    @Transactional
    public void add(Long userId, Long productModelId) {
        requireConsumer(userId);
        if (!productModelRepository.existsById(productModelId)) {
            throw new BusinessException(WishlistErrorCode.WISHLIST_PRODUCT_NOT_FOUND);
        }
        if (wishlistRepository.findByUserIdAndProductModelId(userId, productModelId).isPresent()) {
            return;
        }
        try {
            wishlistRepository.saveAndFlush(ConsumerWishlist.of(userId, productModelId));
        } catch (DataIntegrityViolationException alreadyWished) {
            // 같은 순간 들어온 다른 요청이 먼저 넣었다. 원하던 상태가 이미 됐다.
        }
    }

    /**
     * 찜을 뺀다. <b>담겨 있지 않아도 성공이다</b> — 추가와 같은 이유이고, 결과 상태가 같다.
     *
     * <p>사용자 id 를 조건에 함께 넣으므로 남의 찜은 지워지지 않는다. 그 요청은 아무 행도
     * 건드리지 못하고 끝난다.
     */
    @Transactional
    public void remove(Long userId, Long productModelId) {
        requireConsumer(userId);
        wishlistRepository.deleteByUserIdAndProductModelId(userId, productModelId);
    }

    /**
     * 내 찜 목록. 최근에 담은 것부터.
     *
     * <p>상품 행을 한 번에 읽어 카드로 조립한다 — 찜마다 상품을 부르면 그게 N+1 이다.
     * 순서는 찜 행의 순서를 그대로 따른다.
     */
    @Transactional(readOnly = true)
    public PageResponse<WishlistItemResponse> findMine(Long userId, Integer page, Integer size) {
        requireConsumer(userId);
        Pageable pageable = PageRequest.of(pageOrDefault(page), sizeOrDefault(size));
        Page<ConsumerWishlist> wishes = wishlistRepository.findMine(userId, pageable);

        List<ProductModel> models = productModelRepository.findAllById(
                wishes.getContent().stream().map(ConsumerWishlist::getProductModelId).toList());
        // 찜 목록은 로그인해야 보이는 화면이라 이미지도 인증 경로를 쓴다. 공개 경로는 판매
        // 중인 상품만 열려 있어 단종된 찜 상품의 사진이 깨진다.
        Map<Long, PublicProductModelResponse> cards =
                productCardAssembler.assembleAuthenticated(models);

        List<WishlistItemResponse> items = new ArrayList<>();
        for (ConsumerWishlist wish : wishes.getContent()) {
            PublicProductModelResponse card = cards.get(wish.getProductModelId());
            if (card == null) {
                // product_model FK 가 막고 있어 실제로는 나오지 않는다. 그래도 한 건 때문에
                // 목록 전체가 500 이 되는 것보다는 그 줄만 빠지는 편이 낫다.
                continue;
            }
            items.add(new WishlistItemResponse(card, wish.getCreatedAt(), purchasable(card)));
        }
        return new PageResponse<>(
                items,
                wishes.getNumber(),
                wishes.getSize(),
                wishes.getTotalElements(),
                wishes.getTotalPages(),
                wishes.hasNext());
    }

    /**
     * 내가 찜한 상품 id 전부. 화면이 하트를 칠할 때 쓴다.
     *
     * <p>목록·상세·검색이 저마다 "이 상품 찜했나" 를 묻는 대신 한 번 받아 두는 편이 낫다 —
     * 상품이 화면에 뜰 때마다 요청이 나가면 SHOP 한 페이지에 12번이 붙는다.
     */
    @Transactional(readOnly = true)
    public List<Long> findMyProductModelIds(Long userId) {
        requireConsumer(userId);
        return wishlistRepository.findProductModelIdsOf(userId);
    }

    /** 지금 살 수 있는가. 목록이 걸러내지 않는 대신 이 값으로 알린다. */
    private boolean purchasable(PublicProductModelResponse card) {
        return card.status() == ProductModelStatus.ON_SALE
                && card.stockQuantity() > 0
                && card.category().isConsumerVisible();
    }

    /**
     * 소비자만 찜할 수 있다. 사업자 계정은 구매 흐름을 쓰지 않는다.
     *
     * <p>역할을 토큰이 아니라 DB 에서 다시 읽는다 — 토큰은 발급 시점의 스냅샷이라 정지되거나
     * 역할이 바뀐 계정이 만료 전까지 계속 통과한다. 제품 주문이 하는 검사와 같다.
     */
    private void requireConsumer(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(
                () -> new BusinessException(WishlistErrorCode.WISHLIST_ROLE_NOT_ALLOWED));
        if (!user.isActive() || user.getRole() != UserRole.CONSUMER) {
            throw new BusinessException(WishlistErrorCode.WISHLIST_ROLE_NOT_ALLOWED);
        }
    }

    private int pageOrDefault(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private int sizeOrDefault(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
