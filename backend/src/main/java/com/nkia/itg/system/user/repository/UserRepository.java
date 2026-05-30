package com.nkia.itg.system.user.repository;

import com.nkia.itg.system.user.domain.UserStatus;
import com.nkia.itg.system.user.entity.UserAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);

    /** 특정 역할 코드를 보유한 사용자 목록 (알림 대상자 조회 — 워크플로우 단계 담당 역할). */
    List<UserAccount> findByRoles_Code(String roleCode);

    /**
     * 페이지 검색. dept·roleCode·status·keyword 모두 옵션(null 이면 무시).
     * roleCode 필터를 위해 roles 를 left join 하므로 distinct 로 중복 행 제거.
     * keyword 는 cast(:kw as string) 으로 타입을 고정한다 — 미지정 시 null 파라미터가
     * PostgreSQL 에서 bytea 로 추론되어 lower(bytea) 오류가 발생한다(DB 레벨 결함 회피).
     */
    @Query("""
            select distinct u from UserAccount u
             left join u.roles r
             where (:dept   is null or u.departmentId = :dept)
               and (:role   is null or r.code         = :role)
               and (:status is null or u.status       = :status)
               and (cast(:kw as string) is null
                    or lower(u.username) like lower(concat('%', cast(:kw as string), '%'))
                    or lower(u.name)     like lower(concat('%', cast(:kw as string), '%')))
            """)
    Page<UserAccount> search(
            @Param("dept")   Long       deptId,
            @Param("role")   String     roleCode,
            @Param("status") UserStatus status,
            @Param("kw")     String     keyword,
            Pageable         pageable
    );
}
