package com.nkia.itg.system.menu.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Menu 도메인 메서드 단위 테스트")
class MenuTest {

    private Menu menuSample() {
        return Menu.builder()
                .code("MENU-SAMPLE")
                .label("샘플 메뉴")
                .route("/sample")
                .sortOrder(1)
                .build();
    }

    @Test
    @DisplayName("setPermission — permission_code 설정·해제")
    void setPermission_설정_해제() {
        Menu menu = menuSample();

        menu.setPermission("TICKET_CREATE");
        assertThat(menu.getPermissionCode()).isEqualTo("TICKET_CREATE");

        menu.setPermission(null);
        assertThat(menu.getPermissionCode()).isNull();
    }

    @Test
    @DisplayName("moveTo — parent_id·sort_order 이동")
    void moveTo_이동() {
        Menu menu = menuSample();

        menu.moveTo(10L, 5);

        assertThat(menu.getParentId()).isEqualTo(10L);
        assertThat(menu.getSortOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("deactivate — active false")
    void deactivate_active_false() {
        Menu menu = menuSample();
        assertThat(menu.isActive()).isTrue();

        menu.deactivate();

        assertThat(menu.isActive()).isFalse();
    }
}
