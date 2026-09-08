package com.ssafy.upcycle.user.repository;

import com.ssafy.upcycle.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 지갑 로그인은 {@code user_wallet} 을 거쳐 id 로만 찾으므로 {@code findById} 로 충분하다.
 * {@code findByEmail} 은 이메일 로그인이 들어올 때 그쪽에서 추가한다 — 지금 만들면 호출부가 없다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
