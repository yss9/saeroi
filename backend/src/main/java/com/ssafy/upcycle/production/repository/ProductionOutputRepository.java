package com.ssafy.upcycle.production.repository;

import com.ssafy.upcycle.production.entity.ProductionOutput;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 자투리 산출 조회.
 *
 * <p><b>배치 단위 조회 메서드가 없다.</b> 산출은 투입에 매달려 있어서 배치로 바로 찾을 수
 * 없고, 찾으려면 {@code production_input} 을 거쳐야 한다. 그 조인은 배치 상세 화면이 생길
 * 때 QueryDSL 로 짜는 편이 낫다 — 여기에 {@code @Query} 로 넣으면 투입과 산출 양쪽 컬럼을
 * 문자열로 적게 되고, 컬럼명이 바뀌어도 컴파일러가 잡아 주지 않는다.
 */
public interface ProductionOutputRepository extends JpaRepository<ProductionOutput, Long> {

    List<ProductionOutput> findAllByProductionInputId(Long productionInputId);

    List<ProductionOutput> findAllByProductionInputIdIn(List<Long> productionInputIds);

    /** 자투리로 발행된 소재를 되짚는다. 혈통 검증이 쓴다. */
    List<ProductionOutput> findAllByNewMaterialId(Long newMaterialId);
}
