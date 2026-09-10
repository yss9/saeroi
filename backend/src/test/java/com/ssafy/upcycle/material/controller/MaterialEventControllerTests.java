package com.ssafy.upcycle.material.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialEvent;
import com.ssafy.upcycle.material.entity.MaterialEventReferenceType;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialEventRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MaterialEventControllerTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private UserRepository userRepository;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private MaterialEventRepository eventRepository;

    private String token;
    private Long materialId;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        User user = userRepository.save(User.register(
                "event-reader@example.com", "{noop}x", "조회자", "010-1111-2222", UserRole.CONSUMER));
        token = jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
        Material material = materialRepository.save(Material.register(
                "MAT-EVENT-1", MaterialType.DENIM, MaterialRootOrigin.WASTE,
                "이벤트 소재", null, null, 1_000L, null, null, user.getId()));
        materialId = material.getId();
        eventRepository.saveAll(List.of(
                event(MaterialEventType.SOURCE_VERIFIED, "second", LocalDateTime.of(2026, 9, 10, 12, 1)),
                event(MaterialEventType.MATERIAL_REGISTERED, "first", LocalDateTime.of(2026, 9, 10, 12, 0))));
    }

    @AfterEach
    void tearDown() {
        eventRepository.deleteAll();
        materialRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/materials/" + materialId + "/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTheChronologicalMaterialTimeline() throws Exception {
        mockMvc.perform(get("/api/materials/" + materialId + "/events")
                        .param("page", "0")
                        .param("size", "20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.materialId").value(materialId))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.events[0].eventType").value("MATERIAL_REGISTERED"))
                .andExpect(jsonPath("$.data.events[1].eventType").value("SOURCE_VERIFIED"));
    }

    private MaterialEvent event(MaterialEventType type, String key, LocalDateTime time) {
        return MaterialEvent.record(materialId, type, null,
                MaterialEventReferenceType.MATERIAL, materialId, null, null, null, key, time);
    }
}
