package com.nkia.itg.system.permission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.fixture.SystemFixtures;
import com.nkia.itg.system.permission.dto.PermissionCreateRequest;
import com.nkia.itg.system.permission.dto.PermissionResponse;
import com.nkia.itg.system.permission.dto.PermissionUpdateRequest;
import com.nkia.itg.system.permission.entity.Permission;
import com.nkia.itg.system.permission.repository.PermissionRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private PermissionRepository permissionRepository;
    @InjectMocks
    private PermissionService permissionService;

    @Test
    @DisplayName("create: 신규 권한 저장")
    void create_권한_저장() {
        given(permissionRepository.existsByCode("TICKET_CREATE")).willReturn(false);
        given(permissionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        PermissionResponse res = permissionService.create(
                new PermissionCreateRequest("TICKET_CREATE", "티켓 생성", "샘플 설명"));

        assertThat(res.code()).isEqualTo("TICKET_CREATE");
        assertThat(res.name()).isEqualTo("티켓 생성");
    }

    @Test
    @DisplayName("create: 코드 중복 DATA_INTEGRITY")
    void create_코드_중복_거부() {
        given(permissionRepository.existsByCode("META_PUBLISH")).willReturn(true);

        assertThatThrownBy(() -> permissionService.create(
                new PermissionCreateRequest("META_PUBLISH", "메타 배포", null)))
                .isInstanceOf(ITGException.class)
                .satisfies(e -> assertThat(((ITGException) e).getErrorCode()).isEqualTo("DATA_INTEGRITY"));
        then(permissionRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("update: 이름·설명 수정")
    void update_이름_설명_수정() {
        Permission perm = SystemFixtures.permission(10L, "TICKET_CREATE", "티켓 생성");
        given(permissionRepository.findById(10L)).willReturn(Optional.of(perm));

        PermissionResponse res = permissionService.update(10L,
                new PermissionUpdateRequest("티켓 등록", "갱신 설명"));

        assertThat(res.name()).isEqualTo("티켓 등록");
        assertThat(res.description()).isEqualTo("갱신 설명");
    }

    @Test
    @DisplayName("getById: 없는 권한 PERMISSION_NOT_FOUND")
    void getById_없으면_거부() {
        given(permissionRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> permissionService.getById(99L))
                .isInstanceOf(ITGException.class)
                .satisfies(e -> assertThat(((ITGException) e).getErrorCode())
                        .isEqualTo("PERMISSION_NOT_FOUND"));
    }
}
