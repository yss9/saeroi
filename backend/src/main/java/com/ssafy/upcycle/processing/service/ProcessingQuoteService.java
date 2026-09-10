package com.ssafy.upcycle.processing.service;

import static com.ssafy.upcycle.processing.ProcessingErrorCode.*;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.MaterialEventReferenceType;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.service.MaterialEventRecorder;
import com.ssafy.upcycle.processing.dto.*;
import com.ssafy.upcycle.processing.entity.*;
import com.ssafy.upcycle.processing.repository.*;
import com.ssafy.upcycle.user.entity.*;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ProcessingQuoteService {
    private final UserRepository users;
    private final ProcessingRequestRepository requests;
    private final ProcessingQuoteRepository quotes;
    private final ProcessingRequestInputRepository requestInputs;
    private final Clock clock;
    private final MaterialEventRecorder eventRecorder;

    @Transactional
    public ProcessingQuoteResponse submit(Long actorId, Long requestId, SubmitProcessingQuoteRequest input) {
        requireRole(actorId, UserRole.PROCESSOR);
        ProcessingRequest request = requests.findForUpdate(requestId)
                .orElseThrow(() -> new BusinessException(PROCESSING_REQUEST_NOT_FOUND));
        if (!actorId.equals(request.getProcessorId())) throw new BusinessException(PROCESSING_FORBIDDEN);
        if (request.getStatus()!=ProcessingRequestStatus.QUOTING || request.isDue(LocalDateTime.now(clock)))
            throw new BusinessException(PROCESSING_INVALID_STATE);
        if (quotes.existsByProcessingRequestIdAndProcessorId(requestId, actorId))
            throw new BusinessException(PROCESSING_QUOTE_ALREADY_EXISTS);
        if (input.price()==null || input.price()<0 || input.leadDays()==null || input.leadDays()<=0)
            throw new BusinessException(PROCESSING_INVALID_QUOTE);
        ProcessingQuote quote = quotes.save(ProcessingQuote.submit(requestId, actorId, input.price(),
                input.leadDays(), input.memo(), LocalDateTime.now(clock)));
        ProcessingRequestInput requestInput = requestInputs.findByProcessingRequestId(requestId)
                .orElseThrow(() -> new BusinessException(PROCESSING_INVALID_STATE));
        eventRecorder.record(
                requestInput.getMaterialId(), MaterialEventType.PROCESSING_QUOTE_SUBMITTED, actorId,
                MaterialEventReferenceType.PROCESSING_QUOTE, quote.getId(), null,
                requestInput.getQuantity(), "가공업체 견적 제출",
                MaterialEventRecorder.key(
                        MaterialEventType.PROCESSING_QUOTE_SUBMITTED,
                        requestInput.getMaterialId(), quote.getId()));
        return ProcessingQuoteResponse.of(quote, users.findById(actorId).orElseThrow().getName());
    }

    @Transactional(readOnly=true)
    public List<ProcessingQuoteResponse> list(Long actorId, Long requestId) {
        requireRole(actorId, UserRole.BRAND);
        ProcessingRequest request=requests.findById(requestId).orElseThrow(() -> new BusinessException(PROCESSING_REQUEST_NOT_FOUND));
        if (!actorId.equals(request.getBrandId())) throw new BusinessException(PROCESSING_FORBIDDEN);
        return quotes.findAllByProcessingRequestIdOrderByCreatedAtAscIdAsc(requestId).stream()
                .map(q -> ProcessingQuoteResponse.of(q, users.findById(q.getProcessorId()).map(User::getName).orElse(""))).toList();
    }

    private void requireRole(Long id, UserRole role) {
        if (id==null || users.findById(id).filter(User::isActive).filter(u->u.getRole()==role).isEmpty())
            throw new BusinessException(PROCESSING_FORBIDDEN);
    }
}
