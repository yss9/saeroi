package com.ssafy.upcycle.processing.service;

import static com.ssafy.upcycle.processing.ProcessingErrorCode.*;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.processing.dto.*;
import com.ssafy.upcycle.processing.entity.*;
import com.ssafy.upcycle.processing.repository.*;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가공사가 자기 프로필과 <b>처리 조건</b>을 관리한다.
 *
 * <p>견적이 사라지면서 이 표의 무게가 달라졌다. 예전에는 "이런 일을 할 수 있다" 는 소개에
 * 가까웠고 실제 금액은 요청이 올 때마다 견적으로 적었다. 지금은 <b>여기 적힌 단가가 곧
 * 청구되는 금액</b>이고, 하루 처리량이 곧 약속한 일정이다.
 *
 * <p>소요일 표({@code processor_lead_time})는 없앴다. 수량 구간마다 며칠인지 따로 적게 하면
 * 가공사가 채울 칸이 조합 수만큼 늘어나는데, 실제로 달라지는 것은 수량뿐이고 그건
 * {@code ⌈수량 ÷ 하루 처리량⌉} 으로 나온다.
 */
@Service
@RequiredArgsConstructor
public class ProcessorProfileService {

    private final UserRepository users;
    private final ProcessorProfileRepository profiles;
    private final ProcessorCapabilityRepository capabilities;

    @Transactional(readOnly = true)
    public ProcessorProfileResponse profile(Long processorId) {
        requireProcessor(processorId);
        return profiles.findById(processorId).map(this::profileResponse)
                .orElseThrow(() -> new BusinessException(PROCESSOR_PROFILE_NOT_FOUND));
    }

    @Transactional
    public ProcessorProfileResponse updateProfile(Long processorId, ProcessorProfileRequest request) {
        requireProcessor(processorId);
        ProcessorProfile profile = profiles.findById(processorId).orElseGet(
                () -> ProcessorProfile.register(processorId, request.region()));
        if (profiles.existsById(processorId)) profile.updateRegion(request.region());
        return profileResponse(profiles.save(profile));
    }

    @Transactional(readOnly = true)
    public List<ProcessorCapabilityResponse> capabilities(Long processorId) {
        requireProcessor(processorId);
        return capabilityResponses(processorId);
    }

    /**
     * 처리 조건을 통째로 갈아 끼운다.
     *
     * <p>부분 수정이 아니라 전체 교체다 — 화면이 표 하나를 통으로 편집하고, 어느 줄이
     * 지워졌는지 따로 알려 주지 않기 때문이다.
     *
     * <p>같은 (소재, 공정) 조합이 두 번 오면 거절한다. DB 의 UNIQUE 가 잡아 주기는 하지만
     * 그건 {@code DataIntegrityViolationException} 이라 가공사에게 설명할 수 없다.
     */
    @Transactional
    public List<ProcessorCapabilityResponse> updateCapabilities(Long processorId,
            ProcessorCapabilitiesUpdateRequest request) {
        requireProfile(processorId);
        var pairs = new HashSet<String>();
        for (var item : request.capabilities()) {
            if (!pairs.add(key(item.materialType(), item.processType()))) {
                throw new BusinessException(PROCESSOR_CAPABILITIES_INVALID);
            }
        }
        capabilities.deleteAllByProcessorProfileId(processorId);
        capabilities.flush();
        capabilities.saveAll(request.capabilities().stream()
                .map(item -> ProcessorCapability.of(processorId, item.materialType(),
                        item.processType(), item.dailyCapacity(), item.pricePerKg()))
                .toList());
        return capabilityResponses(processorId);
    }

    private void requireProcessor(Long processorId) {
        if (processorId == null || users.findById(processorId)
                .filter(user -> user.isActive() && user.getRole() == UserRole.PROCESSOR).isEmpty()) {
            throw new BusinessException(PROCESSOR_PROFILE_FORBIDDEN);
        }
    }

    private void requireProfile(Long processorId) {
        requireProcessor(processorId);
        if (!profiles.existsById(processorId)) throw new BusinessException(PROCESSOR_PROFILE_NOT_FOUND);
    }

    private ProcessorProfileResponse profileResponse(ProcessorProfile profile) {
        return new ProcessorProfileResponse(profile.getUserId(), profile.getRegion());
    }

    private List<ProcessorCapabilityResponse> capabilityResponses(Long processorId) {
        return capabilities.findAllByProcessorProfileIdOrderByMaterialTypeAscProcessTypeAsc(processorId).stream()
                .map(item -> new ProcessorCapabilityResponse(item.getMaterialType(), item.getProcessType(),
                        item.getDailyCapacity(), item.getPricePerKg()))
                .toList();
    }

    private String key(Object materialType, Object processType) {
        return materialType + ":" + processType;
    }
}
