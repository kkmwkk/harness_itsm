package com.nkia.itg.system.dept.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Department 도메인 메서드 단위 테스트")
class DepartmentTest {

    private Department deptSample() {
        return Department.builder()
                .code("DEPT-SAMPLE")
                .name("샘플 부서")
                .path("/1/")
                .build();
    }

    @Test
    @DisplayName("assignManager — manager_user_id 설정")
    void assignManager_설정() {
        Department dept = deptSample();

        dept.assignManager(42L);

        assertThat(dept.getManagerUserId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("removeManager — manager_user_id 해제")
    void removeManager_해제() {
        Department dept = deptSample();
        dept.assignManager(42L);

        dept.removeManager();

        assertThat(dept.getManagerUserId()).isNull();
    }

    @Test
    @DisplayName("deactivate · activate — active 토글")
    void deactivate_activate_토글() {
        Department dept = deptSample();
        assertThat(dept.isActive()).isTrue();

        dept.deactivate();
        assertThat(dept.isActive()).isFalse();

        dept.activate();
        assertThat(dept.isActive()).isTrue();
    }
}
