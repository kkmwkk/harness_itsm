package com.nkia.itg.itam.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nkia.itg.itam.category.dto.AssetCategoryTreeNode;
import com.nkia.itg.itam.category.entity.AssetCategory;
import com.nkia.itg.itam.category.repository.AssetCategoryRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetCategoryServiceTest {

    @Mock
    AssetCategoryRepository assetCategoryRepository;

    @InjectMocks
    AssetCategoryService assetCategoryService;

    private AssetCategory category(String code, String parentCode, String path) {
        return AssetCategory.builder()
                .code(code)
                .label(code + " 라벨")
                .parentCode(parentCode)
                .path(path)
                .active(true)
                .sortOrder(0)
                .build();
    }

    @Test
    @DisplayName("create 는 루트 분류의 path 를 '/{code}/' 로, 하위 분류는 '{parent.path}{code}/' 로 자동 계산한다")
    void create_path_자동() {
        when(assetCategoryRepository.existsById(any())).thenReturn(false);
        when(assetCategoryRepository.findById("HW")).thenReturn(Optional.of(category("HW", null, "/HW/")));
        when(assetCategoryRepository.save(any(AssetCategory.class))).thenAnswer(inv -> inv.getArgument(0));

        AssetCategory root = assetCategoryService.create("HW", "하드웨어", null, null, 0);
        assertThat(root.getPath()).isEqualTo("/HW/");

        AssetCategory child = assetCategoryService.create("HW_LAPTOP", "노트북", "HW", "itg-asset-hw-laptop", 1);
        assertThat(child.getPath()).isEqualTo("/HW/HW_LAPTOP/");
    }

    @Test
    @DisplayName("move 는 분류를 자기 자신·자손으로 이동하면 IllegalStateException 으로 거부한다")
    void move_자기_자손_거부() {
        AssetCategory hw = category("HW", null, "/HW/");
        AssetCategory laptop = category("HW_LAPTOP", "HW", "/HW/HW_LAPTOP/");

        // 자기 자신으로 이동
        when(assetCategoryRepository.findById("HW")).thenReturn(Optional.of(hw));
        assertThatThrownBy(() -> assetCategoryService.move("HW", "HW"))
                .isInstanceOf(IllegalStateException.class);

        // 자손(HW_LAPTOP)으로 이동 — HW 를 자기 자손 밑으로 이동 시도
        when(assetCategoryRepository.findById("HW_LAPTOP")).thenReturn(Optional.of(laptop));
        assertThatThrownBy(() -> assetCategoryService.move("HW", "HW_LAPTOP"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("getTree 는 path 정렬로 로드해 parent_code 기준 트리를 조립한다")
    void getTree_path_정렬() {
        List<AssetCategory> all = List.of(
                category("HW", null, "/HW/"),
                category("HW_LAPTOP", "HW", "/HW/HW_LAPTOP/"),
                category("HW_SERVER", "HW", "/HW/HW_SERVER/"),
                category("SW", null, "/SW/"));
        when(assetCategoryRepository.findAllByOrderByPathAsc()).thenReturn(all);

        List<AssetCategoryTreeNode> roots = assetCategoryService.getTree();

        assertThat(roots).extracting(AssetCategoryTreeNode::code).containsExactly("HW", "SW");
        AssetCategoryTreeNode hw = roots.get(0);
        assertThat(hw.children()).extracting(AssetCategoryTreeNode::code)
                .containsExactly("HW_LAPTOP", "HW_SERVER");
        assertThat(roots.get(1).children()).isEmpty();
    }
}
