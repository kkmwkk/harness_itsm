package com.nkia.itg.system.menu.repository;

import com.nkia.itg.system.menu.entity.Menu;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    /** 활성 메뉴만 sort_order 오름차순 — 트리 조립 입력. */
    List<Menu> findAllByActiveTrueOrderBySortOrderAsc();
}
