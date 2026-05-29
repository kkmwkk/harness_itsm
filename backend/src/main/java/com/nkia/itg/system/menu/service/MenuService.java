package com.nkia.itg.system.menu.service;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.system.menu.dto.MenuCreateRequest;
import com.nkia.itg.system.menu.dto.MenuResponse;
import com.nkia.itg.system.menu.dto.MenuTreeNode;
import com.nkia.itg.system.menu.dto.MenuUpdateRequest;
import com.nkia.itg.system.menu.entity.Menu;
import com.nkia.itg.system.menu.repository.MenuRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메뉴 트리 CRUD + 권한 필터 트리. getTreeFor 는 비활성 메뉴를 제외하고
 * permissionCode 가 null(누구나)이거나 사용자가 보유한 권한과 매칭되는 메뉴만 트리로 조립한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class MenuService {

    private final MenuRepository menuRepository;

    public MenuResponse create(MenuCreateRequest req) {
        Menu menu = Menu.builder()
                .code(req.code())
                .label(req.label())
                .parentId(req.parentId())
                .icon(req.icon())
                .sortOrder(req.sortOrder())
                .route(req.route())
                .groupId(req.groupId())
                .permissionCode(req.permissionCode())
                .active(true)
                .build();
        return MenuResponse.from(menuRepository.save(menu));
    }

    @Transactional(readOnly = true)
    public MenuResponse getById(Long id) {
        return MenuResponse.from(loadOrThrow(id));
    }

    /** 관리자용 — 활성/비활성·권한 무관 전체 메뉴 평면 목록 (sort_order 무관, id 순). */
    @Transactional(readOnly = true)
    public List<MenuResponse> findAll() {
        return menuRepository.findAll().stream().map(MenuResponse::from).toList();
    }

    public MenuResponse update(Long id, MenuUpdateRequest req) {
        Menu menu = loadOrThrow(id);
        menu.update(req.label(), req.icon(), req.route(), req.groupId(), req.permissionCode());
        return MenuResponse.from(menu);
    }

    public void move(Long id, Long newParentId, int newSortOrder) {
        loadOrThrow(id).moveTo(newParentId, newSortOrder);
    }

    public void deactivate(Long id) {
        loadOrThrow(id).deactivate();
    }

    /**
     * 현재 사용자 권한에 맞춘 메뉴 트리.
     * @param userPermissionCodes 사용자가 보유한 권한 코드 (Controller 가 SecurityContext 에서 추출해 전달)
     */
    @Transactional(readOnly = true)
    public List<MenuTreeNode> getTreeFor(Collection<String> userPermissionCodes) {
        Set<String> perms = (userPermissionCodes == null)
                ? Set.of() : new HashSet<>(userPermissionCodes);

        // 1) 활성 메뉴 로드 → 2) 권한 필터
        Map<Long, MenuTreeNode> byId = new LinkedHashMap<>();
        for (Menu m : menuRepository.findAllByActiveTrueOrderBySortOrderAsc()) {
            if (m.getPermissionCode() == null || perms.contains(m.getPermissionCode())) {
                byId.put(m.getId(), MenuTreeNode.of(m));
            }
        }

        // 3) parent_id 트리 조립 (부모가 필터로 빠졌으면 루트로 승격)
        List<MenuTreeNode> roots = new ArrayList<>();
        for (MenuTreeNode node : byId.values()) {
            MenuTreeNode parent = (node.parentId() == null) ? null : byId.get(node.parentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children().add(node);
            }
        }
        return roots;
    }

    private Menu loadOrThrow(Long id) {
        return menuRepository.findById(id)
                .orElseThrow(() -> new ITGException(
                        "MENU_NOT_FOUND", "메뉴를 찾을 수 없습니다: " + id, HttpStatus.NOT_FOUND));
    }
}
