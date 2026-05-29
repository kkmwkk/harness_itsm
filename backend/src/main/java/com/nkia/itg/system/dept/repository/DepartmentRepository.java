package com.nkia.itg.system.dept.repository;

import com.nkia.itg.system.dept.entity.Department;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByCode(String code);

    /** path 사전순 정렬 — 트리 조립 시 부모가 항상 자식보다 먼저 등장하도록 보장. */
    List<Department> findAllByOrderByPathAsc();
}
