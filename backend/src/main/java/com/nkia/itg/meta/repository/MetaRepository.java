package com.nkia.itg.meta.repository;

import com.nkia.itg.meta.domain.MetaStatus;
import com.nkia.itg.meta.domain.PackageType;
import com.nkia.itg.meta.domain.SystemType;
import com.nkia.itg.meta.entity.PageMeta;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MetaRepository extends JpaRepository<PageMeta, String> {

    Optional<PageMeta> findTopByGroupIdAndMetaStatusAndActiveTrueOrderByMajorVersionDescMinorVersionDesc(
            String groupId, MetaStatus metaStatus);

    List<PageMeta> findAllByGroupIdOrderByMajorVersionDescMinorVersionDesc(String groupId);

    @Query("""
            select max(p.minorVersion)
              from PageMeta p
             where p.groupId      = :groupId
               and p.majorVersion = :majorVersion
            """)
    Optional<Integer> findMaxMinorVersion(@Param("groupId") String groupId,
                                          @Param("majorVersion") int majorVersion);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PageMeta p
               set p.metaStatus = com.nkia.itg.meta.domain.MetaStatus.DEPRECATED,
                   p.updatedAt  = current_timestamp
             where p.groupId    = :groupId
               and p.id         <> :excludeId
               and p.metaStatus = com.nkia.itg.meta.domain.MetaStatus.PUBLISHED
            """)
    int deprecatePublished(@Param("groupId") String groupId,
                           @Param("excludeId") String excludeId);

    List<PageMeta> findAllBySystemTypeAndMetaStatusAndActiveTrueOrderByGroupIdAscMajorVersionDescMinorVersionDesc(
            SystemType systemType, MetaStatus metaStatus);

    List<PageMeta> findAllByPackageTypeOrderByGroupIdAscMajorVersionDescMinorVersionDesc(
            PackageType packageType);

    List<PageMeta> findAllBySystemTypeAndPackageTypeOrderByGroupIdAscMajorVersionDescMinorVersionDesc(
            SystemType systemType, PackageType packageType);
}
