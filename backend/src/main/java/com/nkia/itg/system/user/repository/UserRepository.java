package com.nkia.itg.system.user.repository;

import com.nkia.itg.system.user.domain.UserStatus;
import com.nkia.itg.system.user.entity.UserAccount;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);

    /**
     * 페이지 검색. dept·roleCode·status·keyword 모두 옵션(null 이면 무시).
     * roleCode 필터를 위해 roles 를 left join 하므로 distinct 로 중복 행 제거.
     */
    @Query("""
            select distinct u from UserAccount u
             left join u.roles r
             where (:dept   is null or u.departmentId = :dept)
               and (:role   is null or r.code         = :role)
               and (:status is null or u.status       = :status)
               and (:kw     is null
                    or lower(u.username) like lower(concat('%', :kw, '%'))
                    or lower(u.name)     like lower(concat('%', :kw, '%')))
            """)
    Page<UserAccount> search(
            @Param("dept")   Long       deptId,
            @Param("role")   String     roleCode,
            @Param("status") UserStatus status,
            @Param("kw")     String     keyword,
            Pageable         pageable
    );
}
