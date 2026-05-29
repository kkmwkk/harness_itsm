package com.nkia.itg.meta.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nkia.itg.meta.domain.MetaStatus;
import com.nkia.itg.meta.domain.PackageType;
import com.nkia.itg.meta.domain.SystemType;
import com.nkia.itg.meta.dto.PageMetaCreateRequest;
import com.nkia.itg.meta.service.MetaValidationService.ValidationIssue;
import com.nkia.itg.meta.service.MetaValidationService.ValidationIssue.Severity;
import com.nkia.itg.meta.service.MetaValidationService.ValidationResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MetaValidationService 단위 테스트")
class MetaValidationServiceTest {

    private final MetaValidationService service = new MetaValidationService();

    // ---------------------------------------------------------------- 빌더 헬퍼

    private Map<String, Object> column(String field, String label, String type) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("field", field);
        c.put("label", label);
        c.put("type", type);
        return c;
    }

    private Map<String, Object> field(String name, String label, String type) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("name", name);
        f.put("label", label);
        f.put("type", type);
        return f;
    }

    private Map<String, Object> action(String id, String label, String type) {
        Map<String, Object> a = new LinkedHashMap<>();
        if (id != null) {
            a.put("id", id);
        }
        a.put("label", label);
        a.put("type", type);
        return a;
    }

    /** ERROR 0건이 되는 정상 metaJson 본문. */
    private Map<String, Object> validMetaJson() {
        List<Object> columns = new ArrayList<>();
        columns.add(column("title", "제목", "text"));

        List<Object> fields = new ArrayList<>();
        fields.add(field("title", "제목", "text"));

        Map<String, Object> grid = new LinkedHashMap<>();
        grid.put("columns", columns);

        Map<String, Object> form = new LinkedHashMap<>();
        form.put("layout", "two-column");
        form.put("fields", fields);

        List<Object> actions = new ArrayList<>();
        actions.add(action("create", "등록", "dialog-form"));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("api", "/api/tickets");
        meta.put("grid", grid);
        meta.put("form", form);
        meta.put("actions", actions);
        return meta;
    }

    /** ERROR 0건이 되는 정상 요청. */
    private PageMetaCreateRequest validRequest() {
        return new PageMetaCreateRequest(
                "itg-ticket-v1-1", "ITSM 티켓 관리",
                SystemType.ITSM, PackageType.PACKAGE, "itg-ticket",
                1, 1, MetaStatus.DRAFT, validMetaJson());
    }

    private boolean hasCode(ValidationResult r, String code) {
        return r.issues().stream().anyMatch(i -> i.code().equals(code));
    }

    private List<ValidationIssue> errors(ValidationResult r) {
        return r.issues().stream().filter(i -> i.severity() == Severity.ERROR).toList();
    }

    // ---------------------------------------------------------------- 케이스

    @Test
    @DisplayName("1. 정상 메타 — ERROR 0건, valid true")
    void valid_정상_메타_ERROR_0() {
        ValidationResult r = service.validate(validRequest());

        assertThat(errors(r)).isEmpty();
        assertThat(r.valid()).isTrue();
    }

    @Test
    @DisplayName("2. id 패턴 불일치 — ERROR INVALID_ID_FORMAT")
    void id_패턴_불일치_ERROR_INVALID_ID_FORMAT() {
        PageMetaCreateRequest req = new PageMetaCreateRequest(
                "bad_id", "ITSM 티켓 관리",
                SystemType.ITSM, PackageType.PACKAGE, "itg-ticket",
                1, 1, MetaStatus.DRAFT, validMetaJson());

        ValidationResult r = service.validate(req);

        assertThat(r.valid()).isFalse();
        assertThat(hasCode(r, "INVALID_ID_FORMAT")).isTrue();
    }

    @Test
    @DisplayName("3. systemType null — ERROR INVALID_SYSTEM_TYPE")
    void systemType_null_ERROR() {
        PageMetaCreateRequest req = new PageMetaCreateRequest(
                "itg-ticket-v1-1", "ITSM 티켓 관리",
                null, PackageType.PACKAGE, "itg-ticket",
                1, 1, MetaStatus.DRAFT, validMetaJson());

        ValidationResult r = service.validate(req);

        assertThat(r.valid()).isFalse();
        assertThat(hasCode(r, "INVALID_SYSTEM_TYPE")).isTrue();
    }

    @Test
    @DisplayName("4. groupId 특수문자 — ERROR INVALID_GROUP_ID")
    void groupId_특수문자_ERROR() {
        PageMetaCreateRequest req = new PageMetaCreateRequest(
                "bad!id-v1-1", "ITSM 티켓 관리",
                SystemType.ITSM, PackageType.PACKAGE, "bad!id",
                1, 1, MetaStatus.DRAFT, validMetaJson());

        ValidationResult r = service.validate(req);

        assertThat(r.valid()).isFalse();
        assertThat(hasCode(r, "INVALID_GROUP_ID")).isTrue();
    }

    @Test
    @DisplayName("5. minorVersion 0 이하 — ERROR INVALID_MINOR_VERSION")
    void minorVersion_0_이하_ERROR() {
        PageMetaCreateRequest req = new PageMetaCreateRequest(
                "itg-ticket-v1-1", "ITSM 티켓 관리",
                SystemType.ITSM, PackageType.PACKAGE, "itg-ticket",
                1, 0, MetaStatus.DRAFT, validMetaJson());

        ValidationResult r = service.validate(req);

        assertThat(r.valid()).isFalse();
        assertThat(hasCode(r, "INVALID_MINOR_VERSION")).isTrue();
    }

    @Test
    @DisplayName("6. metaStatus 없거나 invalid — ERROR INVALID_META_STATUS")
    void metaStatus_없거나_invalid_ERROR() {
        PageMetaCreateRequest req = new PageMetaCreateRequest(
                "itg-ticket-v1-1", "ITSM 티켓 관리",
                SystemType.ITSM, PackageType.PACKAGE, "itg-ticket",
                1, 1, null, validMetaJson());

        ValidationResult r = service.validate(req);

        assertThat(r.valid()).isFalse();
        assertThat(hasCode(r, "INVALID_META_STATUS")).isTrue();
    }

    @Test
    @DisplayName("7. metaJson.api 누락 — ERROR MISSING_API")
    void metaJson_api_누락_ERROR() {
        Map<String, Object> meta = validMetaJson();
        meta.remove("api");
        PageMetaCreateRequest req = new PageMetaCreateRequest(
                "itg-ticket-v1-1", "ITSM 티켓 관리",
                SystemType.ITSM, PackageType.PACKAGE, "itg-ticket",
                1, 1, MetaStatus.DRAFT, meta);

        ValidationResult r = service.validate(req);

        assertThat(r.valid()).isFalse();
        assertThat(hasCode(r, "MISSING_API")).isTrue();
    }

    @Test
    @DisplayName("8. form.fields[2].type=email — ERROR INVALID_FIELD_TYPE (path 표시)")
    void metaJson_form_fields_2_type_email_ERROR_INVALID_FIELD_TYPE() {
        Map<String, Object> meta = validMetaJson();
        @SuppressWarnings("unchecked")
        List<Object> fields = (List<Object>) ((Map<String, Object>) meta.get("form")).get("fields");
        fields.add(field("content", "본문", "text"));
        fields.add(field("email", "이메일", "email")); // index 2, 허용되지 않은 타입

        PageMetaCreateRequest req = new PageMetaCreateRequest(
                "itg-ticket-v1-1", "ITSM 티켓 관리",
                SystemType.ITSM, PackageType.PACKAGE, "itg-ticket",
                1, 1, MetaStatus.DRAFT, meta);

        ValidationResult r = service.validate(req);

        assertThat(r.valid()).isFalse();
        assertThat(r.issues())
                .anySatisfy(i -> {
                    assertThat(i.code()).isEqualTo("INVALID_FIELD_TYPE");
                    assertThat(i.path()).isEqualTo("form.fields[2].type");
                });
    }

    @Test
    @DisplayName("9. actions.id 누락 — ERROR MISSING_ACTION_ID")
    void actions_id_누락_ERROR() {
        Map<String, Object> meta = validMetaJson();
        List<Object> actions = new ArrayList<>();
        actions.add(action(null, "등록", "dialog-form"));
        meta.put("actions", actions);

        PageMetaCreateRequest req = new PageMetaCreateRequest(
                "itg-ticket-v1-1", "ITSM 티켓 관리",
                SystemType.ITSM, PackageType.PACKAGE, "itg-ticket",
                1, 1, MetaStatus.DRAFT, meta);

        ValidationResult r = service.validate(req);

        assertThat(r.valid()).isFalse();
        assertThat(hasCode(r, "MISSING_ACTION_ID")).isTrue();
    }

    @Test
    @DisplayName("10. metaStatus=PUBLISHED 신규 — WARNING PUBLISHED_ON_CREATE, valid 유지")
    void metaStatus_PUBLISHED_신규_WARNING_DRAFT_권장() {
        PageMetaCreateRequest req = new PageMetaCreateRequest(
                "itg-ticket-v1-1", "ITSM 티켓 관리",
                SystemType.ITSM, PackageType.PACKAGE, "itg-ticket",
                1, 1, MetaStatus.PUBLISHED, validMetaJson());

        ValidationResult r = service.validate(req);

        assertThat(hasCode(r, "PUBLISHED_ON_CREATE")).isTrue();
        assertThat(r.issues())
                .filteredOn(i -> i.code().equals("PUBLISHED_ON_CREATE"))
                .allSatisfy(i -> assertThat(i.severity()).isEqualTo(Severity.WARNING));
        assertThat(r.valid()).isTrue(); // WARNING 은 valid 판정에 영향 없음
    }

    @Test
    @DisplayName("11. grid.columns 와 form.fields 매칭 0 — WARNING GRID_FORM_MISMATCH")
    void grid_columns_와_form_fields_매칭_0_WARNING() {
        Map<String, Object> meta = validMetaJson();
        List<Object> columns = new ArrayList<>();
        columns.add(column("nonMatchingField", "안맞는컬럼", "text"));
        @SuppressWarnings("unchecked")
        Map<String, Object> grid = (Map<String, Object>) meta.get("grid");
        grid.put("columns", columns);

        PageMetaCreateRequest req = new PageMetaCreateRequest(
                "itg-ticket-v1-1", "ITSM 티켓 관리",
                SystemType.ITSM, PackageType.PACKAGE, "itg-ticket",
                1, 1, MetaStatus.DRAFT, meta);

        ValidationResult r = service.validate(req);

        assertThat(hasCode(r, "GRID_FORM_MISMATCH")).isTrue();
        assertThat(r.issues())
                .filteredOn(i -> i.code().equals("GRID_FORM_MISMATCH"))
                .allSatisfy(i -> assertThat(i.severity()).isEqualTo(Severity.WARNING));
        assertThat(r.valid()).isTrue();
    }

    @Test
    @DisplayName("12. ERROR 가 1건이라도 있으면 valid false")
    void valid_ERROR_가_있으면_valid_false() {
        PageMetaCreateRequest req = new PageMetaCreateRequest(
                "itg-ticket-v1-1", "ITSM 티켓 관리",
                null, PackageType.PACKAGE, "itg-ticket",
                1, 1, MetaStatus.DRAFT, validMetaJson());

        ValidationResult r = service.validate(req);

        assertThat(errors(r)).isNotEmpty();
        assertThat(r.valid()).isFalse();
    }
}
