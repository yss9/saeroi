package com.ssafy.upcycle.processing.service;

import com.ssafy.upcycle.material.service.ProcessingVerification;
import com.ssafy.upcycle.processing.repository.ProcessingOutputRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProcessingVerificationService implements ProcessingVerification {
    private final ProcessingOutputRepository outputs;

    @Override
    @Transactional(readOnly = true)
    public boolean hasConfirmedProcessingResult(Long materialId) {
        return materialId != null && outputs.existsConfirmedOutputMaterial(materialId);
    }
}
