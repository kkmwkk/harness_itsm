package com.nkia.itg.pms.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.pms.project.domain.ProjectStatus;
import com.nkia.itg.pms.project.domain.TaskStatus;
import com.nkia.itg.pms.project.dto.ProjectResponse;
import com.nkia.itg.pms.project.entity.Project;
import com.nkia.itg.pms.project.entity.Task;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.nkia.itg.pms.project.repository.ProjectRepository;
import com.nkia.itg.pms.project.repository.TaskRepository;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    ProjectRepository projectRepository;

    @Mock
    TaskRepository taskRepository;

    @InjectMocks
    ProjectService projectService;

    private Project project(Long id, String code) {
        return Project.builder()
                .id(id)
                .code(code)
                .name(code + " 이름")
                .status(ProjectStatus.IN_PROGRESS)
                .startDate(LocalDate.of(2026, 5, 1))
                .dueDate(LocalDate.of(2026, 7, 1))
                .progress(40)
                .build();
    }

    private Task task(Long id, Long projectId, String title, int sortOrder) {
        return Task.builder()
                .id(id)
                .projectId(projectId)
                .title(title)
                .status(TaskStatus.TODO)
                .progress(0)
                .sortOrder(sortOrder)
                .build();
    }

    @Test
    @DisplayName("list 는 프로젝트별로 태스크를 묶어 반환한다 (N+1 회피 — 태스크 일괄 로드)")
    void list_그룹핑() {
        // given
        Project p1 = project(1L, "PRJ-1");
        Project p2 = project(2L, "PRJ-2");
        when(projectRepository.findAllByOrderByStartDateAscCodeAsc()).thenReturn(List.of(p1, p2));
        when(taskRepository.findByProjectIdInOrderByProjectIdAscSortOrderAsc(anyList()))
                .thenReturn(List.of(task(10L, 1L, "분석", 0), task(11L, 1L, "설계", 1), task(20L, 2L, "준비", 0)));

        // when
        List<ProjectResponse> result = projectService.list();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).code()).isEqualTo("PRJ-1");
        assertThat(result.get(0).tasks()).extracting(t -> t.title()).containsExactly("분석", "설계");
        assertThat(result.get(1).tasks()).hasSize(1);
    }

    @Test
    @DisplayName("list 는 프로젝트가 없으면 빈 리스트(태스크 조회 생략)")
    void list_빈리스트() {
        // given
        when(projectRepository.findAllByOrderByStartDateAscCodeAsc()).thenReturn(List.of());

        // when / then
        assertThat(projectService.list()).isEmpty();
    }

    @Test
    @DisplayName("getById 는 태스크를 포함해 단건을 반환한다")
    void getById_단건() {
        // given
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project(1L, "PRJ-1")));
        when(taskRepository.findByProjectIdOrderBySortOrderAsc(1L))
                .thenReturn(List.of(task(10L, 1L, "분석", 0)));

        // when
        ProjectResponse result = projectService.getById(1L);

        // then
        assertThat(result.code()).isEqualTo("PRJ-1");
        assertThat(result.tasks()).hasSize(1);
    }

    @Test
    @DisplayName("getById 는 없는 id 면 PROJECT_NOT_FOUND 로 거부한다")
    void getById_없음() {
        // given
        when(projectRepository.findById(anyLong())).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> projectService.getById(999L))
                .isInstanceOf(ITGException.class)
                .hasMessageContaining("프로젝트를 찾을 수 없습니다");
    }
}
