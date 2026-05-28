package com.nkia.itg.meta.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nkia.itg.meta.domain.MetaStatus;
import com.nkia.itg.meta.domain.PackageType;
import com.nkia.itg.meta.domain.SystemType;
import com.nkia.itg.meta.entity.PageMeta;
import com.nkia.itg.support.PostgresIntegrationTestBase;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class MetaRepositoryIT extends PostgresIntegrationTestBase {

    @Autowired
    MetaRepository repository;

    @Autowired
    EntityManager entityManager;

    private PageMeta build(String id, String groupId, int major, int minor,
                            MetaStatus status, SystemType systemType, PackageType packageType) {
        return PageMeta.builder()
                .id(id)
                .title("샘플 페이지 " + id)
                .systemType(systemType)
                .packageType(packageType)
                .groupId(groupId)
                .majorVersion(major)
                .minorVersion(minor)
                .metaStatus(status)
                .metaJson(Map.of("api", "/api/sample"))
                .active(true)
                .build();
    }

    private void nativeInsert(String id, String groupId, int major, int minor, MetaStatus status,
                               SystemType systemType, PackageType packageType) {
        entityManager.createNativeQuery("""
                INSERT INTO page_meta
                  (id, title, system_type, package_type, group_id,
                   major_version, minor_version, meta_status, meta_json, active,
                   created_at, updated_at)
                VALUES
                  (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), TRUE, NOW(), NOW())
                """)
                .setParameter(1, id)
                .setParameter(2, "샘플 페이지 " + id)
                .setParameter(3, systemType.name())
                .setParameter(4, packageType.name())
                .setParameter(5, groupId)
                .setParameter(6, major)
                .setParameter(7, minor)
                .setParameter(8, status.name())
                .setParameter(9, "{\"api\":\"/api/sample\"}")
                .executeUpdate();
    }

    @Test
    @DisplayName("findTopByGroupId — PUBLISHED 가 여러개일 때 가장 높은 버전 반환")
    void findTop_returnsHighestVersion() {
        nativeInsert("itg-ticket-v1-1", "itg-ticket", 1, 1, MetaStatus.PUBLISHED,
                SystemType.ITSM, PackageType.PACKAGE);
        nativeInsert("itg-ticket-v1-2", "itg-ticket", 1, 2, MetaStatus.PUBLISHED,
                SystemType.ITSM, PackageType.PACKAGE);
        entityManager.flush();
        entityManager.clear();

        Optional<PageMeta> result = repository
                .findTopByGroupIdAndMetaStatusAndActiveTrueOrderByMajorVersionDescMinorVersionDesc(
                        "itg-ticket", MetaStatus.PUBLISHED);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("itg-ticket-v1-2");
        assertThat(result.get().getMinorVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("findMaxMinorVersion — 그룹 내 같은 major 의 최대 minor 반환")
    void findMaxMinorVersion_returnsMaxWithinMajor() {
        nativeInsert("grp-v1-1", "grp", 1, 1, MetaStatus.DRAFT, SystemType.ITSM, PackageType.PACKAGE);
        nativeInsert("grp-v1-2", "grp", 1, 2, MetaStatus.DRAFT, SystemType.ITSM, PackageType.PACKAGE);
        nativeInsert("grp-v1-3", "grp", 1, 3, MetaStatus.DRAFT, SystemType.ITSM, PackageType.PACKAGE);
        nativeInsert("grp-v2-1", "grp", 2, 1, MetaStatus.DRAFT, SystemType.ITSM, PackageType.PACKAGE);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findMaxMinorVersion("grp", 1)).contains(3);
        assertThat(repository.findMaxMinorVersion("grp", 2)).contains(1);
    }

    @Test
    @DisplayName("findMaxMinorVersion — 매칭 없으면 Optional.empty")
    void findMaxMinorVersion_noMatchReturnsEmpty() {
        Optional<Integer> result = repository.findMaxMinorVersion("non-existent-group", 1);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("deprecatePublished — 같은 그룹의 다른 PUBLISHED 들을 모두 DEPRECATED")
    void deprecatePublished_marksOthers() {
        nativeInsert("grp-v1-1", "grp", 1, 1, MetaStatus.PUBLISHED,
                SystemType.ITSM, PackageType.PACKAGE);
        nativeInsert("grp-v1-2", "grp", 1, 2, MetaStatus.PUBLISHED,
                SystemType.ITSM, PackageType.PACKAGE);
        entityManager.flush();
        entityManager.clear();

        int affected = repository.deprecatePublished("grp", "grp-v1-2");

        assertThat(affected).isEqualTo(1);

        PageMeta v11 = repository.findById("grp-v1-1").orElseThrow();
        PageMeta v12 = repository.findById("grp-v1-2").orElseThrow();
        assertThat(v11.getMetaStatus()).isEqualTo(MetaStatus.DEPRECATED);
        assertThat(v12.getMetaStatus()).isEqualTo(MetaStatus.PUBLISHED);
    }

    @Test
    @DisplayName("자동 DEPRECATE 트리거 동작 확인 — DRAFT→PUBLISHED 전환 시 기존 PUBLISHED 가 DEPRECATED")
    void autoDeprecateTrigger() {
        PageMeta v11 = repository.saveAndFlush(
                build("trg-v1-1", "trg", 1, 1, MetaStatus.PUBLISHED,
                        SystemType.ITSM, PackageType.PACKAGE));
        PageMeta v12 = repository.saveAndFlush(
                build("trg-v1-2", "trg", 1, 2, MetaStatus.DRAFT,
                        SystemType.ITSM, PackageType.PACKAGE));

        v12.publish();
        repository.saveAndFlush(v12);
        entityManager.clear();

        PageMeta reloadedV11 = repository.findById("trg-v1-1").orElseThrow();
        PageMeta reloadedV12 = repository.findById("trg-v1-2").orElseThrow();
        assertThat(reloadedV11.getMetaStatus()).isEqualTo(MetaStatus.DEPRECATED);
        assertThat(reloadedV12.getMetaStatus()).isEqualTo(MetaStatus.PUBLISHED);
    }

    @Test
    @DisplayName("UNIQUE 제약 위반 — 동일 (groupId, major, minor) 두 건 저장 시 예외")
    void uniqueConstraintViolation() {
        repository.saveAndFlush(build("uq-v1-1", "uq", 1, 1, MetaStatus.DRAFT,
                SystemType.ITSM, PackageType.PACKAGE));

        assertThatThrownBy(() -> repository.saveAndFlush(
                build("uq-v1-1-dup", "uq", 1, 1, MetaStatus.DRAFT,
                        SystemType.ITSM, PackageType.PACKAGE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findAllBySystemType — ITSM PUBLISHED active 만 정렬된 순서로 반환")
    void findAllBySystemType_filtersAndSorts() {
        nativeInsert("itsm-a-v1-1", "itsm-a", 1, 1, MetaStatus.PUBLISHED,
                SystemType.ITSM, PackageType.PACKAGE);
        nativeInsert("itsm-a-v1-2", "itsm-a", 1, 2, MetaStatus.PUBLISHED,
                SystemType.ITSM, PackageType.PACKAGE);
        nativeInsert("itsm-b-v1-1", "itsm-b", 1, 1, MetaStatus.PUBLISHED,
                SystemType.ITSM, PackageType.PACKAGE);
        nativeInsert("itsm-c-v1-1", "itsm-c", 1, 1, MetaStatus.DRAFT,
                SystemType.ITSM, PackageType.PACKAGE);
        nativeInsert("itam-x-v1-1", "itam-x", 1, 1, MetaStatus.PUBLISHED,
                SystemType.ITAM, PackageType.PACKAGE);
        entityManager.flush();
        entityManager.clear();

        List<PageMeta> result = repository
                .findAllBySystemTypeAndMetaStatusAndActiveTrueOrderByGroupIdAscMajorVersionDescMinorVersionDesc(
                        SystemType.ITSM, MetaStatus.PUBLISHED);

        assertThat(result).extracting(PageMeta::getId)
                .containsExactly("itsm-a-v1-2", "itsm-a-v1-1", "itsm-b-v1-1");
    }

    @Test
    @DisplayName("findAllByPackageType — PACKAGE 만 반환, 정렬 검증")
    void findAllByPackageType_filtersAndSorts() {
        nativeInsert("pkg-a-v1-1", "pkg-a", 1, 1, MetaStatus.DRAFT,
                SystemType.ITSM, PackageType.PACKAGE);
        nativeInsert("pkg-a-v2-1", "pkg-a", 2, 1, MetaStatus.PUBLISHED,
                SystemType.ITSM, PackageType.PACKAGE);
        nativeInsert("pkg-b-v1-1", "pkg-b", 1, 1, MetaStatus.PUBLISHED,
                SystemType.ITSM, PackageType.PACKAGE);
        nativeInsert("cst-x-v1-1", "cst-x", 1, 1, MetaStatus.PUBLISHED,
                SystemType.ITSM, PackageType.CUSTOM);
        entityManager.flush();
        entityManager.clear();

        List<PageMeta> result = repository
                .findAllByPackageTypeOrderByGroupIdAscMajorVersionDescMinorVersionDesc(
                        PackageType.PACKAGE);

        assertThat(result).extracting(PageMeta::getId)
                .containsExactly("pkg-a-v2-1", "pkg-a-v1-1", "pkg-b-v1-1");
    }
}
