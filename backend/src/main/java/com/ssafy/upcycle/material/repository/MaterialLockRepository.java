package com.ssafy.upcycle.material.repository;

import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockReferenceType;
import com.ssafy.upcycle.material.entity.MaterialLockStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 두 조회 경로 모두 ERD 의 인덱스와 정확히 대응한다.
 *
 * <ul>
 *   <li>{@code (material_id, owner_id, status)} — available 계산</li>
 *   <li>{@code (reference_type, reference_id)} — 거래·가공·생산이 끝날 때
 *       자기가 만든 잠금을 되찾아 해제하거나 소비하는 경로</li>
 * </ul>
 */
public interface MaterialLockRepository extends JpaRepository<MaterialLock, Long> {

    List<MaterialLock> findAllByMaterialIdAndOwnerIdAndStatus(
            Long materialId, Long ownerId, MaterialLockStatus status);

    List<MaterialLock> findAllByReferenceTypeAndReferenceId(
            MaterialLockReferenceType referenceType, Long referenceId);
}
