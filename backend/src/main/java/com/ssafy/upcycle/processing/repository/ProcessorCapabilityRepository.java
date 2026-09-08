package com.ssafy.upcycle.processing.repository;

import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.processing.entity.ProcessorCapability;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessorCapabilityRepository extends JpaRepository<ProcessorCapability, Long> {
    List<ProcessorCapability> findAllByProcessorProfileIdAndMaterialType(Long processorId, MaterialType materialType);
    List<ProcessorCapability> findAllByProcessorProfileIdOrderByMaterialTypeAscProcessTypeAsc(Long processorId);
    void deleteAllByProcessorProfileId(Long processorId);
}
