package com.ssafy.upcycle.processing.repository;

import com.ssafy.upcycle.processing.entity.ProcessorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessorProfileRepository extends JpaRepository<ProcessorProfile, Long> {
}
