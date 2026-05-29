package com.nkia.itg.meta.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nkia.itg.meta.domain.MetaStatus;
import com.nkia.itg.meta.domain.PackageType;
import com.nkia.itg.meta.domain.SystemType;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PageMeta 도메인 메서드 단위 테스트")
class PageMetaTest {

    private PageMeta sampleDraft() {
        Map<String, Object> metaJson = new HashMap<>();
        metaJson.put("api", "/api/samples");
        metaJson.put("title", "샘플-페이지");
        return PageMeta.builder()
                .id("itg-sample-v1-1")
                .title("샘플-페이지")
                .systemType(SystemType.ITSM)
                .packageType(PackageType.PACKAGE)
                .groupId("itg-sample")
                .majorVersion(1)
                .minorVersion(1)
                .metaStatus(MetaStatus.DRAFT)
                .metaJson(metaJson)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("publish — DRAFT 에서 PUBLISHED 로 전이")
    void publish_DRAFT_에서_PUBLISHED_로_전이() {
        // given
        PageMeta meta = sampleDraft();

        // when
        meta.publish();

        // then
        assertThat(meta.getMetaStatus()).isEqualTo(MetaStatus.PUBLISHED);
    }

    @Test
    @DisplayName("publish — PUBLISHED 에서 재호출 시 예외")
    void publish_PUBLISHED_에서_재호출_시_예외() {
        // given
        PageMeta meta = sampleDraft();
        meta.publish();

        // when & then
        assertThatThrownBy(meta::publish)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("publish — ARCHIVED 에서 호출 시 예외")
    void publish_ARCHIVED_에서_호출_시_예외() {
        // given
        PageMeta meta = PageMeta.builder()
                .id("itg-sample-v1-1")
                .title("샘플-페이지")
                .systemType(SystemType.ITSM)
                .packageType(PackageType.PACKAGE)
                .groupId("itg-sample")
                .majorVersion(1)
                .minorVersion(1)
                .metaStatus(MetaStatus.ARCHIVED)
                .metaJson(new HashMap<>())
                .active(true)
                .build();

        // when & then
        assertThatThrownBy(meta::publish)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("archive — 어느 상태에서든 ARCHIVED 로 변경")
    void archive_어느_상태에서든_ARCHIVED_로_변경() {
        // given
        MetaStatus[] startingStates = {
                MetaStatus.DRAFT,
                MetaStatus.PUBLISHED,
                MetaStatus.DEPRECATED
        };

        for (MetaStatus start : startingStates) {
            PageMeta meta = PageMeta.builder()
                    .id("itg-sample-v1-1")
                    .title("샘플-페이지")
                    .systemType(SystemType.ITSM)
                    .packageType(PackageType.PACKAGE)
                    .groupId("itg-sample")
                    .majorVersion(1)
                    .minorVersion(1)
                    .metaStatus(start)
                    .metaJson(new HashMap<>())
                    .active(true)
                    .build();

            // when
            meta.archive();

            // then
            assertThat(meta.getMetaStatus())
                    .as("시작 상태 %s → ARCHIVED", start)
                    .isEqualTo(MetaStatus.ARCHIVED);
        }
    }

    @Test
    @DisplayName("copyAs — 복사본은 항상 DRAFT 이고 minorVersion 은 지정값")
    void copyAs_복사본은_항상_DRAFT_이고_minorVersion_은_지정값() {
        // given
        PageMeta original = sampleDraft();
        original.publish();
        assertThat(original.getMetaStatus()).isEqualTo(MetaStatus.PUBLISHED);

        // when
        PageMeta copy = original.copyAs("itg-sample-v1-3", 3);

        // then
        assertThat(copy.getMetaStatus()).isEqualTo(MetaStatus.DRAFT);
        assertThat(copy.getMinorVersion()).isEqualTo(3);
        assertThat(copy.getId()).isEqualTo("itg-sample-v1-3");
        assertThat(copy.getGroupId()).isEqualTo(original.getGroupId());
        assertThat(copy.getMajorVersion()).isEqualTo(original.getMajorVersion());
        assertThat(copy.getSystemType()).isEqualTo(original.getSystemType());
        assertThat(copy.getPackageType()).isEqualTo(original.getPackageType());
        assertThat(copy.getTitle()).isEqualTo(original.getTitle());
        assertThat(copy.isActive()).isTrue();
    }

    @Test
    @DisplayName("copyAs — 원본 metaJson 과 복사본 metaJson 은 독립 인스턴스")
    void copyAs_원본_metaJson_과_복사본_metaJson_은_독립_인스턴스() {
        // given
        PageMeta original = sampleDraft();

        // when
        PageMeta copy = original.copyAs("itg-sample-v1-2", 2);
        copy.getMetaJson().put("newKey", "newValue");

        // then
        assertThat(copy.getMetaJson()).containsKey("newKey");
        assertThat(original.getMetaJson()).doesNotContainKey("newKey");
        assertThat(copy.getMetaJson()).isNotSameAs(original.getMetaJson());
    }

    @Test
    @DisplayName("copyAs — minorVersion 0 이하 입력 시 예외")
    void copyAs_minorVersion_0_이하_입력_시_예외() {
        // given
        PageMeta original = sampleDraft();

        // when & then
        assertThatThrownBy(() -> original.copyAs("itg-sample-v1-0", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minorVersion");

        assertThatThrownBy(() -> original.copyAs("itg-sample-v1-neg", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minorVersion");
    }

    @Test
    @DisplayName("replaceBody — DRAFT 의 본문을 통째로 교체한다")
    void replaceBody_DRAFT_본문_교체() {
        // given
        PageMeta meta = sampleDraft();

        // when
        meta.replaceBody(Map.of("api", "/api/changed"));

        // then
        assertThat(meta.getMetaJson()).containsEntry("api", "/api/changed");
        assertThat(meta.getMetaJson()).doesNotContainKey("title");
    }

    @Test
    @DisplayName("replaceBody — DRAFT 가 아니면 예외")
    void replaceBody_DRAFT_아니면_예외() {
        // given
        PageMeta meta = sampleDraft();
        meta.publish();

        // when & then
        assertThatThrownBy(() -> meta.replaceBody(Map.of("api", "/api/x")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("versionLabel — v{major}.{minor} 포맷")
    void versionLabel_은_v_major_dot_minor_포맷() {
        // given
        PageMeta meta = PageMeta.builder()
                .id("itg-sample-v2-5")
                .title("샘플-페이지")
                .systemType(SystemType.ITSM)
                .packageType(PackageType.PACKAGE)
                .groupId("itg-sample")
                .majorVersion(2)
                .minorVersion(5)
                .metaStatus(MetaStatus.PUBLISHED)
                .metaJson(new HashMap<>())
                .active(true)
                .build();

        // when
        String label = meta.versionLabel();

        // then
        assertThat(label).isEqualTo("v2.5");
    }
}
