package com.ssafy.upcycle.user.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.EmailAuthErrorCode;
import com.ssafy.upcycle.user.dto.EmailLoginRequest;
import com.ssafy.upcycle.user.dto.EmailLoginResponse;
import com.ssafy.upcycle.user.dto.UserSummaryResponse;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 이메일과 비밀번호를 검증하고 기존 JWT Access Token을 발급한다. */
@Service
public class EmailLoginService {

    private static final String DUMMY_PASSWORD = "email-login-dummy-password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final String dummyPasswordHash;

    public EmailLoginService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD);
    }

    @Transactional
    public EmailLoginResponse login(EmailLoginRequest request) {
        String email = normalizeEmail(request.email());
        Optional<User> foundUser = userRepository.findByEmail(email);
        String storedPassword = foundUser.map(User::getPassword).orElse(dummyPasswordHash);

        if (!passwordEncoder.matches(request.password(), storedPassword) || foundUser.isEmpty()) {
            throw new BusinessException(EmailAuthErrorCode.INVALID_CREDENTIALS);
        }

        User user = foundUser.orElseThrow();
        if (!user.isActive()) {
            throw new BusinessException(EmailAuthErrorCode.USER_SUSPENDED);
        }

        IssuedTokenPair tokens = refreshTokenService.issueFor(user);
        return new EmailLoginResponse(
                tokens.accessToken(), tokens.refreshToken(), UserSummaryResponse.from(user));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
