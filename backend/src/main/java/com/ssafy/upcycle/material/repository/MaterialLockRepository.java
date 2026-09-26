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

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select l from MaterialLock l where l.id = :id")
    java.util.Optional<MaterialLock> findByIdForUpdate(
            @org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select l from MaterialLock l where l.materialId = :materialId "
            + "and l.ownerId = :ownerId and l.status = com.ssafy.upcycle.material.entity.MaterialLockStatus.ACTIVE order by l.id")
    List<MaterialLock> findActiveForUpdate(
            @org.springframework.data.repository.query.Param("materialId") Long materialId,
            @org.springframework.data.repository.query.Param("ownerId") Long ownerId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select l from MaterialLock l where l.referenceType = :type "
            + "and l.referenceId = :id order by l.id")
    List<MaterialLock> findByReferenceForUpdate(
            @org.springframework.data.repository.query.Param("type") MaterialLockReferenceType type,
            @org.springframework.data.repository.query.Param("id") Long id);

    List<MaterialLock> findAllByMaterialIdAndOwnerIdAndStatus(
            Long materialId, Long ownerId, MaterialLockStatus status);

    List<MaterialLock> findAllByReferenceTypeAndReferenceId(
            MaterialLockReferenceType referenceType, Long referenceId);
}
