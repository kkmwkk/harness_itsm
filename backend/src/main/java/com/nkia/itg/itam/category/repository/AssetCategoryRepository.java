package com.nkia.itg.itam.category.repository;

import com.nkia.itg.itam.category.entity.AssetCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetCategoryRepository extends JpaRepository<AssetCategory, String> {

    /** path 사전순 정렬 — 트리 조립 시 부모가 항상 자식보다 먼저 등장하도록 보장. */
    List<AssetCategory> findAllByOrderByPathAsc();
}
