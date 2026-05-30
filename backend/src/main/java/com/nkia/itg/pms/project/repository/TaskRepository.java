package com.nkia.itg.pms.project.repository;

import com.nkia.itg.pms.project.entity.Task;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

    /** 한 프로젝트의 태스크를 정렬 순서대로. */
    List<Task> findByProjectIdOrderBySortOrderAsc(Long projectId);

    /** 여러 프로젝트의 태스크를 한 번에(목록 화면의 N+1 회피). */
    List<Task> findByProjectIdInOrderByProjectIdAscSortOrderAsc(List<Long> projectIds);
}
