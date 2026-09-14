package com.ssafy.upcycle.user.repository;

import com.ssafy.upcycle.user.entity.ConsumerAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumerAddressRepository extends JpaRepository<ConsumerAddress, Long> {

    /**
     * 기본 배송지가 맨 위. 화면은 대개 첫 줄을 바로 고르므로 정렬을 서버가 정한다.
     *
     * <p>나머지는 등록 순서({@code id})다. 주소는 자주 바뀌지 않아 사용자가 기억하는 순서가
     * 곧 등록 순서다 — 별칭 가나다순으로 섞으면 오히려 찾기 어려워진다.
     */
    List<ConsumerAddress> findAllByUserIdOrderByIsDefaultDescIdAsc(Long userId);

    Optional<ConsumerAddress> findByUserIdAndIsDefaultTrue(Long userId);

    long countByUserId(Long userId);
}
