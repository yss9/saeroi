package com.ssafy.upcycle.user.repository;

import com.ssafy.upcycle.user.entity.ConsumerWishlist;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 찜 조회·삭제.
 *
 * <p><b>모든 메서드가 {@code userId} 를 함께 받는다.</b> 찜 id 만으로 찾는 경로를 열어 두면
 * 남의 찜을 지우는 요청이 만들어질 수 있고, 그 검사를 서비스가 빠뜨리면 조용히 통과한다.
 * 조건에 넣어 두면 그런 요청이 아무것도 건드리지 못한다.
 *
 * <p>{@code uk_consumer_wishlist_user_product} 가 존재 확인과 삭제를, {@code (user_id, id)}
 * 인덱스가 목록 정렬을 받친다.
 */
public interface ConsumerWishlistRepository extends JpaRepository<ConsumerWishlist, Long> {

    /**
     * 내 찜 목록. <b>최근에 담은 것부터</b>.
     *
     * <p>{@code id} 를 마지막 키로 둔다 — {@code created_at} 은 초 단위라 같은 초에 담은
     * 것들의 순서가 페이지마다 달라질 수 있다.
     */
    @Query("select wish from ConsumerWishlist wish where wish.userId = :userId "
            + "order by wish.createdAt desc, wish.id desc")
    Page<ConsumerWishlist> findMine(@Param("userId") Long userId, Pageable pageable);

    Optional<ConsumerWishlist> findByUserIdAndProductModelId(Long userId, Long productModelId);

    /** 화면이 하트를 칠할 때 쓴다. 담은 순서와 무관하므로 정렬하지 않는다. */
    @Query("select wish.productModelId from ConsumerWishlist wish where wish.userId = :userId")
    List<Long> findProductModelIdsOf(@Param("userId") Long userId);

    /**
     * 찜 해제. <b>지워진 행 수를 돌려준다</b> — 이미 없던 것과 방금 지운 것을 서비스가
     * 구분할 수 있어야 로그가 의미를 갖는다. 응답은 둘 다 성공이다.
     */
    long deleteByUserIdAndProductModelId(Long userId, Long productModelId);
}
