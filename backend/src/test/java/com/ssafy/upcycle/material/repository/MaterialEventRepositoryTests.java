package com.ssafy.upcycle.material.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.material.entity.MaterialEvent;
import com.ssafy.upcycle.material.entity.MaterialEventReferenceType;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MaterialEventRepositoryTests {

    @Autowired
    private MaterialEventRepository repository;

    @Test
    void findsEventsInChronologicalOrder() {
        repository.save(event(1L, "second", LocalDateTime.of(2026, 9, 10, 12, 1)));
        repository.save(event(1L, "first", LocalDateTime.of(2026, 9, 10, 12, 0)));

        var result = repository.findAllByMaterialIdOrderByOccurredAtAscIdAsc(1L, PageRequest.of(0, 20));

        assertThat(result.getContent())
                .extracting(MaterialEvent::getIdempotencyKey)
                .containsExactly("first", "second");
    }

    @Test
    void rejectsDuplicateIdempotencyKeys() {
        repository.saveAndFlush(event(1L, "same", LocalDateTime.of(2026, 9, 10, 12, 0)));

        assertThatThrownBy(() -> repository.saveAndFlush(
                event(1L, "same", LocalDateTime.of(2026, 9, 10, 12, 1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private MaterialEvent event(Long materialId, String key, LocalDateTime time) {
        return MaterialEvent.record(
                materialId,
                MaterialEventType.MATERIAL_REGISTERED,
                null,
                MaterialEventReferenceType.MATERIAL,
                materialId,
                null,
                100L,
                null,
                key,
                time);
    }
}
