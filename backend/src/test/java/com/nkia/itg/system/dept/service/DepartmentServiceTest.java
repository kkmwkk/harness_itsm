package com.nkia.itg.system.dept.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.nkia.itg.fixture.SystemFixtures;
import com.nkia.itg.system.dept.dto.DepartmentCreateRequest;
import com.nkia.itg.system.dept.dto.DepartmentResponse;
import com.nkia.itg.system.dept.dto.DepartmentTreeNode;
import com.nkia.itg.system.dept.entity.Department;
import com.nkia.itg.system.dept.repository.DepartmentRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;
    @InjectMocks
    private DepartmentService departmentService;

    @Test
    @DisplayName("create: 루트 부서는 path '/{id}/' 로 자동 계산")
    void create_root_path_자동_계산() {
        Department saved = SystemFixtures.rootDept(10L, "DEPT-ROOT", "샘플 본부");
        given(departmentRepository.save(any())).willReturn(saved);

        DepartmentResponse res = departmentService.create(
                new DepartmentCreateRequest("DEPT-ROOT", "샘플 본부", null));

        assertThat(res.path()).isEqualTo("/10/");
        assertThat(res.parentId()).isNull();
    }

    @Test
    @DisplayName("create: 자식 부서는 부모 path + id 로 연결")
    void create_child_부모_path_id_연결() {
        Department parent = SystemFixtures.rootDept(1L, "DEPT-ROOT", "샘플 본부");
        Department saved = SystemFixtures.childDept(parent, 5L, "DEPT-IT", "샘플 IT팀");
        given(departmentRepository.findById(1L)).willReturn(Optional.of(parent));
        given(departmentRepository.save(any())).willReturn(saved);

        DepartmentResponse res = departmentService.create(
                new DepartmentCreateRequest("DEPT-IT", "샘플 IT팀", 1L));

        assertThat(res.path()).isEqualTo("/1/5/");
        assertThat(res.parentId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("move: 자기 자신으로 이동 거부 IllegalStateException")
    void move_자기_이동_거부() {
        Department dept = SystemFixtures.childDept(
                SystemFixtures.rootDept(1L, "ROOT", "본부"), 3L, "DEPT-IT", "IT팀");
        given(departmentRepository.findById(3L)).willReturn(Optional.of(dept));

        assertThatThrownBy(() -> departmentService.move(3L, 3L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("move: 자손으로 이동 거부 IllegalStateException")
    void move_자손_이동_거부() {
        Department root = SystemFixtures.rootDept(1L, "ROOT", "본부");
        Department dept = SystemFixtures.childDept(root, 3L, "DEPT-IT", "IT팀");      // /1/3/
        Department child = SystemFixtures.childDept(dept, 7L, "DEPT-DEV", "개발파트"); // /1/3/7/
        given(departmentRepository.findById(3L)).willReturn(Optional.of(dept));
        given(departmentRepository.findById(7L)).willReturn(Optional.of(child));

        assertThatThrownBy(() -> departmentService.move(3L, 7L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("move: 정상 이동 시 자기·자손 path 재계산")
    void move_정상_시_자손_path_재계산() {
        Department oldRoot = SystemFixtures.rootDept(1L, "ROOT", "본부");
        Department dept = SystemFixtures.childDept(oldRoot, 3L, "DEPT-IT", "IT팀");   // /1/3/
        Department child = SystemFixtures.childDept(dept, 7L, "DEPT-DEV", "개발파트"); // /1/3/7/
        Department newParent = SystemFixtures.rootDept(2L, "ROOT2", "신규 본부");      // /2/
        given(departmentRepository.findById(3L)).willReturn(Optional.of(dept));
        given(departmentRepository.findById(2L)).willReturn(Optional.of(newParent));
        given(departmentRepository.findAllByOrderByPathAsc()).willReturn(List.of(dept, child));

        departmentService.move(3L, 2L);

        assertThat(dept.getParentId()).isEqualTo(2L);
        assertThat(dept.getPath()).isEqualTo("/2/3/");
        assertThat(child.getPath()).isEqualTo("/2/3/7/");
    }

    @Test
    @DisplayName("getTree: path 정렬 입력으로 parent_id 트리 조립")
    void getTree_path_정렬로_트리_조립() {
        Department root = SystemFixtures.rootDept(1L, "ROOT", "본부");
        Department it = SystemFixtures.childDept(root, 3L, "DEPT-IT", "IT팀");
        Department dev = SystemFixtures.childDept(it, 7L, "DEPT-DEV", "개발파트");
        given(departmentRepository.findAllByOrderByPathAsc())
                .willReturn(List.of(root, it, dev));

        List<DepartmentTreeNode> tree = departmentService.getTree();

        assertThat(tree).hasSize(1);
        DepartmentTreeNode rootNode = tree.get(0);
        assertThat(rootNode.id()).isEqualTo(1L);
        assertThat(rootNode.children()).hasSize(1);
        assertThat(rootNode.children().get(0).id()).isEqualTo(3L);
        assertThat(rootNode.children().get(0).children().get(0).id()).isEqualTo(7L);
    }
}
