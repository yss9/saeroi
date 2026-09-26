package com.ssafy.upcycle.processing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class ProcessingRequestQueryServiceTests {

    @Test
    void detailAcceptsMySqlLocalDateTimeValues() {
        UserRepository users = mock(UserRepository.class);
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        ProcessingRequestQueryService service = new ProcessingRequestQueryService(users, jdbc);
        User brand = User.register("brand@test.com", "hash", "brand", "010", UserRole.BRAND);
        when(users.findById(2L)).thenReturn(Optional.of(brand));

        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 10, 17, 13, 57);
        LocalDateTime expiresAt = createdAt.plusDays(7);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("request_code", "PR-1");
        row.put("brand_id", 2L);
        row.put("brand_name", "brand");
        row.put("processor_id", 1L);
        row.put("processor_name", "processor");
        row.put("status", "REQUESTED");
        row.put("created_at", createdAt);
        row.put("expires_at", expiresAt);
        row.put("material_id", 3L);
        row.put("material_code", "MAT-3");
        row.put("material_name", "cotton");
        row.put("material_type", "COTTON");
        row.put("quantity", 1_000L);
        row.put("process_type", "CLEANING");
        row.put("sequence_no", 1);
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class)))
                .thenReturn(List.of(row));

        var detail = service.detail(2L, 1L);

        assertThat(detail.createdAt()).isEqualTo(createdAt);
        assertThat(detail.expiresAt()).isEqualTo(expiresAt);
    }
}
