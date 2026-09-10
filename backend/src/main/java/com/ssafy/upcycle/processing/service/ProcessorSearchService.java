package com.ssafy.upcycle.processing.service;

import static com.ssafy.upcycle.processing.ProcessingErrorCode.*;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.processing.dto.*;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProcessorSearchService {
    private final UserRepository users;
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public ProcessorSearchResponse search(Long brandId, String materialType, String quantity,
            List<String> processTypes, String page, String size) {
        requireBrand(brandId);
        long grams = unsignedNumber(quantity);
        long pageNumber = unsignedNumber(page);
        long pageSize = unsignedNumber(size);
        List<String> purposes;
        try {
            MaterialType.valueOf(materialType);
            if (processTypes == null || processTypes.isEmpty() || processTypes.size() > 8) throw new IllegalArgumentException();
            purposes = processTypes.stream().map(s -> ProcessType.valueOf(s).name()).toList();
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(PROCESSING_INVALID_REQUEST);
        }
        if (grams == 0 || pageNumber > Integer.MAX_VALUE || pageSize < 1 || pageSize > 50
                || new HashSet<>(purposes).size() != purposes.size()) throw new BusinessException(PROCESSING_INVALID_REQUEST);
        var params = new HashMap<String,Object>();
        params.put("materialType",materialType);
        params.put("quantity",grams);
        params.put("purposes",purposes);
        params.put("purposeCount",purposes.size());
        params.put("limit",pageSize);
        params.put("offset",pageNumber * pageSize);
        String matching = """
                from processor_capability c
                join processor_profile p on p.user_id=c.processor_profile_id
                join users u on u.id=p.user_id
                where u.role='PROCESSOR' and u.status='ACTIVE'
                  and c.material_type=:materialType and c.process_type in (:purposes)
                  and c.min_quantity<=:quantity and c.max_quantity>=:quantity
                  and exists (select 1 from processor_lead_time configured
                              where configured.processor_profile_id=p.user_id
                                and configured.material_type=c.material_type
                                and configured.process_type=c.process_type
                                and configured.max_quantity>=:quantity)
                group by p.user_id,u.name,p.region
                having count(distinct c.process_type)=:purposeCount
                """;
        Long total = jdbc.queryForObject("select count(*) from (select p.user_id " + matching + ") matched",params,Long.class);
        String select = """
                select p.user_id,u.name,p.region,max(c.min_quantity) as min_quantity,min(c.max_quantity) as max_quantity
                """;
        var items = jdbc.query(select + matching + " order by p.user_id limit :limit offset :offset",params,
                (rs,row) -> new ProcessorSearchResponse.Item(rs.getLong("user_id"),rs.getString("name"),
                        rs.getString("region"),rs.getLong("min_quantity"),rs.getLong("max_quantity"),
                        List.of()));
        if (!items.isEmpty()) {
            params.put("processorIds", items.stream().map(ProcessorSearchResponse.Item::processorId).toList());
            Map<Long, Map<String, Integer>> estimates = new HashMap<>();
            var leadTimeRows = jdbc.query("""
                    select t.processor_profile_id,t.process_type,t.lead_days
                    from processor_lead_time t
                    where t.processor_profile_id in (:processorIds)
                      and t.material_type=:materialType
                      and t.process_type in (:purposes)
                      and t.max_quantity=(select min(t2.max_quantity) from processor_lead_time t2
                        where t2.processor_profile_id=t.processor_profile_id
                          and t2.material_type=t.material_type
                          and t2.process_type=t.process_type
                          and t2.max_quantity>=:quantity)
                    """, params, (rs, row) -> new LeadTimeRow(rs.getLong("processor_profile_id"),
                            rs.getString("process_type"), rs.getInt("lead_days")));
            leadTimeRows.forEach(row -> estimates
                    .computeIfAbsent(row.processorId(), ignored -> new HashMap<>())
                    .put(row.processType(), row.leadDays()));
            items = items.stream().map(item -> new ProcessorSearchResponse.Item(item.processorId(), item.name(),
                    item.region(), item.minQuantity(), item.maxQuantity(), purposes.stream()
                            .map(process -> new ProcessorSearchResponse.EstimatedLeadTime(ProcessType.valueOf(process),
                                    estimates.get(item.processorId()).get(process)))
                            .toList())).toList();
        }
        return new ProcessorSearchResponse(items,total == null ? 0 : total,(int)pageNumber,(int)pageSize);
    }

    @Transactional(readOnly = true)
    public List<ProcessingMaterialResponse> materials(Long brandId) {
        requireBrand(brandId);
        return jdbc.query("""
                select m.id,m.material_code,m.name,m.material_type,b.quantity,
                  coalesce(l.locked,0) as locked
                from material_balance b join material m on m.id=b.material_id
                left join (select material_id,owner_id,sum(amount) as locked from material_lock
                           where status='ACTIVE' group by material_id,owner_id) l
                  on l.material_id=b.material_id and l.owner_id=b.owner_id
                where b.owner_id=:owner and m.status='ACTIVE' and b.quantity>0
                order by m.id
                """, Map.of("owner",brandId),(rs,row) -> {
                    long quantity=rs.getLong("quantity");
                    long locked=rs.getLong("locked");
                    return new ProcessingMaterialResponse(rs.getLong("id"),rs.getString("material_code"),
                            rs.getString("name"),rs.getString("material_type"),quantity,locked,Math.max(0,quantity-locked));
                });
    }

    private void requireBrand(Long id) {
        if (id == null || users.findById(id).filter(u -> u.isActive() && u.getRole()==UserRole.BRAND).isEmpty())
            throw new BusinessException(PROCESSING_FORBIDDEN);
    }

    private long unsignedNumber(String raw) {
        try {
            if (raw == null || !raw.matches("[0-9]{1,19}")) throw new NumberFormatException();
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            throw new BusinessException(PROCESSING_INVALID_REQUEST);
        }
    }

    private record LeadTimeRow(Long processorId, String processType, int leadDays) {}
}
