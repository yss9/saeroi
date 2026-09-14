package com.ssafy.upcycle.processing.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.storage.FileContent;
import com.ssafy.upcycle.common.storage.FileReference;
import com.ssafy.upcycle.common.storage.FileStorageErrorCode;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.common.storage.StoredFile;
import com.ssafy.upcycle.processing.ProcessingErrorCode;
import com.ssafy.upcycle.processing.dto.ProcessorProfileImageResponse;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProcessorProfileImageService {
    private static final String REFERENCE_TYPE = "PROCESSOR_PROFILE_IMAGE";
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");
    private static final String IMAGE_URL = "/api/v1/processors/me/profile/image";

    private final UserRepository userRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public ProcessorProfileImageResponse upload(Long processorId, MultipartFile file) {
        requireProcessor(processorId);
        CompanyProfile company = companyProfileRepository.findById(processorId)
                .orElseThrow(() -> new BusinessException(ProcessingErrorCode.PROCESSOR_PROFILE_NOT_FOUND));
        requireUploadable(file);

        StoredFile stored = store(processorId, file);
        try {
            company.updateLogoUrl(stored.storageKey());
            companyProfileRepository.saveAndFlush(company);
            return new ProcessorProfileImageResponse(IMAGE_URL);
        } catch (RuntimeException failure) {
            fileStorageService.delete(stored.storageKey());
            throw failure;
        }
    }

    @Transactional(readOnly = true)
    public FileContent read(Long processorId) {
        requireProcessor(processorId);
        String storageKey = companyProfileRepository.findById(processorId)
                .map(CompanyProfile::getLogoUrl)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new BusinessException(ProcessingErrorCode.PROCESSOR_PROFILE_IMAGE_NOT_FOUND));
        try {
            return fileStorageService.read(storageKey);
        } catch (BusinessException failure) {
            if (failure.getErrorCode() == FileStorageErrorCode.FILE_NOT_FOUND) {
                throw new BusinessException(ProcessingErrorCode.PROCESSOR_PROFILE_IMAGE_NOT_FOUND);
            }
            throw failure;
        }
    }

    private void requireProcessor(Long processorId) {
        User user = userRepository.findById(processorId)
                .orElseThrow(() -> new BusinessException(ProcessingErrorCode.PROCESSOR_PROFILE_FORBIDDEN));
        if (!user.isActive() || user.getRole() != UserRole.PROCESSOR) {
            throw new BusinessException(ProcessingErrorCode.PROCESSOR_PROFILE_FORBIDDEN);
        }
    }

    private void requireUploadable(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(FileStorageErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(FileStorageErrorCode.FILE_TOO_LARGE);
        }
        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT).trim();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(FileStorageErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
    }

    private StoredFile store(Long processorId, MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            return fileStorageService.store(
                    new FileReference(REFERENCE_TYPE, processorId),
                    file.getOriginalFilename(), file.getContentType(), content, file.getSize());
        } catch (IOException failure) {
            throw new BusinessException(FileStorageErrorCode.FILE_STORAGE_FAILED);
        }
    }
}
