package com.ssafy.upcycle.user.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.SignupErrorCode;
import com.ssafy.upcycle.user.dto.BusinessSignupRequest;
import com.ssafy.upcycle.user.dto.ConsumerSignupRequest;
import com.ssafy.upcycle.user.dto.UserSummaryResponse;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 개인회원과 사업자회원의 계정 생성을 담당한다. */
@Service
public class SignupService {

    private final UserRepository userRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final PasswordEncoder passwordEncoder;

    public SignupService(
            UserRepository userRepository,
            CompanyProfileRepository companyProfileRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.companyProfileRepository = companyProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserSummaryResponse registerConsumer(ConsumerSignupRequest request) {
        User user = registerUser(
                request.email(), request.password(), request.name(), request.phone(), UserRole.CONSUMER);
        return UserSummaryResponse.from(user);
    }

    @Transactional
    public UserSummaryResponse registerBusiness(BusinessSignupRequest request) {
        if (!request.role().canOwnWallet()) {
            throw new BusinessException(SignupErrorCode.INVALID_BUSINESS_ROLE);
        }

        User user = registerUser(
                request.email(),
                request.password(),
                request.representativeName(),
                request.companyPhone(),
                request.role());
        companyProfileRepository.save(CompanyProfile.register(
                user.getId(),
                request.companyName().trim(),
                request.businessNumber().trim(),
                request.zipCode().trim(),
                request.address().trim(),
                normalizeOptional(request.addressDetail())));
        return UserSummaryResponse.from(user);
    }

    private User registerUser(
            String email, String password, String name, String phone, UserRole role) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(SignupErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.register(
                normalizedEmail,
                passwordEncoder.encode(password),
                name.trim(),
                phone.trim(),
                role);
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(SignupErrorCode.DUPLICATE_EMAIL);
        }
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
