package com.nkia.itg.system.dept.service;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.system.dept.dto.DepartmentCreateRequest;
import com.nkia.itg.system.dept.dto.DepartmentResponse;
import com.nkia.itg.system.dept.dto.DepartmentTreeNode;
import com.nkia.itg.system.dept.dto.DepartmentUpdateRequest;
import com.nkia.itg.system.dept.entity.Department;
import com.nkia.itg.system.dept.repository.DepartmentRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부서 트리 CRUD. path('/1/3/12/')는 Service 가 계산·유지한다 (DB 트리거 의존 없음).
 * move 는 자기·자손으로의 이동을 거부해 순환 트리를 원천 차단한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentResponse create(DepartmentCreateRequest req) {
        Department parent = (req.parentId() == null) ? null : loadOrThrow(req.parentId());
        Department dept = Department.builder()
                .code(req.code())
                .name(req.name())
                .parentId(req.parentId())
                .active(true)
                .build();
        Department saved = departmentRepository.save(dept);          // id 확정
        saved.assignPath(computePath(parent, saved.getId()));        // path 계산 (id 필요)
        return DepartmentResponse.from(saved);
    }

    public DepartmentResponse update(Long id, DepartmentUpdateRequest req) {
        Department dept = loadOrThrow(id);
        dept.rename(req.name());
        if (req.managerUserId() == null) {
            dept.removeManager();
        } else {
            dept.assignManager(req.managerUserId());
        }
        return DepartmentResponse.from(dept);
    }

    /** 트리 이동 + 자손 path 재계산. 자기·자손으로 이동하면 IllegalStateException(순환 방지). */
    public void move(Long id, Long newParentId) {
        Department dept = loadOrThrow(id);
        Department newParent = (newParentId == null) ? null : loadOrThrow(newParentId);

        if (newParentId != null) {
            if (newParentId.equals(id)) {
                throw new IllegalStateException("부서를 자기 자신으로 이동할 수 없습니다.");
            }
            // 새 부모가 자기 자손이면(자기 path 로 시작) 순환 — 거부
            if (newParent.getPath() != null && dept.getPath() != null
                    && newParent.getPath().startsWith(dept.getPath())) {
                throw new IllegalStateException("부서를 자기 자손으로 이동할 수 없습니다.");
            }
        }

        String oldPath = dept.getPath();
        dept.relocate(newParentId);
        String newPath = computePath(newParent, dept.getId());
        dept.assignPath(newPath);

        // 자손들의 path 접두사 치환
        if (oldPath != null) {
            for (Department child : departmentRepository.findAllByOrderByPathAsc()) {
                if (!child.getId().equals(dept.getId())
                        && child.getPath() != null
                        && child.getPath().startsWith(oldPath)) {
                    child.assignPath(newPath + child.getPath().substring(oldPath.length()));
                }
            }
        }
    }

    public void deactivate(Long id) {
        loadOrThrow(id).deactivate();
    }

    /** path 사전순으로 로드해 부모→자식 순서를 보장한 뒤 parent_id 기준 트리 조립. */
    @Transactional(readOnly = true)
    public List<DepartmentTreeNode> getTree() {
        List<Department> all = departmentRepository.findAllByOrderByPathAsc();
        Map<Long, DepartmentTreeNode> byId = new LinkedHashMap<>();
        for (Department d : all) {
            byId.put(d.getId(), DepartmentTreeNode.of(
                    d.getId(), d.getCode(), d.getName(), d.getParentId(), d.getPath(), d.isActive()));
        }
        List<DepartmentTreeNode> roots = new ArrayList<>();
        for (DepartmentTreeNode node : byId.values()) {
            DepartmentTreeNode parent = (node.parentId() == null) ? null : byId.get(node.parentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children().add(node);
            }
        }
        return roots;
    }

    /** parent 가 null 이면 루트 '/{id}/', 아니면 '{parent.path}{id}/'. */
    private String computePath(Department parent, Long id) {
        if (parent == null || parent.getPath() == null) {
            return "/" + id + "/";
        }
        return parent.getPath() + id + "/";
    }

    private Department loadOrThrow(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ITGException(
                        "DEPT_NOT_FOUND", "부서를 찾을 수 없습니다: " + id, HttpStatus.NOT_FOUND));
    }
}
