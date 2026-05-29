package com.nkia.itg.itam.category.service;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.itam.category.dto.AssetCategoryTreeNode;
import com.nkia.itg.itam.category.entity.AssetCategory;
import com.nkia.itg.itam.category.repository.AssetCategoryRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자산 분류 트리 CRUD. path('/HW/HW_LAPTOP/')는 Service 가 계산·유지한다 (DB 트리거 의존 없음).
 * move 는 {@link AssetCategory#moveTo}의 자기·자손 가드로 순환 트리를 원천 차단한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AssetCategoryService {

    private final AssetCategoryRepository assetCategoryRepository;

    public AssetCategory create(String code, String label, String parentCode,
                                String formMetaGroupId, int sortOrder) {
        if (assetCategoryRepository.existsById(code)) {
            throw new ITGException("ASSET_CATEGORY_DUPLICATE",
                    "이미 존재하는 분류 코드입니다: " + code);
        }
        AssetCategory parent = (parentCode == null) ? null : loadOrThrow(parentCode);
        AssetCategory category = AssetCategory.builder()
                .code(code)
                .label(label)
                .parentCode(parentCode)
                .path(computePath(parent, code))   // code 가 PK 라 생성 시점에 path 확정
                .formMetaGroupId(formMetaGroupId)
                .active(true)
                .sortOrder(sortOrder)
                .build();
        return assetCategoryRepository.save(category);
    }

    public AssetCategory update(String code, String label, String formMetaGroupId, int sortOrder) {
        AssetCategory category = loadOrThrow(code);
        category.update(label, formMetaGroupId, sortOrder);
        return category;
    }

    /** 트리 이동 + 자손 path 재계산. 자기·자손으로 이동하면 IllegalStateException(순환 방지). */
    public void move(String code, String newParentCode) {
        AssetCategory category = loadOrThrow(code);
        AssetCategory newParent = (newParentCode == null) ? null : loadOrThrow(newParentCode);

        String oldPath = category.getPath();
        category.moveTo(newParent);                       // 자기·자손 가드 (도메인)
        String newPath = computePath(newParent, code);
        category.assignPath(newPath);

        // 자손들의 path 접두사 치환
        if (oldPath != null) {
            for (AssetCategory child : assetCategoryRepository.findAllByOrderByPathAsc()) {
                if (!child.getCode().equals(code)
                        && child.getPath() != null
                        && child.getPath().startsWith(oldPath)) {
                    child.assignPath(newPath + child.getPath().substring(oldPath.length()));
                }
            }
        }
    }

    public void deactivate(String code) {
        loadOrThrow(code).deactivate();
    }

    /** path 사전순으로 로드해 부모→자식 순서를 보장한 뒤 parent_code 기준 트리 조립. */
    @Transactional(readOnly = true)
    public List<AssetCategoryTreeNode> getTree() {
        List<AssetCategory> all = assetCategoryRepository.findAllByOrderByPathAsc();
        Map<String, AssetCategoryTreeNode> byCode = new LinkedHashMap<>();
        for (AssetCategory c : all) {
            byCode.put(c.getCode(), AssetCategoryTreeNode.of(
                    c.getCode(), c.getLabel(), c.getParentCode(), c.getPath(),
                    c.getFormMetaGroupId(), c.isActive(), c.getSortOrder()));
        }
        List<AssetCategoryTreeNode> roots = new ArrayList<>();
        for (AssetCategoryTreeNode node : byCode.values()) {
            AssetCategoryTreeNode parent = (node.parentCode() == null) ? null : byCode.get(node.parentCode());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children().add(node);
            }
        }
        return roots;
    }

    /** parent 가 null 이면 루트 '/{code}/', 아니면 '{parent.path}{code}/'. */
    private String computePath(AssetCategory parent, String code) {
        if (parent == null || parent.getPath() == null) {
            return "/" + code + "/";
        }
        return parent.getPath() + code + "/";
    }

    private AssetCategory loadOrThrow(String code) {
        return assetCategoryRepository.findById(code)
                .orElseThrow(() -> new ITGException(
                        "ASSET_CATEGORY_NOT_FOUND", "자산 분류를 찾을 수 없습니다: " + code, HttpStatus.NOT_FOUND));
    }
}
