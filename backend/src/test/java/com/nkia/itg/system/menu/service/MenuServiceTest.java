package com.nkia.itg.system.menu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.nkia.itg.fixture.SystemFixtures;
import com.nkia.itg.system.menu.dto.MenuTreeNode;
import com.nkia.itg.system.menu.entity.Menu;
import com.nkia.itg.system.menu.repository.MenuRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock
    private MenuRepository menuRepository;
    @InjectMocks
    private MenuService menuService;

    @Test
    @DisplayName("move: 트리 위치·정렬 변경을 Entity 도메인 메서드에 위임")
    void move_위치_정렬_변경() {
        Menu menu = SystemFixtures.menu(5L, "MENU-ITSM", "ITSM", "/itsm", null);
        given(menuRepository.findById(5L)).willReturn(Optional.of(menu));

        menuService.move(5L, 1L, 3);

        assertThat(menu.getParentId()).isEqualTo(1L);
        assertThat(menu.getSortOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("getTreeFor: permissionCode null + 사용자 보유 권한 매칭 메뉴를 트리로 조립")
    void getTreeFor_권한_매칭_트리_조립() {
        Menu root = SystemFixtures.menu(1L, "MENU-ROOT", "운영", "/", null);           // 누구나
        Menu itsm = SystemFixtures.childMenu(2L, root, "MENU-ITSM", "ITSM", "/itsm",
                "TICKET_CREATE");                                                       // 보유
        given(menuRepository.findAllByActiveTrueOrderBySortOrderAsc())
                .willReturn(List.of(root, itsm));

        List<MenuTreeNode> tree = menuService.getTreeFor(Set.of("TICKET_CREATE"));

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).id()).isEqualTo(1L);
        assertThat(tree.get(0).children()).hasSize(1);
        assertThat(tree.get(0).children().get(0).id()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getTreeFor: 사용자가 권한 없으면 그 메뉴 제외")
    void getTreeFor_권한_없으면_그_메뉴_제외() {
        Menu root = SystemFixtures.menu(1L, "MENU-ROOT", "운영", "/", null);
        Menu admin = SystemFixtures.childMenu(2L, root, "MENU-ADMIN", "관리", "/system",
                "USER_ADMIN");                                                          // 미보유
        given(menuRepository.findAllByActiveTrueOrderBySortOrderAsc())
                .willReturn(List.of(root, admin));

        List<MenuTreeNode> tree = menuService.getTreeFor(Set.of("TICKET_CREATE"));

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).id()).isEqualTo(1L);
        assertThat(tree.get(0).children()).isEmpty();
    }

    @Test
    @DisplayName("getTreeFor: 권한 코드 없는(null) 사용자에게도 공개 메뉴는 노출")
    void getTreeFor_권한없는_사용자_공개_메뉴() {
        Menu root = SystemFixtures.menu(1L, "MENU-ROOT", "운영", "/", null);
        Menu secured = SystemFixtures.menu(2L, "MENU-ADMIN", "관리", "/system", "USER_ADMIN");
        given(menuRepository.findAllByActiveTrueOrderBySortOrderAsc())
                .willReturn(List.of(root, secured));

        List<MenuTreeNode> tree = menuService.getTreeFor(null);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).id()).isEqualTo(1L);
    }
}
