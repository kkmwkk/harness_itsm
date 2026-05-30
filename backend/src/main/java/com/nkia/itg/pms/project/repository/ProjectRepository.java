package com.nkia.itg.pms.project.repository;

import com.nkia.itg.pms.project.entity.Project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /** 시작일·코드 순으로 정렬된 전체 프로젝트(간트 표시 순서). */
    List<Project> findAllByOrderByStartDateAscCodeAsc();
}
