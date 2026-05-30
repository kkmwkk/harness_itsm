package com.nkia.itg.pms.project.service;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.pms.project.dto.ProjectResponse;
import com.nkia.itg.pms.project.entity.Project;
import com.nkia.itg.pms.project.entity.Task;
import com.nkia.itg.pms.project.repository.ProjectRepository;
import com.nkia.itg.pms.project.repository.TaskRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트·태스크 조회 (PMS MVP — PRD §4-4). 읽기 전용 — 생성/수정은 별도 phase 의 ADR.
 * 목록 조회는 태스크를 한 번에 로드해 projectId 로 그룹핑(N+1 회피).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;

    /** 전체 프로젝트(태스크 포함) — 시작일·코드 순. */
    public List<ProjectResponse> list() {
        List<Project> projects = projectRepository.findAllByOrderByStartDateAscCodeAsc();
        if (projects.isEmpty()) {
            return List.of();
        }
        List<Long> ids = projects.stream().map(Project::getId).toList();
        Map<Long, List<Task>> tasksByProject = taskRepository
                .findByProjectIdInOrderByProjectIdAscSortOrderAsc(ids).stream()
                .collect(Collectors.groupingBy(Task::getProjectId));
        List<ProjectResponse> out = new ArrayList<>(projects.size());
        for (Project p : projects) {
            out.add(ProjectResponse.from(p, tasksByProject.getOrDefault(p.getId(), List.of())));
        }
        return out;
    }

    /** 단건(태스크 포함). 없으면 PROJECT_NOT_FOUND(404). */
    public ProjectResponse getById(Long id) {
        Project p = projectRepository.findById(id)
                .orElseThrow(() -> new ITGException(
                        "PROJECT_NOT_FOUND", "프로젝트를 찾을 수 없습니다: " + id, HttpStatus.NOT_FOUND));
        return ProjectResponse.from(p, taskRepository.findByProjectIdOrderBySortOrderAsc(id));
    }
}
