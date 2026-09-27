package com.ssafy.upcycle.processing.service;

import static com.ssafy.upcycle.processing.ProcessingErrorCode.*;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.service.MaterialProcessHistoryResolver;
import com.ssafy.upcycle.processing.dto.*;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.processing.entity.ProcessingEstimate;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 브랜드가 가공사를 고르는 화면.
 *
 * <h2>견적이 사라진 뒤 이 화면이 하는 일</h2>
 *
 * <p>예전에는 여기서 업체를 고르면 견적을 요청하고, 가공사가 금액과 소요일을 적어 보내고,
 * 브랜드가 그중 하나를 골랐다. 지금은 <b>이 목록에 이미 금액과 소요일이 적혀 있다.</b>
 * 가공사가 미리 등록해 둔 단가와 하루 처리량으로 서버가 계산해 내려보낸다.
 *
 * <p><b>화면이 계산하지 않는다.</b> 단가와 수량을 함께 주고 화면이 곱하게 하면, 올림·반올림이
 * 어긋나는 순간 목록에 보인 금액과 결제창 금액이 달라진다 — 그건 사용자가 결제 직전에야
 * 알게 되는 종류의 오류다. 그래서 {@code estimatedPrice}·{@code estimatedDays} 를 서버가
 * 준다. 요청을 만들 때도 {@link ProcessingEstimate} 의 같은 식을 쓰므로 두 값이 일치한다.
 *
 * <h2>여러 공정을 한 번에 맡길 때</h2>
 *
 * <p>금액은 공정별 금액의 <b>합</b>이고, 소요일도 <b>합</b>이다. 세척한 뒤에 선별하는 것이지
 * 동시에 하는 것이 아니므로 기간은 더해진다. 금액을 합산할 때 공정마다 올림한 뒤 더하는데,
 * 요청 생성도 똑같이 공정마다 올림해 더하므로 어긋나지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ProcessorSearchService {

    /**
     * 목록에 보일 업체 이름. {@code users.name} 은 가입한 사람(대표자)의 이름이라 브랜드가 고르는
     * 업체를 가리키지 못한다 — 업체명을 쓰고, 비어 있을 때만 가입자 이름으로 둔다
     * ({@code agent.service.CompanyNames} 와 같은 규칙).
     */
    private static final String DISPLAY_NAME =
            "coalesce(nullif(trim(company.company_name),''),u.name) as name";

    private final UserRepository users;
    private final NamedParameterJdbcTemplate jdbc;
    private final MaterialProcessHistoryResolver processHistoryResolver;

    /** 가공사 검색. 필터가 하나도 없으면 등록된 업체를 그냥 나열한다. */
    @Transactional(readOnly = true)
    public ProcessorSearchResponse search(Long brandId, String materialType, String quantity,
            List<String> processTypes, String page, String size) {
        requireBrand(brandId);
        long pageNumber = unsignedNumber(page);
        long pageSize = unsignedNumber(size);
        if (pageNumber > Integer.MAX_VALUE || pageSize < 1 || pageSize > 50) {
            throw new BusinessException(PROCESSING_INVALID_REQUEST);
        }
        boolean filtersEmpty = (materialType == null || materialType.isBlank())
                && (quantity == null || quantity.isBlank())
                && (processTypes == null || processTypes.isEmpty());
        if (filtersEmpty) return browse(pageNumber, pageSize);

        long grams = unsignedNumber(quantity);
        List<String> purposes;
        try {
            MaterialType.valueOf(materialType);
            if (processTypes == null || processTypes.isEmpty() || processTypes.size() > 8) {
                throw new IllegalArgumentException();
            }
            purposes = processTypes.stream().map(s -> ProcessType.valueOf(s).name()).toList();
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(PROCESSING_INVALID_REQUEST);
        }
        if (grams == 0 || new HashSet<>(purposes).size() != purposes.size()) {
            throw new BusinessException(PROCESSING_INVALID_REQUEST);
        }

        var params = new HashMap<String, Object>();
        params.put("materialType", materialType);
        params.put("purposes", purposes);
        params.put("purposeCount", purposes.size());
        params.put("limit", pageSize);
        params.put("offset", pageNumber * pageSize);

        // 수량 상한이 사라졌다 — 많이 맡기면 오래 걸릴 뿐 못 받는 것이 아니다. 대신
        // 하루 처리량이 0 인 줄은 거른다: 소요일의 분모라 계산 자체가 성립하지 않는다.
        String matching = """
                from processor_capability c
                join processor_profile p on p.user_id=c.processor_profile_id
                join users u on u.id=p.user_id
                join company_profile company on company.user_id=p.user_id
                where u.role='PROCESSOR' and u.status='ACTIVE'
                  and company.role_approval_status='APPROVED'
                  and c.material_type=:materialType and c.process_type in (:purposes)
                  and c.daily_capacity>0
                group by p.user_id,u.name,company.company_name,p.region
                having count(distinct c.process_type)=:purposeCount
                """;
        Long total = jdbc.queryForObject(
                "select count(*) from (select p.user_id " + matching + ") matched", params, Long.class);

        var matched = jdbc.query("select p.user_id," + DISPLAY_NAME + ",p.region " + matching
                        + " order by p.user_id limit :limit offset :offset", params,
                (rs, row) -> new Matched(rs.getLong("user_id"), rs.getString("name"), rs.getString("region")));
        if (matched.isEmpty()) {
            return new ProcessorSearchResponse(List.of(), total == null ? 0 : total,
                    (int) pageNumber, (int) pageSize);
        }

        params.put("processorIds", matched.stream().map(Matched::processorId).toList());
        Map<Long, Map<String, Terms>> termsByProcessor = new HashMap<>();
        jdbc.query("""
                select c.processor_profile_id,c.process_type,c.daily_capacity,c.price_per_kg
                from processor_capability c
                where c.processor_profile_id in (:processorIds)
                  and c.material_type=:materialType
                  and c.process_type in (:purposes)
                """, params, (rs, row) -> {
            termsByProcessor
                    .computeIfAbsent(rs.getLong("processor_profile_id"), ignored -> new HashMap<>())
                    .put(rs.getString("process_type"),
                            new Terms(rs.getLong("daily_capacity"), rs.getLong("price_per_kg")));
            return null;
        });

        List<ProcessorSearchResponse.Item> items = matched.stream()
                .map(row -> toItem(row, termsByProcessor.getOrDefault(row.processorId(), Map.of()),
                        purposes, grams))
                .toList();
        return new ProcessorSearchResponse(items, total == null ? 0 : total,
                (int) pageNumber, (int) pageSize);
    }

    /**
     * 공정별 조건을 합쳐 한 업체의 한 줄을 만든다.
     *
     * <p>단가는 더하고 소요일도 더한다. 하루 처리량은 가장 <b>느린</b> 공정의 값을 싣는다 —
     * 여러 공정을 한 줄에 요약하면서 "이 업체는 하루에 얼마나 소화하나" 를 답하려면 병목이
     * 정직하다. 가장 빠른 값을 실으면 실제보다 빨라 보인다.
     */
    private ProcessorSearchResponse.Item toItem(
            Matched row, Map<String, Terms> terms, List<String> purposes, long grams) {
        long pricePerKg = 0;
        long slowestCapacity = Long.MAX_VALUE;
        long estimatedPrice = 0;
        int estimatedDays = 0;
        List<ProcessorSearchResponse.EstimatedLeadTime> leadTimes = new ArrayList<>();
        for (String purpose : purposes) {
            Terms found = terms.get(purpose);
            if (found == null) {
                // having 절이 걸러 주므로 정상 데이터에서는 오지 않는다. 그래도 넘어간다 —
                // 업체 하나 때문에 검색 전체가 500 이 되는 것보다 낫다.
                continue;
            }
            pricePerKg += found.pricePerKg();
            slowestCapacity = Math.min(slowestCapacity, found.dailyCapacity());
            estimatedPrice += ProcessingEstimate.fee(found.pricePerKg(), grams);
            int days = ProcessingEstimate.leadDays(found.dailyCapacity(), grams);
            estimatedDays += days;
            leadTimes.add(new ProcessorSearchResponse.EstimatedLeadTime(
                    ProcessType.valueOf(purpose), days));
        }
        return new ProcessorSearchResponse.Item(row.processorId(), row.name(), row.region(),
                slowestCapacity == Long.MAX_VALUE ? 0 : slowestCapacity, pricePerKg,
                estimatedPrice, estimatedDays, leadTimes);
    }

    /**
     * 필터 없이 둘러보기.
     *
     * <p>수량이 없으므로 예상 금액도 소요일도 계산할 수 없다 — {@code null} 로 둔다. 0 으로
     * 채우면 "공짜로 오늘 끝난다" 가 되어, 값이 없다는 사실보다 나쁜 거짓말이 된다.
     * 대신 가장 싼 단가와 가장 큰 하루 처리량을 실어 대략을 보여 준다.
     */
    private ProcessorSearchResponse browse(long pageNumber, long pageSize) {
        var params = new HashMap<String, Object>();
        params.put("limit", pageSize);
        params.put("offset", pageNumber * pageSize);
        String registered = """
                from processor_profile p
                join users u on u.id=p.user_id
                join company_profile company on company.user_id=p.user_id
                join processor_capability c on c.processor_profile_id=p.user_id
                where u.role='PROCESSOR' and u.status='ACTIVE'
                  and company.role_approval_status='APPROVED'
                  and c.daily_capacity>0
                group by p.user_id,u.name,company.company_name,p.region
                """;
        Long total = jdbc.queryForObject(
                "select count(*) from (select p.user_id " + registered + ") registered", params, Long.class);
        var items = jdbc.query("select p.user_id," + DISPLAY_NAME + ",p.region,"
                + " max(c.daily_capacity) as daily_capacity,min(c.price_per_kg) as price_per_kg "
                + registered + " order by p.user_id limit :limit offset :offset", params,
                (rs, row) -> new ProcessorSearchResponse.Item(rs.getLong("user_id"), rs.getString("name"),
                        rs.getString("region"), rs.getLong("daily_capacity"), rs.getLong("price_per_kg"),
                        null, null, List.of()));
        return new ProcessorSearchResponse(items, total == null ? 0 : total, (int) pageNumber, (int) pageSize);
    }

    /**
     * 가공을 맡길 수 있는 내 소재.
     *
     * <p>{@code completedProcessTypes} 를 함께 싣는다. 화면이 "이 소재는 세척까지 됐으니
     * 이번엔 선별을 맡기자" 를 그 자리에서 판단해야 하는데, 이 값이 없으면 보유 소재 목록
     * ({@code GET /api/materials/me})을 한 번 더 불러 같은 소재를 다시 읽는다.
     */
    @Transactional(readOnly = true)
    public List<ProcessingMaterialResponse> materials(Long brandId) {
        requireBrand(brandId);
        List<ProcessingMaterialResponse> held = jdbc.query("""
                select m.id,m.material_code,m.name,m.material_type,b.quantity,
                  coalesce(l.locked,0) as locked
                from material_balance b join material m on m.id=b.material_id
                left join (select material_id,owner_id,sum(amount) as locked from material_lock
                           where status='ACTIVE' group by material_id,owner_id) l
                  on l.material_id=b.material_id and l.owner_id=b.owner_id
                where b.owner_id=:owner and m.status='ACTIVE' and b.quantity>0
                order by m.id
                """, Map.of("owner", brandId), (rs, row) -> {
            long quantity = rs.getLong("quantity");
            long locked = rs.getLong("locked");
            return new ProcessingMaterialResponse(rs.getLong("id"), rs.getString("material_code"),
                    rs.getString("name"), rs.getString("material_type"), quantity, locked,
                    Math.max(0, quantity - locked), List.of());
        });
        // 목록 전체를 한 번에 넘긴다. 소재마다 부르면 혈통 세대마다 쿼리가 목록 길이만큼 늘어난다.
        var processTypes = processHistoryResolver.resolveAll(
                held.stream().map(ProcessingMaterialResponse::materialId).toList());
        return held.stream()
                .map(row -> row.withCompletedProcessTypes(
                        processTypes.getOrDefault(row.materialId(), List.of())))
                .toList();
    }

    private void requireBrand(Long id) {
        if (id == null || users.findById(id).filter(u -> u.isActive() && u.getRole() == UserRole.BRAND).isEmpty()) {
            throw new BusinessException(PROCESSING_FORBIDDEN);
        }
    }

    private long unsignedNumber(String raw) {
        try {
            if (raw == null || !raw.matches("[0-9]{1,19}")) throw new NumberFormatException();
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            throw new BusinessException(PROCESSING_INVALID_REQUEST);
        }
    }

    /** 조건에 걸린 업체 한 줄. 가격·기간은 다음 쿼리에서 붙는다. */
    private record Matched(Long processorId, String name, String region) {
    }

    /** 한 업체의 한 공정에 대한 조건. */
    private record Terms(long dailyCapacity, long pricePerKg) {
    }
}
