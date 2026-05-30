package com.nkia.itg.pms.project.controller;

import com.nkia.itg.common.response.ApiResponse;
import com.nkia.itg.pms.project.dto.ProjectResponse;
import com.nkia.itg.pms.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "PMS Project — 프로젝트",
        description = "프로젝트·태스크 조회(읽기 전용, MVP). 인증된 사용자면 조회 가능. "
                + "응답은 태스크를 포함해 간트 차트가 한 번의 호출로 그려진다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @Operation(summary = "프로젝트 목록 조회", description = "전체 프로젝트를 태스크와 함께 시작일·코드 순으로 반환한다.")
    @GetMapping
    public ApiResponse<List<ProjectResponse>> list() {
        return ApiResponse.ok(projectService.list());
    }

    @Operation(summary = "프로젝트 단건 조회", description = "id 로 프로젝트 1건(태스크 포함)을 조회한다. 없으면 PROJECT_NOT_FOUND(404).")
    @GetMapping("/{id}")
    public ApiResponse<ProjectResponse> getById(
            @Parameter(description = "프로젝트 ID", example = "1") @PathVariable Long id) {
        return ApiResponse.ok(projectService.getById(id));
    }
}
