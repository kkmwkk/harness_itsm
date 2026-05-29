package com.nkia.itg;

import com.nkia.itg.itam.asset.repository.AssetRepository;
import com.nkia.itg.itsm.ticket.repository.TicketRepository;
import com.nkia.itg.meta.repository.MetaRepository;
import com.nkia.itg.system.dept.repository.DepartmentRepository;
import com.nkia.itg.system.menu.repository.MenuRepository;
import com.nkia.itg.system.permission.repository.PermissionRepository;
import com.nkia.itg.system.role.repository.RoleRepository;
import com.nkia.itg.system.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
class ApplicationContextLoadTest {

    @MockitoBean
    private MetaRepository metaRepository;

    @MockitoBean
    private TicketRepository ticketRepository;

    @MockitoBean
    private AssetRepository assetRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private DepartmentRepository departmentRepository;

    @MockitoBean
    private RoleRepository roleRepository;

    @MockitoBean
    private PermissionRepository permissionRepository;

    @MockitoBean
    private MenuRepository menuRepository;

    @Test
    void contextLoads() {}
}
