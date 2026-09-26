package com.ssafy.upcycle.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.processing.expiry.enabled=false")
@AutoConfigureMockMvc
@Transactional
class ProcessorProfileImageTests {
    private static final byte[] IMAGE = "processor-logo".getBytes(StandardCharsets.UTF_8);
    private static final Path STORAGE_ROOT = storageRoot();

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Long processorId;
    private Long brandId;

    @BeforeEach
    void fixture() {
        User processor = users.save(User.register(
                "profile-image-processor@test.com", "{noop}x", "가공사", "010-0000-0000", UserRole.PROCESSOR));
        User brand = users.save(User.register(
                "profile-image-brand@test.com", "{noop}x", "브랜드", "010-1111-1111", UserRole.BRAND));
        processorId = processor.getId();
        brandId = brand.getId();
        jdbc.update("insert into company_profile(user_id,company_name,business_number,zip_code,address) values (?,?,?,?,?)",
                processorId, "리뉴 가공소", "123-45-67890", "04790", "서울 성동구");
    }

    @Test
    void processorUploadsAndReadsRepresentativeImage() throws Exception {
        mvc.perform(authenticated(multipart("/api/v1/processors/me/profile/image")
                        .file(new MockMultipartFile("file", "logo.jpg", "image/jpeg", IMAGE)), processorId, "PROCESSOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").value("/api/v1/processors/me/profile/image"));

        String key = jdbc.queryForObject(
                "select logo_url from company_profile where user_id=?", String.class, processorId);
        assertThat(key).startsWith("PROCESSOR_PROFILE_IMAGE/" + processorId + "/");

        byte[] response = mvc.perform(authenticated(
                        get("/api/v1/processors/me/profile/image"), processorId, "PROCESSOR"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, max-age=0, no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(response).isEqualTo(IMAGE);
    }

    @Test
    void rejectsNonProcessorAndUnsupportedFile() throws Exception {
        mvc.perform(authenticated(multipart("/api/v1/processors/me/profile/image")
                        .file(new MockMultipartFile("file", "logo.jpg", "image/jpeg", IMAGE)), brandId, "BRAND"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROCESSOR_PROFILE_FORBIDDEN"));

        mvc.perform(authenticated(multipart("/api/v1/processors/me/profile/image")
                        .file(new MockMultipartFile("file", "logo.pdf", "application/pdf", IMAGE)), processorId, "PROCESSOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"));
    }

    @Test
    void reportsMissingRepresentativeImage() throws Exception {
        mvc.perform(authenticated(get("/api/v1/processors/me/profile/image"), processorId, "PROCESSOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROCESSOR_PROFILE_IMAGE_NOT_FOUND"));
    }

    private MockHttpServletRequestBuilder authenticated(
            MockHttpServletRequestBuilder request, Long userId, String role) {
        return request.with(jwt().jwt(token -> token.subject(userId.toString()))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static Path storageRoot() {
        try {
            return Files.createTempDirectory("processor-profile-image-");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
