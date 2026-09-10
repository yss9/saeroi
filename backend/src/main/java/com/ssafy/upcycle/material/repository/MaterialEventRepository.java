package com.ssafy.upcycle.material.repository;

import com.ssafy.upcycle.material.entity.MaterialEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MaterialEventRepository extends JpaRepository<MaterialEvent, Long> {

    Page<MaterialEvent> findAllByMaterialIdOrderByOccurredAtAscIdAsc(
            Long materialId, Pageable pageable);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<MaterialEvent> findByIdempotencyKey(String idempotencyKey);
}
