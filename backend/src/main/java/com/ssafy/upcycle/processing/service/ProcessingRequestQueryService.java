package com.ssafy.upcycle.processing.service;

import static com.ssafy.upcycle.processing.ProcessingErrorCode.PROCESSING_FORBIDDEN;
import static com.ssafy.upcycle.processing.ProcessingErrorCode.PROCESSING_INVALID_QUERY;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.processing.dto.ProcessingRequestListResponse;
import com.ssafy.upcycle.processing.dto.ProcessingRequestDetailResponse;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.processing.entity.ProcessingRequestStatus;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProcessingRequestQueryService {
    private final UserRepository users;
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public ProcessingRequestDetailResponse detail(Long actorId, Long requestId) {
        User actor = users.findById(actorId).filter(User::isActive)
                .orElseThrow(() -> new BusinessException(PROCESSING_FORBIDDEN));
        String sql = """
                select r.id,r.request_code,r.brand_id,b.name brand_name,r.processor_id,p.name processor_name,
                       r.status,r.created_at,r.expires_at,i.material_id,m.material_code,m.name material_name,
                       m.material_type,i.quantity,t.process_type,t.sequence_no
                from processing_request r join users b on b.id=r.brand_id join users p on p.id=r.processor_id
                join processing_request_input i on i.processing_request_id=r.id
                join material m on m.id=i.material_id join processing_request_type t on t.processing_request_id=r.id
                where r.id=:id order by t.sequence_no
                """;
        var rows = jdbc.queryForList(sql, new MapSqlParameterSource("id", requestId));
        if (rows.isEmpty()) throw new BusinessException(com.ssafy.upcycle.processing.ProcessingErrorCode.PROCESSING_REQUEST_NOT_FOUND);
        var first = rows.getFirst();
        Long brandId = ((Number) first.get("brand_id")).longValue();
        Long processorId = ((Number) first.get("processor_id")).longValue();
        if (!actorId.equals(brandId) && !actorId.equals(processorId)) throw new BusinessException(PROCESSING_FORBIDDEN);
        List<ProcessType> processTypes = rows.stream().map(r -> ProcessType.valueOf((String) r.get("process_type"))).toList();
        return new ProcessingRequestDetailResponse(((Number) first.get("id")).longValue(), (String) first.get("request_code"),
                brandId, (String) first.get("brand_name"), processorId, (String) first.get("processor_name"),
                ((Number) first.get("material_id")).longValue(), (String) first.get("material_code"),
                (String) first.get("material_name"), MaterialType.valueOf((String) first.get("material_type")),
                ((Number) first.get("quantity")).longValue(), processTypes,
                ProcessingRequestStatus.valueOf((String) first.get("status")),
                localDateTime(first.get("created_at")),
                localDateTime(first.get("expires_at")));
    }

    @Transactional(readOnly = true)
    public ProcessingRequestListResponse list(Long actorId, String statusValue, String pageValue, String sizeValue) {
        User actor = users.findById(actorId).filter(User::isActive)
                .orElseThrow(() -> new BusinessException(PROCESSING_FORBIDDEN));
        int page = parseUnsigned(pageValue, 0, Integer.MAX_VALUE);
        int size = parseUnsigned(sizeValue, 1, 50);
        ProcessingRequestStatus status = parseStatus(statusValue);

        String scope = switch (actor.getRole()) {
            case BRAND -> "r.brand_id=:actor";
            case PROCESSOR -> "r.processor_id=:actor";
            default -> "1=0";
        };
        String statusClause = status == null ? "" : " and r.status=:status";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("actor", actorId)
                .addValue("status", status == null ? null : status.name())
                .addValue("limit", size)
                .addValue("offset", (long) page * size);

        long total = jdbc.queryForObject(
                "select count(*) from processing_request r where " + scope + statusClause,
                params, Long.class);
        List<Long> ids = jdbc.queryForList(
                "select r.id from processing_request r where " + scope + statusClause
                        + " order by r.created_at desc,r.id desc limit :limit offset :offset",
                params, Long.class);
        if (ids.isEmpty()) return new ProcessingRequestListResponse(List.of(), total, page, size);

        params.addValue("ids", ids);
        String details = """
                select r.id,r.request_code,r.brand_id,b.name as brand_name,
                       r.processor_id,p.name as processor_name,r.status,r.created_at,r.expires_at,
                       i.material_id,m.material_code,m.name as material_name,m.material_type,i.quantity,
                       t.process_type,t.sequence_no
                from processing_request r
                join users b on b.id=r.brand_id
                join users p on p.id=r.processor_id
                join processing_request_input i on i.processing_request_id=r.id
                join material m on m.id=i.material_id
                join processing_request_type t on t.processing_request_id=r.id
                where r.id in (:ids)
                order by r.created_at desc,r.id desc,t.sequence_no
                """;
        Map<Long, ItemBuilder> grouped = new LinkedHashMap<>();
        jdbc.query(details, params, rs -> {
            ItemBuilder item = grouped.computeIfAbsent(rs.getLong("id"), ignored -> mapItem(rs));
            item.processTypes.add(ProcessType.valueOf(rs.getString("process_type")));
        });
        return new ProcessingRequestListResponse(
                grouped.values().stream().map(ItemBuilder::build).toList(), total, page, size);
    }

    private ProcessingRequestStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return ProcessingRequestStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(PROCESSING_INVALID_QUERY);
        }
    }

    private int parseUnsigned(String value, int minimum, int maximum) {
        if (value == null || !value.matches("\\d{1,10}")) throw new BusinessException(PROCESSING_INVALID_QUERY);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minimum || parsed > maximum) throw new BusinessException(PROCESSING_INVALID_QUERY);
            return parsed;
        } catch (NumberFormatException exception) {
            throw new BusinessException(PROCESSING_INVALID_QUERY);
        }
    }

    private static LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) return localDateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        throw new IllegalStateException("Unsupported processing request timestamp value: "
                + (value == null ? "null" : value.getClass().getName()));
    }

    private ItemBuilder mapItem(ResultSet rs) {
        try {
            return new ItemBuilder(
                    rs.getLong("id"), rs.getString("request_code"), rs.getLong("brand_id"),
                    rs.getString("brand_name"), rs.getLong("processor_id"), rs.getString("processor_name"),
                    rs.getLong("material_id"), rs.getString("material_code"), rs.getString("material_name"),
                    MaterialType.valueOf(rs.getString("material_type")), rs.getLong("quantity"),
                    ProcessingRequestStatus.valueOf(rs.getString("status")),
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getTimestamp("expires_at").toLocalDateTime());
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not map processing request", exception);
        }
    }

    private static final class ItemBuilder {
        private final Long requestId;
        private final String requestCode;
        private final Long brandId;
        private final String brandName;
        private final Long processorId;
        private final String processorName;
        private final Long materialId;
        private final String materialCode;
        private final String materialName;
        private final MaterialType materialType;
        private final long quantity;
        private final ProcessingRequestStatus status;
        private final java.time.LocalDateTime createdAt;
        private final java.time.LocalDateTime expiresAt;
        private final List<ProcessType> processTypes = new ArrayList<>();

        private ItemBuilder(Long requestId, String requestCode, Long brandId, String brandName,
                Long processorId, String processorName, Long materialId, String materialCode,
                String materialName, MaterialType materialType, long quantity,
                ProcessingRequestStatus status, java.time.LocalDateTime createdAt,
                java.time.LocalDateTime expiresAt) {
            this.requestId = requestId;
            this.requestCode = requestCode;
            this.brandId = brandId;
            this.brandName = brandName;
            this.processorId = processorId;
            this.processorName = processorName;
            this.materialId = materialId;
            this.materialCode = materialCode;
            this.materialName = materialName;
            this.materialType = materialType;
            this.quantity = quantity;
            this.status = status;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }

        private ProcessingRequestListResponse.Item build() {
            return new ProcessingRequestListResponse.Item(
                    requestId, requestCode, brandId, brandName, processorId, processorName,
                    materialId, materialCode, materialName, materialType, quantity,
                    List.copyOf(processTypes), status, createdAt, expiresAt);
        }
    }
}
