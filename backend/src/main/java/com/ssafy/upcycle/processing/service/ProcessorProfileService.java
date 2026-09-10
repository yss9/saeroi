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

@Service
@RequiredArgsConstructor
public class ProcessorProfileService {
    private final UserRepository users;
    private final ProcessorProfileRepository profiles;
    private final ProcessorCapabilityRepository capabilities;
    private final ProcessorLeadTimeRepository leadTimes;

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

    @Transactional
    public List<ProcessorCapabilityResponse> updateCapabilities(Long processorId,
            ProcessorCapabilitiesUpdateRequest request) {
        requireProfile(processorId);
        var pairs = new HashSet<String>();
        for (var item : request.capabilities()) {
            if (item.minQuantity() > item.maxQuantity()
                    || !pairs.add(item.materialType().name() + ':' + item.processType().name())) {
                throw new BusinessException(PROCESSOR_CAPABILITIES_INVALID);
            }
        }
        leadTimes.deleteAllByProcessorProfileId(processorId);
        leadTimes.flush();
        capabilities.deleteAllByProcessorProfileId(processorId);
        capabilities.flush();
        capabilities.saveAll(request.capabilities().stream()
                .map(item -> ProcessorCapability.of(processorId, item.materialType(), item.processType(),
                        item.minQuantity(), item.maxQuantity()))
                .toList());
        return capabilityResponses(processorId);
    }

    @Transactional(readOnly = true)
    public List<ProcessorLeadTimeResponse> leadTimes(Long processorId) {
        requireProcessor(processorId);
        return leadTimeResponses(processorId);
    }

    @Transactional
    public List<ProcessorLeadTimeResponse> updateLeadTimes(Long processorId,
            ProcessorLeadTimesUpdateRequest request) {
        requireProfile(processorId);
        var capabilityMap = capabilities.findAllByProcessorProfileIdOrderByMaterialTypeAscProcessTypeAsc(processorId)
                .stream().collect(java.util.stream.Collectors.toMap(
                        item -> key(item.getMaterialType(), item.getProcessType()), item -> item));
        var bounds = new HashSet<String>();
        var largestBounds = new HashMap<String, Long>();
        for (var item : request.leadTimes()) {
            String key = key(item.materialType(), item.processType());
            ProcessorCapability capability = capabilityMap.get(key);
            if (capability == null || item.maxQuantity() < capability.getMinQuantity()
                    || !bounds.add(key + ':' + item.maxQuantity())) {
                throw new BusinessException(PROCESSOR_LEAD_TIMES_INVALID);
            }
            largestBounds.merge(key, item.maxQuantity(), Math::max);
        }
        boolean uncovered = capabilityMap.entrySet().stream().anyMatch(entry ->
                largestBounds.getOrDefault(entry.getKey(), 0L) < entry.getValue().getMaxQuantity());
        if (uncovered) throw new BusinessException(PROCESSOR_LEAD_TIMES_INVALID);
        leadTimes.deleteAllByProcessorProfileId(processorId);
        leadTimes.flush();
        leadTimes.saveAll(request.leadTimes().stream()
                .map(item -> ProcessorLeadTime.of(processorId, item.materialType(), item.processType(),
                        item.maxQuantity(), item.leadDays()))
                .toList());
        return leadTimeResponses(processorId);
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
                        item.getMinQuantity(), item.getMaxQuantity()))
                .toList();
    }

    private List<ProcessorLeadTimeResponse> leadTimeResponses(Long processorId) {
        return leadTimes.findAllByProcessorProfileIdOrderByMaterialTypeAscProcessTypeAscMaxQuantityAsc(processorId).stream()
                .map(item -> new ProcessorLeadTimeResponse(item.getMaterialType(), item.getProcessType(),
                        item.getMaxQuantity(), item.getLeadDays()))
                .toList();
    }

    private String key(Object materialType, Object processType) {
        return materialType + ":" + processType;
    }
}
