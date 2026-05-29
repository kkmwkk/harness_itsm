package com.nkia.itg.system.role.service;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.system.permission.entity.Permission;
import com.nkia.itg.system.permission.repository.PermissionRepository;
import com.nkia.itg.system.role.dto.RoleCreateRequest;
import com.nkia.itg.system.role.dto.RoleResponse;
import com.nkia.itg.system.role.dto.RoleUpdateRequest;
import com.nkia.itg.system.role.entity.Role;
import com.nkia.itg.system.role.repository.RoleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 역할 CRUD + 권한 부여/회수. 코드 중복은 DATA_INTEGRITY 로 거부한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public RoleResponse create(RoleCreateRequest req) {
        if (roleRepository.existsByCode(req.code())) {
            throw new ITGException("DATA_INTEGRITY", "이미 사용 중인 역할 코드입니다: " + req.code());
        }
        Role role = Role.builder()
                .code(req.code())
                .name(req.name())
                .description(req.description())
                .build();
        return RoleResponse.from(roleRepository.save(role));
    }

    @Transactional(readOnly = true)
    public RoleResponse getById(Long id) {
        return RoleResponse.from(loadOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> findAll() {
        return roleRepository.findAll().stream().map(RoleResponse::from).toList();
    }

    public RoleResponse update(Long id, RoleUpdateRequest req) {
        Role role = loadOrThrow(id);
        role.update(req.name(), req.description());
        return RoleResponse.from(role);
    }

    public void grantPermission(String roleCode, String permissionCode) {
        Role role = loadByCodeOrThrow(roleCode);
        role.grant(loadPermissionOrThrow(permissionCode));
    }

    public void revokePermission(String roleCode, String permissionCode) {
        Role role = loadByCodeOrThrow(roleCode);
        role.revoke(loadPermissionOrThrow(permissionCode));
    }

    private Role loadOrThrow(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ITGException(
                        "ROLE_NOT_FOUND", "역할을 찾을 수 없습니다: " + id, HttpStatus.NOT_FOUND));
    }

    private Role loadByCodeOrThrow(String code) {
        return roleRepository.findByCode(code)
                .orElseThrow(() -> new ITGException(
                        "ROLE_NOT_FOUND", "역할을 찾을 수 없습니다: " + code, HttpStatus.NOT_FOUND));
    }

    private Permission loadPermissionOrThrow(String code) {
        return permissionRepository.findByCode(code)
                .orElseThrow(() -> new ITGException(
                        "PERMISSION_NOT_FOUND", "권한을 찾을 수 없습니다: " + code, HttpStatus.NOT_FOUND));
    }
}
