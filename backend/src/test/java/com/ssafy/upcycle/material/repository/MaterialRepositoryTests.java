package com.ssafy.upcycle.material.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class MaterialRepositoryTests {

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savedMaterialIsFoundByMaterialCode() {
        materialRepository.save(registerDenim("MAT-000001"));
        entityManager.flush();
        entityManager.clear();

        Optional<Material> found = materialRepository.findByMaterialCode("MAT-000001");

        assertThat(found).isPresent();
        assertThat(found.get().getMaterialType()).isEqualTo(MaterialType.DENIM);
        assertThat(found.get().getRootOrigin()).isEqualTo(MaterialRootOrigin.WASTE);
    }

    @Test
    void unknownMaterialCodeIsNotFound() {
        assertThat(materialRepository.findByMaterialCode("MAT-999999")).isEmpty();
        assertThat(materialRepository.existsByMaterialCode("MAT-999999")).isFalse();
    }

    @Test
    void existsByMaterialCodeReflectsSavedMaterial() {
        materialRepository.save(registerDenim("MAT-000002"));
        entityManager.flush();

        assertThat(materialRepository.existsByMaterialCode("MAT-000002")).isTrue();
    }

    @Test
    void persistedMaterialGetsCreatedAt() {
        Material saved = materialRepository.save(registerDenim("MAT-000003"));
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    private Material registerDenim(String materialCode) {
        return Material.register(
                materialCode,
                MaterialType.DENIM,
                MaterialRootOrigin.WASTE,
                "폐데님",
                "수거된 폐청바지",
                "A",
                20_000L,
                32_000L,
                1_500L,
                1L);
    }
}
