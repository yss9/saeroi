package com.ssafy.upcycle.material.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.MaterialErrorCode;
import com.ssafy.upcycle.material.dto.MaterialEventResponse;
import com.ssafy.upcycle.material.repository.MaterialEventRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaterialEventQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MaterialRepository materialRepository;
    private final MaterialEventRepository eventRepository;

    @Transactional(readOnly = true)
    public MaterialEventResponse.Page findEvents(Long materialId, int page, int size) {
        if (!materialRepository.existsById(materialId)) {
            throw new BusinessException(MaterialErrorCode.MATERIAL_NOT_FOUND);
        }
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(MaterialErrorCode.MATERIAL_EVENT_QUERY_INVALID);
        }
        var result = eventRepository.findAllByMaterialIdOrderByOccurredAtAscIdAsc(
                materialId, PageRequest.of(page, size));
        return new MaterialEventResponse.Page(
                materialId,
                result.getContent().stream().map(MaterialEventResponse::from).toList(),
                result.getTotalElements(),
                page,
                size);
    }
}
