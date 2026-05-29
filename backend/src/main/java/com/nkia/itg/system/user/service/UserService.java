package com.nkia.itg.system.user.service;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.system.dept.entity.Department;
import com.nkia.itg.system.dept.repository.DepartmentRepository;
import com.nkia.itg.system.role.entity.Role;
import com.nkia.itg.system.role.repository.RoleRepository;
import com.nkia.itg.system.user.domain.UserStatus;
import com.nkia.itg.system.user.dto.UserCreateRequest;
import com.nkia.itg.system.user.dto.UserResponse;
import com.nkia.itg.system.user.dto.UserSummary;
import com.nkia.itg.system.user.dto.UserUpdateRequest;
import com.nkia.itg.system.user.entity.UserAccount;
import com.nkia.itg.system.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 CRUD + 상태 전이 + 역할 할당. 비밀번호는 항상 BCrypt 해시로만 저장하며
 * 평문은 어떤 경로로도 DB·응답에 노출되지 않는다. 상태 전이·프로필 수정은 Entity 도메인 메서드에 위임한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResponse create(UserCreateRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new ITGException(
                    "DATA_INTEGRITY", "이미 사용 중인 사용자명입니다: " + req.username());
        }
        UserAccount user = UserAccount.builder()
                .username(req.username())
                .passwordHash(passwordEncoder.encode(req.password()))
                .name(req.name())
                .email(req.email())
                .phone(req.phone())
                .departmentId(req.departmentId())
                .status(UserStatus.ACTIVE)
                .build();
        if (req.roleCodes() != null) {
            for (String code : req.roleCodes()) {
                user.assignRole(loadRoleOrThrow(code));
            }
        }
        UserAccount saved = userRepository.save(user);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return toResponse(loadOrThrow(id));
    }

    public UserResponse update(Long id, UserUpdateRequest req) {
        UserAccount user = loadOrThrow(id);
        user.updateProfile(req.name(), req.email(), req.phone(), req.departmentId());
        return toResponse(user);
    }

    public void lock(Long id) {
        loadOrThrow(id).lock();
    }

    public void unlock(Long id) {
        loadOrThrow(id).unlock();
    }

    public void retire(Long id) {
        loadOrThrow(id).retire();
    }

    /** 비밀번호 변경 — 평문을 BCrypt 해시로 변환 후 Entity 도메인 메서드에 위임. */
    public void changePassword(Long id, String newPlain) {
        UserAccount user = loadOrThrow(id);
        user.changePassword(passwordEncoder.encode(newPlain));
    }

    @Transactional(readOnly = true)
    public Page<UserSummary> search(
            Long deptId, String roleCode, UserStatus status, String kw, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        String keyword = (kw == null || kw.isBlank()) ? null : kw.trim();
        return userRepository.search(deptId, roleCode, status, keyword, pageable)
                .map(UserSummary::from);
    }

    public void assignRole(Long userId, String roleCode) {
        UserAccount user = loadOrThrow(userId);
        user.assignRole(loadRoleOrThrow(roleCode));
    }

    public void revokeRole(Long userId, String roleCode) {
        UserAccount user = loadOrThrow(userId);
        user.removeRole(loadRoleOrThrow(roleCode));
    }

    private UserResponse toResponse(UserAccount user) {
        String departmentName = resolveDepartmentName(user.getDepartmentId());
        return UserResponse.from(user, departmentName);
    }

    private String resolveDepartmentName(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findById(departmentId)
                .map(Department::getName)
                .orElse(null);
    }

    private UserAccount loadOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ITGException(
                        "USER_NOT_FOUND", "사용자를 찾을 수 없습니다: " + id, HttpStatus.NOT_FOUND));
    }

    private Role loadRoleOrThrow(String code) {
        return roleRepository.findByCode(code)
                .orElseThrow(() -> new ITGException(
                        "ROLE_NOT_FOUND", "역할을 찾을 수 없습니다: " + code, HttpStatus.NOT_FOUND));
    }
}
