package com.ssafy.upcycle.user.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.SignupErrorCode;
import com.ssafy.upcycle.user.dto.BusinessSignupRequest;
import com.ssafy.upcycle.user.dto.ConsumerSignupRequest;
import com.ssafy.upcycle.user.dto.UserSummaryResponse;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.ConsumerAddress;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.geolocation.AddressGeocoder;
import com.ssafy.upcycle.user.geolocation.GeoCoordinates;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.ConsumerAddressRepository;
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
    private final ConsumerAddressRepository consumerAddressRepository;
    private final PasswordEncoder passwordEncoder;
    private final AddressGeocoder addressGeocoder;

    public SignupService(
            UserRepository userRepository,
            CompanyProfileRepository companyProfileRepository,
            ConsumerAddressRepository consumerAddressRepository,
            PasswordEncoder passwordEncoder,
            AddressGeocoder addressGeocoder) {
        this.userRepository = userRepository;
        this.companyProfileRepository = companyProfileRepository;
        this.consumerAddressRepository = consumerAddressRepository;
        this.passwordEncoder = passwordEncoder;
        this.addressGeocoder = addressGeocoder;
    }

    /**
     * 개인회원 가입. 계정과 함께 <b>기본 배송지 한 건</b>을 만든다.
     *
     * <p>주소를 {@code users} 가 아니라 {@code consumer_address} 에 넣는 이유는 ERD 가
     * 처음부터 1:N 로 설계했기 때문이다. {@code users} 에 주소 컬럼을 더하면 사업자 계정에서
     * 항상 NULL 인 역할 전용 컬럼이 생기고, {@code company_profile}·{@code consumer_address}
     * 와 함께 "진짜 배송지가 어디냐" 에 답이 셋이 된다.
     *
     * <p>사업자 가입과 달리 좌표 변환({@code addressGeocoder})을 하지 않는다 — 배송지는
     * 지도에 찍히지 않는다. 그래서 카카오 REST API 키가 없어도 개인회원 가입은 된다.
     */
    @Transactional
    public UserSummaryResponse registerConsumer(ConsumerSignupRequest request) {
        User user = registerUser(
                request.email(), request.password(), request.name(), request.phone(), UserRole.CONSUMER);

        ConsumerAddress address = consumerAddressRepository.save(ConsumerAddress.ofSignup(
                user.getId(),
                user.getName(),
                user.getPhone(),
                request.zipCode().trim(),
                request.address().trim(),
                normalizeOptional(request.addressDetail())));
        // 가입 직후에는 이것이 유일한 배송지다. 기본으로 올려 두지 않으면 첫 주문 화면이
        // 아무것도 고르지 못한 상태로 시작한다.
        address.markDefault(true);

        return UserSummaryResponse.from(user);
    }

    @Transactional
    public UserSummaryResponse registerBusiness(BusinessSignupRequest request) {
        if (!request.role().canOwnWallet()) {
            throw new BusinessException(SignupErrorCode.INVALID_BUSINESS_ROLE);
        }

        String address = request.address().trim();
        GeoCoordinates coordinates = addressGeocoder.geocode(address);

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
                address,
                normalizeOptional(request.addressDetail()),
                coordinates));
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
