package com.nkia.itg.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
public abstract class PostgresIntegrationTestBase {

    // 운영과 동일하게 init 스크립트를 파일명 사전순으로 모두 적용한다.
    // (01_schema.sql → page_meta, 02_ticket.sql → ticket, 05_asset.sql → asset,
    //  10_auth.sql → user_account/department/role/permission/menu + 매핑 테이블,
    //  11_itsm_workflow.sql → ticket 확장 컬럼 + ticket_request_type/workflow_*,
    //  12_itam_category.sql → asset 확장 컬럼(category_code) + asset_category/asset_lifecycle_event)
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("itgdb")
                    .withUsername("itg")
                    .withPassword("itg1234")
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("init/01_schema.sql"),
                            "/docker-entrypoint-initdb.d/01_schema.sql")
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("init/02_ticket.sql"),
                            "/docker-entrypoint-initdb.d/02_ticket.sql")
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("init/05_asset.sql"),
                            "/docker-entrypoint-initdb.d/05_asset.sql")
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("init/10_auth.sql"),
                            "/docker-entrypoint-initdb.d/10_auth.sql")
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("init/11_itsm_workflow.sql"),
                            "/docker-entrypoint-initdb.d/11_itsm_workflow.sql")
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("init/12_itam_category.sql"),
                            "/docker-entrypoint-initdb.d/12_itam_category.sql");

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
