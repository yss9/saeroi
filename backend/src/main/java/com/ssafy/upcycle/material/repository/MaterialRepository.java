package com.ssafy.upcycle.material.repository;

import com.ssafy.upcycle.material.entity.Material;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 목록 조회·필터·페이징은 아직 두지 않는다. Balance 조회가 들어올 때
 * QueryDSL 로 available 집계와 함께 한 번에 짜는 편이 낫고, 지금 만들면 호출부가 없다.
 */
public interface MaterialRepository extends JpaRepository<Material, Long> {

    Optional<Material> findByMaterialCode(String materialCode);

    boolean existsByMaterialCode(String materialCode);
}
