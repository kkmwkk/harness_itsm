package com.nkia.itg.meta.service;

import com.nkia.itg.meta.dto.PageMetaCreateRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * PageMeta 후보 JSON 의 형식·필수 필드·field type 호환을 사전 검증한다 (dry-run).
 *
 * <p>DB 접근·INSERT 는 일어나지 않는다. 순수 검증만 수행한다.
 * ERROR 가 1건이라도 있으면 {@link ValidationResult#valid()} 는 {@code false}.
 * WARNING 은 정보성이며 valid 판정에 영향을 주지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MetaValidationService {

    /** ARCHITECTURE §5 필드 타입 매핑표 기준 허용 FieldType 12종. */
    private static final Set<String> FIELD_TYPES = Set.of(
            "text", "textarea", "number", "date", "date-range", "select",
            "checkbox", "radio", "user-picker", "file", "status", "priority");

    private static final Set<String> ACTION_TYPES = Set.of(
            "dialog-form", "export", "navigate", "custom");

    private static final Set<String> FORM_LAYOUTS = Set.of("single-column", "two-column");

    private static final Set<String> SYSTEM_TYPES = Set.of("ITSM", "ITAM", "PMS", "COMMON", "SYSTEM");

    private static final Set<String> PACKAGE_TYPES = Set.of("PACKAGE", "CUSTOM");

    private static final Set<String> META_STATUSES = Set.of("DRAFT", "PUBLISHED", "DEPRECATED", "ARCHIVED");

    private static final Pattern GROUP_ID_PATTERN = Pattern.compile("^[A-Za-z0-9-]+$");

    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9-]+-v\\d+-\\d+$");

    public ValidationResult validate(PageMetaCreateRequest req) {
        List<ValidationIssue> issues = new ArrayList<>();
        validateAxes(req, issues);
        validateMetaJson(req, issues);
        boolean valid = issues.stream()
                .noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
        return new ValidationResult(valid, issues);
    }

    // ---------------------------------------------------------------- 필수 분류 축

    private void validateAxes(PageMetaCreateRequest req, List<ValidationIssue> issues) {
        // id
        if (isBlank(req.id())) {
            issues.add(error("id", "INVALID_ID_FORMAT", "id 가 비어 있습니다."));
        } else if (canComposeId(req)) {
            String expected = "%s-v%d-%d".formatted(req.groupId(), req.majorVersion(), req.minorVersion());
            if (!expected.equals(req.id())) {
                issues.add(error("id", "INVALID_ID_FORMAT",
                        "id 는 {groupId}-v{major}-{minor} 패턴이어야 합니다. 기대값: " + expected + ", 실제: " + req.id()));
            }
        } else if (!ID_PATTERN.matcher(req.id()).matches()) {
            issues.add(error("id", "INVALID_ID_FORMAT",
                    "id 는 {groupId}-v{major}-{minor} 패턴이어야 합니다: " + req.id()));
        }

        // title
        if (isBlank(req.title())) {
            issues.add(error("title", "MISSING_TITLE", "title 이 비어 있습니다."));
        }

        // systemType
        if (req.systemType() == null || !SYSTEM_TYPES.contains(req.systemType().name())) {
            issues.add(error("systemType", "INVALID_SYSTEM_TYPE",
                    "systemType 은 ITSM|ITAM|PMS|COMMON|SYSTEM 중 하나여야 합니다."));
        }

        // packageType
        if (req.packageType() == null || !PACKAGE_TYPES.contains(req.packageType().name())) {
            issues.add(error("packageType", "INVALID_PACKAGE_TYPE",
                    "packageType 은 PACKAGE|CUSTOM 중 하나여야 합니다."));
        }

        // groupId
        if (isBlank(req.groupId())) {
            issues.add(error("groupId", "INVALID_GROUP_ID", "groupId 가 비어 있습니다."));
        } else if (!GROUP_ID_PATTERN.matcher(req.groupId()).matches()) {
            issues.add(error("groupId", "INVALID_GROUP_ID",
                    "groupId 는 영숫자·하이픈만 허용됩니다: " + req.groupId()));
        }

        // majorVersion
        if (req.majorVersion() == null || req.majorVersion() < 1) {
            issues.add(error("majorVersion", "INVALID_MAJOR_VERSION", "majorVersion 은 1 이상이어야 합니다."));
        }

        // minorVersion
        if (req.minorVersion() == null || req.minorVersion() < 1) {
            issues.add(error("minorVersion", "INVALID_MINOR_VERSION", "minorVersion 은 1 이상이어야 합니다."));
        }

        // metaStatus
        if (req.metaStatus() == null || !META_STATUSES.contains(req.metaStatus().name())) {
            issues.add(error("metaStatus", "INVALID_META_STATUS",
                    "metaStatus 는 DRAFT|PUBLISHED|DEPRECATED|ARCHIVED 중 하나여야 합니다."));
        } else if (req.metaStatus().name().equals("PUBLISHED")) {
            issues.add(warning("metaStatus", "PUBLISHED_ON_CREATE",
                    "신규 생성 메타는 DRAFT 권장. PUBLISHED 로 생성하면 검토 없이 화면에 노출될 수 있습니다."));
        }
    }

    private boolean canComposeId(PageMetaCreateRequest req) {
        return !isBlank(req.groupId())
                && req.majorVersion() != null && req.majorVersion() >= 1
                && req.minorVersion() != null && req.minorVersion() >= 1;
    }

    // ---------------------------------------------------------------- metaJson 본문

    private void validateMetaJson(PageMetaCreateRequest req, List<ValidationIssue> issues) {
        Map<String, Object> meta = req.resolvedMetaJson();
        if (meta == null || meta.isEmpty()) {
            issues.add(error("metaJson", "MISSING_META_JSON",
                    "metaJson 본문(api/grid/form)이 없습니다."));
            return;
        }

        validateApi(meta, issues);
        List<String> gridFields = validateGrid(meta, issues);
        Set<String> formFieldNames = validateForm(meta, issues);
        validateGridFormMatch(gridFields, formFieldNames, issues);
        validateActions(meta, issues);
    }

    private void validateApi(Map<String, Object> meta, List<ValidationIssue> issues) {
        Object api = meta.get("api");
        if (!(api instanceof String s) || s.isBlank()) {
            issues.add(error("api", "MISSING_API", "api 가 비어 있습니다."));
        } else if (!s.startsWith("/api/")) {
            issues.add(error("api", "INVALID_API", "api 는 /api/ 로 시작해야 합니다: " + s));
        }
    }

    private List<String> validateGrid(Map<String, Object> meta, List<ValidationIssue> issues) {
        List<String> gridFields = new ArrayList<>();
        Object grid = meta.get("grid");
        if (!(grid instanceof Map<?, ?> gridMap)) {
            issues.add(error("grid", "MISSING_GRID", "grid 가 없습니다."));
            return gridFields;
        }
        Object columns = gridMap.get("columns");
        if (!(columns instanceof List<?> list)) {
            issues.add(error("grid.columns", "INVALID_GRID_COLUMNS", "grid.columns 는 배열이어야 합니다."));
            return gridFields;
        }
        for (int i = 0; i < list.size(); i++) {
            String base = "grid.columns[" + i + "]";
            if (!(list.get(i) instanceof Map<?, ?> col)) {
                issues.add(error(base, "INVALID_GRID_COLUMN", "grid 컬럼은 객체여야 합니다."));
                continue;
            }
            String field = requireString(col, "field", base + ".field", "MISSING_COLUMN_FIELD", issues);
            requireString(col, "label", base + ".label", "MISSING_COLUMN_LABEL", issues);
            requireString(col, "type", base + ".type", "MISSING_COLUMN_TYPE", issues);
            if (field != null) {
                gridFields.add(field);
            }
        }
        return gridFields;
    }

    private Set<String> validateForm(Map<String, Object> meta, List<ValidationIssue> issues) {
        Set<String> fieldNames = new HashSet<>();
        Object form = meta.get("form");
        if (!(form instanceof Map<?, ?> formMap)) {
            issues.add(error("form", "MISSING_FORM", "form 이 없습니다."));
            return fieldNames;
        }

        Object layout = formMap.get("layout");
        if (!(layout instanceof String ls) || !FORM_LAYOUTS.contains(ls)) {
            issues.add(error("form.layout", "INVALID_FORM_LAYOUT",
                    "form.layout 은 single-column|two-column 중 하나여야 합니다."));
        }

        Object fields = formMap.get("fields");
        if (!(fields instanceof List<?> list)) {
            issues.add(error("form.fields", "INVALID_FORM_FIELDS", "form.fields 는 배열이어야 합니다."));
            return fieldNames;
        }

        if (list.isEmpty()) {
            issues.add(warning("form.fields", "FORM_FIELDS_EMPTY",
                    "form.fields 가 비어 있습니다 (조회 전용 메타)."));
        }

        for (int i = 0; i < list.size(); i++) {
            String base = "form.fields[" + i + "]";
            if (!(list.get(i) instanceof Map<?, ?> fld)) {
                issues.add(error(base, "INVALID_FORM_FIELD", "form 필드는 객체여야 합니다."));
                continue;
            }
            String name = requireString(fld, "name", base + ".name", "MISSING_FIELD_NAME", issues);
            requireString(fld, "label", base + ".label", "MISSING_FIELD_LABEL", issues);
            Object type = fld.get("type");
            if (!(type instanceof String ts) || ts.isBlank()) {
                issues.add(error(base + ".type", "MISSING_FIELD_TYPE", "필드 type 이 비어 있습니다."));
            } else if (!FIELD_TYPES.contains(ts)) {
                issues.add(error(base + ".type", "INVALID_FIELD_TYPE",
                        "허용되지 않은 field type: " + ts + " (허용: " + FIELD_TYPES + ")"));
            }
            if (name != null) {
                fieldNames.add(name);
            }
        }
        return fieldNames;
    }

    private void validateGridFormMatch(List<String> gridFields, Set<String> formFieldNames,
                                       List<ValidationIssue> issues) {
        if (gridFields.isEmpty() || formFieldNames.isEmpty()) {
            return;
        }
        boolean anyMatch = gridFields.stream().anyMatch(formFieldNames::contains);
        if (!anyMatch) {
            issues.add(warning("grid.columns", "GRID_FORM_MISMATCH",
                    "grid.columns 의 field 가 form.fields 의 name 과 한 개도 매칭되지 않습니다."));
        }
    }

    private void validateActions(Map<String, Object> meta, List<ValidationIssue> issues) {
        Object actions = meta.get("actions");
        if (actions == null) {
            issues.add(warning("actions", "ACTIONS_EMPTY", "actions 가 없습니다 (등록 버튼 없음)."));
            return;
        }
        if (!(actions instanceof List<?> list)) {
            issues.add(error("actions", "INVALID_ACTIONS", "actions 는 배열이어야 합니다."));
            return;
        }
        if (list.isEmpty()) {
            issues.add(warning("actions", "ACTIONS_EMPTY", "actions 가 비어 있습니다 (등록 버튼 없음)."));
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            String base = "actions[" + i + "]";
            if (!(list.get(i) instanceof Map<?, ?> act)) {
                issues.add(error(base, "INVALID_ACTION", "action 은 객체여야 합니다."));
                continue;
            }
            requireString(act, "id", base + ".id", "MISSING_ACTION_ID", issues);
            requireString(act, "label", base + ".label", "MISSING_ACTION_LABEL", issues);
            Object type = act.get("type");
            if (!(type instanceof String ts) || ts.isBlank()) {
                issues.add(error(base + ".type", "MISSING_ACTION_TYPE", "action type 이 비어 있습니다."));
            } else if (!ACTION_TYPES.contains(ts)) {
                issues.add(error(base + ".type", "INVALID_ACTION_TYPE",
                        "허용되지 않은 action type: " + ts + " (허용: " + ACTION_TYPES + ")"));
            }
        }
    }

    // ---------------------------------------------------------------- 헬퍼

    private String requireString(Map<?, ?> map, String key, String path, String code,
                                 List<ValidationIssue> issues) {
        Object v = map.get(key);
        if (!(v instanceof String s) || s.isBlank()) {
            issues.add(error(path, code, key + " 이(가) 비어 있습니다."));
            return null;
        }
        return s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static ValidationIssue error(String path, String code, String message) {
        return new ValidationIssue(ValidationIssue.Severity.ERROR, path, code, message);
    }

    private static ValidationIssue warning(String path, String code, String message) {
        return new ValidationIssue(ValidationIssue.Severity.WARNING, path, code, message);
    }

    // ---------------------------------------------------------------- 결과 타입

    public record ValidationResult(
            boolean valid,
            List<ValidationIssue> issues
    ) {
    }

    public record ValidationIssue(
            Severity severity,
            String path,
            String code,
            String message
    ) {
        public enum Severity {
            ERROR,
            WARNING
        }
    }
}
