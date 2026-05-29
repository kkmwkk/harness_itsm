package com.nkia.itg.system.permission.service;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.system.permission.dto.PermissionCreateRequest;
import com.nkia.itg.system.permission.dto.PermissionResponse;
import com.nkia.itg.system.permission.dto.PermissionUpdateRequest;
import com.nkia.itg.system.permission.entity.Permission;
import com.nkia.itg.system.permission.repository.PermissionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 권한 CRUD. 코드 중복은 DATA_INTEGRITY 로 거부한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionResponse create(PermissionCreateRequest req) {
        if (permissionRepository.existsByCode(req.code())) {
            throw new ITGException("DATA_INTEGRITY", "이미 사용 중인 권한 코드입니다: " + req.code());
        }
        Permission permission = Permission.builder()
                .code(req.code())
                .name(req.name())
                .description(req.description())
                .build();
        return PermissionResponse.from(permissionRepository.save(permission));
    }

    @Transactional(readOnly = true)
    public PermissionResponse getById(Long id) {
        return PermissionResponse.from(loadOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> findAll() {
        return permissionRepository.findAll().stream().map(PermissionResponse::from).toList();
    }

    public PermissionResponse update(Long id, PermissionUpdateRequest req) {
        Permission permission = loadOrThrow(id);
        permission.update(req.name(), req.description());
        return PermissionResponse.from(permission);
    }

    private Permission loadOrThrow(Long id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new ITGException(
                        "PERMISSION_NOT_FOUND", "권한을 찾을 수 없습니다: " + id, HttpStatus.NOT_FOUND));
    }
}
